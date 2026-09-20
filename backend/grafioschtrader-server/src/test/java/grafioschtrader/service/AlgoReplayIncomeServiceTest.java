package grafioschtrader.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;

import grafioschtrader.dto.TaxEstimateRequest.EventKind;
import grafioschtrader.dto.TaxEstimateResult;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Transaction;
import grafioschtrader.repository.SimulationSourceRepository;
import grafioschtrader.types.CouponDayCount;
import grafioschtrader.types.TransactionType;

class AlgoReplayIncomeServiceTest {
  private static final LocalDate DAY = LocalDate.of(2020, 1, 1);
  private static final LocalDate MATURITY = LocalDate.of(2020, 7, 1);
  private final AlgoReplayDividendService dividends = mock(AlgoReplayDividendService.class);
  private final AlgoReplayDividendService.Session claims = mock(AlgoReplayDividendService.Session.class);
  private final AlgoReplayIncomeBookingService booking = mock(AlgoReplayIncomeBookingService.class);
  private final SimulationSourceRepository source = mock(SimulationSourceRepository.class);
  private final AlgoReplayTaxes taxes = mock(AlgoReplayTaxes.class);
  private final AlgoReplayIncomeService service = new AlgoReplayIncomeService(dividends, booking, source,
      mock(MessageSource.class));

  @BeforeEach
  void setup() {
    when(dividends.open(eq(65), anyCollection(), eq("CHF"), any(), any(), any(AlgoReplayInputs.Snapshot.class)))
        .thenReturn(claims);
    when(booking.convertDividend(any(), anyDouble(), eq("CHF"), eq("CHF"), any()))
        .thenAnswer(call -> call.getArgument(1));
  }

  @Test
  void storedAndUnavailableCouponsNeverLoadAccrualLedger() {
    var session = open(Map.of(1, instrument("STORED"), 2, instrument("UNAVAILABLE")), true);

    assertThat(session.receivables(DAY)).isEmpty();
    assertThat(session.summary(DAY)).allSatisfy(total -> {
      assertThat(total.grossReceivables()).isZero();
      assertThat(total.netReceivables()).isZero();
    });
    session.freezeForReporting(MATURITY);
    assertThat(session.receivables(DAY)).isEmpty();
    verifyNoInteractions(source, taxes);
  }

  @Test
  void grossAndNetAccrualShareOneLedgerReadAndRetainWithholding() {
    var session = open(Map.of(1, instrument("GENERATED")), true);
    when(source.transactions(65, DAY.plusDays(1))).thenReturn(List.of(trade(TransactionType.ACCUMULATE)));
    when(taxes.estimate(1, 20, EventKind.SECURITY_INTEREST, MATURITY, 10, 12, 0, 120))
        .thenReturn(new TaxEstimateResult(30, "CHF", true, List.of(), List.of()));

    assertThat(session.summary(DAY)).filteredOn(total -> total.kind().equals("SECURITY_INTEREST")).singleElement()
        .satisfies(total -> {
          assertThat(total.grossReceivables()).isEqualTo(60);
          assertThat(total.estimatedWithholding()).isEqualTo(15);
          assertThat(total.netReceivables()).isEqualTo(45);
        });
    verify(source).transactions(65, DAY.plusDays(1));
    verifyNoMoreInteractions(source);
    verify(taxes).estimate(1, 20, EventKind.SECURITY_INTEREST, MATURITY, 10, 12, 0, 120);
  }

  @Test
  void repeatedValuationOnTheSameDaySeesNewlyCommittedClosures() {
    var session = open(Map.of(1, instrument("GENERATED")), false);
    when(source.transactions(65, DAY.plusDays(1))).thenReturn(List.of(trade(TransactionType.ACCUMULATE)))
        .thenReturn(List.of(trade(TransactionType.ACCUMULATE), trade(TransactionType.REDUCE)));

    assertThat(session.receivables(DAY)).containsEntry("CHF", 60.0);
    assertThat(session.receivables(DAY)).isEmpty();
    verify(source, times(2)).transactions(65, DAY.plusDays(1));
    verifyNoInteractions(taxes);
  }

  @Test
  void completedReplayIndexesHistoricalAccrualWithoutDailyLedgerReads() {
    var session = open(Map.of(1, instrument("GENERATED")), false);
    Transaction purchase = trade(TransactionType.ACCUMULATE);
    purchase.setIdTransaction(1);
    purchase.setTransactionTime(DAY.atStartOfDay());
    purchase.onPrePersist();
    Transaction sale = trade(TransactionType.REDUCE);
    sale.setIdTransaction(2);
    sale.setTransactionTime(DAY.plusDays(2).atStartOfDay());
    sale.onPrePersist();
    when(source.transactions(65, MATURITY.plusDays(1))).thenReturn(List.of(sale, purchase));

    session.freezeForReporting(MATURITY);
    assertThat(session.receivables(DAY.minusDays(1))).isEmpty();
    assertThat(session.receivables(DAY)).containsEntry("CHF", 60.0);
    assertThat(session.receivables(DAY.plusDays(1)).get("CHF")).isCloseTo(60 + 120.0 / 360,
        org.assertj.core.api.Assertions.within(1e-10));
    assertThat(session.receivables(DAY.plusDays(2))).isEmpty();
    assertThat(session.receivables(DAY)).containsEntry("CHF", 60.0);
    session.summary(DAY);
    session.freezeForReporting(MATURITY);
    verify(source).transactions(65, MATURITY.plusDays(1));
    verifyNoMoreInteractions(source);
  }

  private AlgoReplayIncomeService.Session open(Map<Integer, AlgoReplayInputs.Instrument> instruments,
      boolean applyTaxModels) {
    var inputs = new AlgoReplayInputs.Snapshot(2, applyTaxModels, true, 0, Map.of(), Map.of(), instruments, List.of());
    return service.open(new AlgoReplayIncomeBookingService.Context(1, 65, "CHF", null), List.of(), DAY.minusDays(1),
        MATURITY, inputs, taxes, 2, Locale.ENGLISH, _ -> {
        });
  }

  private AlgoReplayInputs.Instrument instrument(String incomeSource) {
    var terms = new AlgoReplayCouponSchedule.Terms(12.0, MATURITY, 1, CouponDayCount.THIRTY_E_360);
    return new AlgoReplayInputs.Instrument("CHF", null, null, null, null, null, true, null, terms, incomeSource, null,
        List.of(), List.of(), null, MATURITY);
  }

  private Transaction trade(TransactionType type) {
    Security security = new Security();
    security.setIdSecuritycurrency(1);
    Transaction transaction = new Transaction();
    transaction.setSecuritycurrency(security);
    transaction.setIdSecurityaccount(20);
    transaction.setUnits(10.0);
    transaction.setTransactionType(type);
    return transaction;
  }
}
