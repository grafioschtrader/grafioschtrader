package grafioschtrader.reports;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import grafiosch.common.ClientClock;
import grafiosch.exceptions.DataViolationException;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Portfolio;
import grafioschtrader.reportviews.DateTransactionCurrencypairMap;
import grafioschtrader.repository.HoldSecurityaccountSecurityJpaRepository;
import grafioschtrader.repository.PortfolioJpaRepository;
import grafioschtrader.repository.TradingDaysPlusJpaRepository;
import grafioschtrader.service.AlgoHistoricalValuationService;

class ClientDateReportTest {
  @Test
  void losAngelesTodayUsesLastFxPriceEvenOnAWorkerThread() {
    LocalDate today = LocalDate.of(2026, 9, 15);
    Currencypair pair = new Currencypair();
    pair.setFromCurrency("USD");
    pair.setToCurrency("CHF");
    pair.setSLast(0.8);
    var days = mock(TradingDaysPlusJpaRepository.class);
    var map = CompletableFuture.supplyAsync(
        () -> new DateTransactionCurrencypairMap("CHF", today, List.of(), List.of(pair), true, true, today)).join();
    assertTrue(map.isUntilDateEqualNowOrAfter());
    assertEquals(0.8, CompletableFuture.supplyAsync(() -> ReportHelper.getReportExchangeRate("USD", map, days)).join());
    verifyNoInteractions(days);
    var past = new DateTransactionCurrencypairMap("CHF", today.minusDays(1), List.of(), List.of(pair), true, true,
        today);
    when(days.hasTradingDayBetweenUntilYesterday(today.minusDays(1), today)).thenReturn(true);
    assertThrows(DataViolationException.class, () -> ReportHelper.getReportExchangeRate("USD", past, days));
  }

  @Test
  void tokyoYesterdayIsAcceptedForHistoricalValuation() {
    LocalDate today = LocalDate.of(2026, 9, 16);
    try (var clock = mockStatic(ClientClock.class)) {
      clock.when(ClientClock::today).thenReturn(today);
      assertDoesNotThrow(() -> AlgoHistoricalValuationService.validateDate(today.minusDays(1)));
      assertThrows(DataViolationException.class, () -> AlgoHistoricalValuationService.validateDate(today));
    }
  }

  @Test
  void performanceCacheSeparatesClientDaysForTheSamePortfolio() throws Exception {
    var report = new PerformanceReport();
    var holdings = mock(HoldSecurityaccountSecurityJpaRepository.class);
    var portfolios = mock(PortfolioJpaRepository.class);
    var days = mock(TradingDaysPlusJpaRepository.class);
    ReflectionTestUtils.setField(report, "holdSecurityaccountSecurityRepository", holdings);
    ReflectionTestUtils.setField(report, "portfolioJpaRepository", portfolios);
    ReflectionTestUtils.setField(report, "tradingDaysPlusJpaRepository", days);
    when(portfolios.findByIdTenantAndIdPortfolio(1, 2)).thenReturn(new Portfolio());
    when(holdings.findByIdPortfolioMinFromHoldDate(2)).thenReturn(LocalDate.of(2025, 1, 1));
    when(holdings.getCombinedHolidayOfHoldingsByPortfolio(2)).thenAnswer(_ -> new HashSet<LocalDate>());
    try (var clock = mockStatic(ClientClock.class)) {
      clock.when(ClientClock::today).thenReturn(LocalDate.of(2026, 9, 16));
      var tokyo = report.getFirstAndMissingTradingDaysByPortfolio(1, 2);
      assertEquals(LocalDate.of(2026, 9, 15), tokyo.latestTradingDay);
      assertEquals(LocalDate.of(2026, 9, 15), tokyo.leatestPossibleTradingDay);
      clock.when(ClientClock::today).thenReturn(LocalDate.of(2026, 9, 15));
      var la = report.getFirstAndMissingTradingDaysByPortfolio(1, 2);
      assertEquals(LocalDate.of(2026, 9, 14), la.latestTradingDay);
      clock.when(ClientClock::today).thenReturn(LocalDate.of(2026, 9, 16));
      assertSame(tokyo, report.getFirstAndMissingTradingDaysByPortfolio(1, 2));
    }
  }
}
