package grafioschtrader.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import grafioschtrader.entities.HoldDailyTotal;
import grafioschtrader.entities.HoldDailyTotalState;
import grafioschtrader.entities.Portfolio;
import grafioschtrader.entities.TradingDaysPlus;
import grafioschtrader.reportviews.performance.IPeriodHolding;
import grafioschtrader.repository.HoldDailyTotalJpaRepository;
import grafioschtrader.repository.HoldDailyTotalStateJpaRepository;
import grafioschtrader.repository.HoldSecurityaccountSecurityJpaRepository;
import grafioschtrader.repository.PortfolioJpaRepository;
import grafioschtrader.repository.TenantJpaRepository;
import grafioschtrader.repository.TradingDaysPlusJpaRepository;

/**
 * Unit tests of the update of {@code hold_daily_total}: which range is recomputed and where the marker of the tenant
 * ends up, with mocked repositories so that no database is needed.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Update of the daily total value")
class HoldDailyTotalServiceTest {

  private static final Integer ID_TENANT = 7;
  private static final Integer ID_PORTFOLIO = 11;
  /** A Wednesday, the latest trading day of every test. */
  private static final LocalDate TODAY = LocalDate.of(2026, 10, 7);
  private static final LocalDateTime DB_NOW = LocalDateTime.of(2026, 10, 7, 7, 0);
  private static final LocalDateTime LAST_SCAN = LocalDateTime.of(2026, 10, 6, 22, 0);

  @Mock
  private HoldDailyTotalJpaRepository holdDailyTotalJpaRepository;
  @Mock
  private HoldDailyTotalStateJpaRepository holdDailyTotalStateJpaRepository;
  @Mock
  private HoldSecurityaccountSecurityJpaRepository holdSecurityaccountSecurityJpaRepository;
  @Mock
  private PortfolioJpaRepository portfolioJpaRepository;
  @Mock
  private TenantJpaRepository tenantJpaRepository;
  @Mock
  private TradingDaysPlusJpaRepository tradingDaysPlusJpaRepository;

  @InjectMocks
  private HoldDailyTotalService service;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(service, "retryDays", 10);
    when(holdDailyTotalStateJpaRepository.getDatabaseNow()).thenReturn(DB_NOW);
  }

  private record Holding(LocalDate date, double cashBalanceMC, double securitiesMC) implements IPeriodHolding {
    @Override
    public LocalDate getDate() {
      return date;
    }

    @Override
    public double getCashBalanceMC() {
      return cashBalanceMC;
    }

    @Override
    public double getSecuritiesMC() {
      return securitiesMC;
    }

    @Override
    public double getDividendRealMC() {
      return 0;
    }

    @Override
    public double getFeeRealMC() {
      return 0;
    }

    @Override
    public double getInterestCashaccountRealMC() {
      return 0;
    }

    @Override
    public double getAccumulateReduceMC() {
      return 0;
    }

    @Override
    public double getExternalCashTransferMC() {
      return 0;
    }

    @Override
    public double getMarginCloseGainMC() {
      return 0;
    }

    @Override
    public double getSecurityRiskMC() {
      return securitiesMC;
    }

    @Override
    public double getGainMC() {
      return 0;
    }
  }

  private HoldDailyTotalState state(LocalDate recalcFromDate) {
    HoldDailyTotalState state = new HoldDailyTotalState(ID_TENANT, recalcFromDate);
    state.setQuoteCheckedTime(LAST_SCAN);
    when(holdDailyTotalStateJpaRepository.lockByIdTenant(ID_TENANT)).thenReturn(Optional.of(state));
    return state;
  }

  private void latestTradingDay(LocalDate date) {
    when(tradingDaysPlusJpaRepository.findTopByTradingDateLessThanOrderByTradingDateDesc(TODAY.plusDays(1)))
        .thenReturn(new TradingDaysPlus(date));
  }

  /** Captures every row handed to saveAll, in the order of the calls. */
  @SuppressWarnings("unchecked")
  private List<HoldDailyTotal> savedRows() {
    ArgumentCaptor<List<HoldDailyTotal>> captor = ArgumentCaptor.forClass(List.class);
    verify(holdDailyTotalJpaRepository, atLeast(0)).saveAll(captor.capture());
    List<HoldDailyTotal> rows = new ArrayList<>();
    captor.getAllValues().forEach(rows::addAll);
    return rows;
  }

  @Test
  @DisplayName("A run without changes computes only the new day and moves the marker behind it")
  void onlyTheNewDay() {
    HoldDailyTotalState state = state(TODAY);
    latestTradingDay(TODAY);
    when(holdSecurityaccountSecurityJpaRepository.getPeriodHoldingsByTenant(ID_TENANT, TODAY, TODAY))
        .thenReturn(List.of(new Holding(TODAY, 100, 900)));

    int days = service.updateTenant(ID_TENANT, TODAY);

    assertThat(days).isEqualTo(1);
    verify(holdDailyTotalJpaRepository).removeByIdTenantFromDate(ID_TENANT, TODAY);
    assertThat(savedRows()).singleElement().satisfies(r -> {
      assertThat(r.getHoldDate()).isEqualTo(TODAY);
      assertThat(r.getIdPortfolio()).isNull();
      assertThat(r.getTotalBalanceMC()).isEqualTo(1000.0);
    });
    assertThat(state.getRecalcFromDate()).isEqualTo(TODAY.plusDays(1));
    assertThat(state.getQuoteCheckedTime()).isBefore(DB_NOW);
  }

  @Test
  @DisplayName("Nothing is computed while the marker lies after the latest trading day")
  void nothingToDo() {
    HoldDailyTotalState state = state(TODAY.plusDays(1));
    latestTradingDay(TODAY);

    assertThat(service.updateTenant(ID_TENANT, TODAY)).isZero();

    verify(holdDailyTotalJpaRepository, never()).removeByIdTenantFromDate(any(), any());
    assertThat(state.getRecalcFromDate()).isEqualTo(TODAY.plusDays(1));
  }

  @Test
  @DisplayName("A booking three months back recomputes from that day only, the portfolios included")
  void backDatedBooking() {
    LocalDate booked = TODAY.minusMonths(3);
    HoldDailyTotalState state = state(booked);
    latestTradingDay(TODAY);
    Portfolio portfolio = new Portfolio();
    portfolio.setIdPortfolio(ID_PORTFOLIO);
    when(portfolioJpaRepository.findByIdTenantOrderByName(ID_TENANT)).thenReturn(List.of(portfolio));
    when(holdSecurityaccountSecurityJpaRepository.getPeriodHoldingsByTenant(ID_TENANT, booked, TODAY))
        .thenReturn(List.of(new Holding(booked, 1, 2), new Holding(TODAY, 3, 4)));
    when(holdSecurityaccountSecurityJpaRepository.getPeriodHoldingsByPortfolio(ID_PORTFOLIO, booked, TODAY))
        .thenReturn(List.of(new Holding(booked, 1, 2)));

    assertThat(service.updateTenant(ID_TENANT, TODAY)).isEqualTo(2);

    verify(holdDailyTotalJpaRepository).removeByIdTenantFromDate(ID_TENANT, booked);
    assertThat(savedRows()).extracting(HoldDailyTotal::getIdPortfolio).containsExactly(null, null, ID_PORTFOLIO);
    assertThat(state.getRecalcFromDate()).isEqualTo(TODAY.plusDays(1));
  }

  @Test
  @DisplayName("A long range is queried per calendar year")
  void chunkedByYear() {
    LocalDate from = LocalDate.of(2024, 11, 4);
    state(from);
    latestTradingDay(TODAY);

    service.updateTenant(ID_TENANT, TODAY);

    verify(holdSecurityaccountSecurityJpaRepository).getPeriodHoldingsByTenant(ID_TENANT, from,
        LocalDate.of(2024, 12, 31));
    verify(holdSecurityaccountSecurityJpaRepository).getPeriodHoldingsByTenant(ID_TENANT, LocalDate.of(2025, 1, 1),
        LocalDate.of(2025, 12, 31));
    verify(holdSecurityaccountSecurityJpaRepository).getPeriodHoldingsByTenant(ID_TENANT, LocalDate.of(2026, 1, 1),
        TODAY);
  }

  @Test
  @DisplayName("A missing quote of yesterday is retried, the marker stays on it")
  void missingQuoteWithinRetryWindow() {
    LocalDate yesterday = TODAY.minusDays(1);
    HoldDailyTotalState state = state(yesterday);
    latestTradingDay(TODAY);
    when(holdSecurityaccountSecurityJpaRepository.getMissingsQuoteDaysByTenant(ID_TENANT))
        .thenReturn(Set.of(yesterday, TODAY.minusYears(2)));

    service.updateTenant(ID_TENANT, TODAY);

    assertThat(state.getRecalcFromDate()).isEqualTo(yesterday);
  }

  @Test
  @DisplayName("A missing quote outside the retry window stays a gap and is not retried")
  void missingQuoteOutsideRetryWindow() {
    LocalDate from = TODAY.minusDays(20);
    HoldDailyTotalState state = state(from);
    latestTradingDay(TODAY);
    when(holdSecurityaccountSecurityJpaRepository.getMissingsQuoteDaysByTenant(ID_TENANT))
        .thenReturn(Set.of(TODAY.minusDays(15)));

    service.updateTenant(ID_TENANT, TODAY);

    assertThat(state.getRecalcFromDate()).isEqualTo(TODAY.plusDays(1));
  }

  @Test
  @DisplayName("A price created or changed since the last scan lowers the marker to its day")
  void changedQuoteLowersTheMarker() {
    LocalDate backfilled = TODAY.minusDays(40);
    HoldDailyTotalState state = state(TODAY);
    latestTradingDay(TODAY);
    when(holdDailyTotalJpaRepository.getEarliestChangedQuoteDateByTenant(ID_TENANT, LAST_SCAN)).thenReturn(backfilled);

    service.updateTenant(ID_TENANT, TODAY);

    verify(holdDailyTotalJpaRepository).removeByIdTenantFromDate(ID_TENANT, backfilled);
    assertThat(state.getRecalcFromDate()).isEqualTo(TODAY.plusDays(1));
  }

  @Test
  @DisplayName("A tenant never computed starts on its zero base day and gets the zero base row when the query lacks it")
  void firstRunFromZeroBaseDay() {
    LocalDate zeroBase = TODAY.minusDays(2);
    when(holdDailyTotalStateJpaRepository.lockByIdTenant(ID_TENANT)).thenReturn(Optional.empty());
    when(holdSecurityaccountSecurityJpaRepository.getZeroBaseDateByTenant(ID_TENANT)).thenReturn(zeroBase);
    when(holdDailyTotalStateJpaRepository.saveAndFlush(any(HoldDailyTotalState.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    latestTradingDay(TODAY);
    when(holdSecurityaccountSecurityJpaRepository.getPeriodHoldingsByTenant(ID_TENANT, zeroBase, TODAY))
        .thenReturn(List.of(new Holding(TODAY, 50, 950)));
    when(holdSecurityaccountSecurityJpaRepository.getPeriodHoldingZeroBaseByTenant(ID_TENANT, zeroBase))
        .thenReturn(List.of(new Holding(zeroBase, 1000, 0)));

    assertThat(service.updateTenant(ID_TENANT, TODAY)).isEqualTo(2);

    verify(holdDailyTotalJpaRepository).removeByIdTenant(ID_TENANT);
    verify(holdDailyTotalJpaRepository, never()).getEarliestChangedQuoteDateByTenant(any(), any());
    assertThat(savedRows()).extracting(HoldDailyTotal::getHoldDate).containsExactly(zeroBase, TODAY);
  }

  @Test
  @DisplayName("A tenant that has never held a security is left without a marker")
  void tenantWithoutHoldings() {
    when(holdDailyTotalStateJpaRepository.lockByIdTenant(ID_TENANT)).thenReturn(Optional.empty());

    assertThat(service.updateTenant(ID_TENANT, TODAY)).isZero();

    verify(holdDailyTotalStateJpaRepository, never()).saveAndFlush(any());
    verify(holdSecurityaccountSecurityJpaRepository, never()).getPeriodHoldingsByTenant(any(), any(), any());
    verify(holdDailyTotalJpaRepository, never()).removeByIdTenant(anyInt());
  }
}
