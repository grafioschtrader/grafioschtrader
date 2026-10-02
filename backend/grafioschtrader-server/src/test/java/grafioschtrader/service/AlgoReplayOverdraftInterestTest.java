package grafioschtrader.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import grafioschtrader.entities.AlgoSimulationResult;
import grafioschtrader.entities.Cashaccount;
import grafioschtrader.entities.Transaction;
import grafioschtrader.exceptions.TransactionLimitExceededException;
import grafioschtrader.repository.HoldCashaccountBalanceJpaRepository;
import grafioschtrader.repository.TransactionJpaRepository;
import grafioschtrader.types.TransactionType;

/** The ACT/360 accrual of overdraft interest in a replay and its charge at month end, without a database. */
class AlgoReplayOverdraftInterestTest {

  /** Thursday 31 December 2026 is the opening day; January has 31 days. */
  private static final LocalDate OPENING = LocalDate.of(2026, 12, 31);
  private static final LocalDate END = LocalDate.of(2027, 6, 15);
  private static final int CASH = 40;

  private final HoldCashaccountBalanceJpaRepository holdings = mock(HoldCashaccountBalanceJpaRepository.class);
  private final TransactionJpaRepository transactions = mock(TransactionJpaRepository.class);
  private final AlgoReplayOverdraftInterest service = new AlgoReplayOverdraftInterest(holdings, transactions, null);
  /** End-of-day balances by day; a day without an entry carries the balance of the last day before it. */
  private final TreeMap<LocalDate, Double> balances = new TreeMap<>();

  @Test
  @DisplayName("Charge days are every month end after the opening and the end date")
  void chargeDates() {
    assertThat(AlgoReplayOverdraftInterest.chargeDates(OPENING, LocalDate.of(2027, 3, 15))).containsExactly(
        LocalDate.of(2027, 1, 31), LocalDate.of(2027, 2, 28), LocalDate.of(2027, 3, 15));
    assertThat(AlgoReplayOverdraftInterest.chargeDates(LocalDate.of(2027, 1, 31), LocalDate.of(2027, 2, 28)))
        .containsExactly(LocalDate.of(2027, 2, 28));
  }

  @Test
  @DisplayName("-10 000 at 3.6 % costs 1.00 a day; 30 days give 30.00, the opening day bears none")
  void thirtyDaysAtOnePerDay() throws Exception {
    LocalDate opening = LocalDate.of(2027, 3, 31);
    LocalDate monthEnd = LocalDate.of(2027, 4, 30);
    balances.put(opening, -10_000.0);
    var session = session(3.6, opening);

    assertThat(session.dates()).contains(monthEnd, END);
    // Closed on the 29th first: the 28 days before carry the balance of the opening day, which itself bears none.
    session.close(LocalDate.of(2027, 4, 29));
    verify(transactions, never()).saveOnlyAttributes(any(), isNull(), anySet());
    session.close(monthEnd);

    Transaction charge = charged().getFirst();
    assertThat(charge.getCashaccountAmount()).isEqualTo(-30.0);
    assertThat(charge.getTransactionType()).isEqualTo(TransactionType.INTEREST_CASHACCOUNT);
    assertThat(charge.getTransactionTime()).isEqualTo(monthEnd.atTime(23, 59));
    assertThat(charge.getNote()).isEqualTo(AlgoReplayOverdraftInterest.NOTE);
    assertThat(charge.getAlgoFillId()).isEqualTo("1:I:40:2027-04-30");
  }

  @Test
  @DisplayName("A weekend gap accrues with the Friday balance, and the charge resets the accrual")
  void weekendGapUsesFridayBalance() throws Exception {
    balances.put(OPENING, 0.0);
    balances.put(LocalDate.of(2027, 1, 29), -3_600.0); // Friday
    balances.put(LocalDate.of(2027, 2, 1), 0.0); // Monday, repaid
    var session = session(10.0);

    session.close(LocalDate.of(2027, 1, 29));
    // Sunday 31 January is a charge day: Friday, Saturday and Sunday at 1.00 each.
    session.close(LocalDate.of(2027, 1, 31));
    session.close(LocalDate.of(2027, 2, 1));
    session.close(LocalDate.of(2027, 2, 28));

    assertThat(charged()).singleElement().satisfies(charge -> assertThat(charge.getCashaccountAmount()).isEqualTo(-3.0));
  }

  @Test
  @DisplayName("A positive balance accrues nothing and books nothing")
  void positiveBalanceCostsNothing() throws Exception {
    balances.put(OPENING, 5_000.0);
    var session = session(5.0);

    session.close(LocalDate.of(2027, 1, 31));
    session.close(END);

    verify(transactions, never()).saveOnlyAttributes(any(), isNull(), anySet());
  }

  @Test
  @DisplayName("Without a frozen rate the session adds no day and charges nothing")
  void noRateNoDates() throws Exception {
    balances.put(OPENING, -10_000.0);
    var session = session(null);

    assertThat(session.dates()).isEmpty();
    session.close(LocalDate.of(2027, 1, 31));

    verify(transactions, never()).saveOnlyAttributes(any(), isNull(), anySet());
  }

  @Test
  @DisplayName("The transaction limit stops the run, any other refusal fails it with the account name")
  void refusalsEndTheRun() {
    balances.put(OPENING, -10_000.0);
    var limit = new TransactionLimitExceededException(10);
    doThrow(limit).when(transactions).throwWhenTransactionLimitReached(7, 1);
    assertThatThrownBy(() -> session(3.6).close(LocalDate.of(2027, 1, 31))).isSameAs(limit);

    doThrow(new IllegalStateException("refused")).when(transactions).throwWhenTransactionLimitReached(7, 1);
    assertThatThrownBy(() -> session(3.6).close(LocalDate.of(2027, 1, 31)))
        .hasMessage(AlgoReplayOverdraftInterest.FAILED + ": Overdraft: refused");
  }

  private List<Transaction> charged() throws Exception {
    var captor = ArgumentCaptor.forClass(Transaction.class);
    verify(transactions, atLeastOnce()).saveOnlyAttributes(captor.capture(), isNull(), anySet());
    return captor.getAllValues();
  }

  private AlgoReplayOverdraftInterest.Session session(Double rate) {
    return session(rate, OPENING);
  }

  private AlgoReplayOverdraftInterest.Session session(Double rate, LocalDate opening) {
    doAnswer(invocation -> {
      LocalDate before = invocation.getArgument(1);
      var entry = balances.lowerEntry(before);
      return entry == null ? null : entry.getValue();
    }).when(holdings).getBalanceBeforeDate(any(), any());
    var run = new AlgoSimulationResult();
    run.setIdSimulationResult(1);
    run.setOpeningDate(opening);
    run.setEndDate(END);
    var inputs = new AlgoReplayInputs.Snapshot(AlgoReplayInputs.SNAPSHOT_VERSION, false, false, 0, Map.of(), Map.of(),
        Map.of(), List.of(), Map.of(), null, Map.of(), Map.of(), Map.of(), List.of(),
        rate == null ? Map.of() : Map.of(CASH, rate));
    var state = mock(AlgoReplayState.class);
    ReflectionTestUtils.setField(state, "run", run);
    ReflectionTestUtils.setField(state, "inputs", inputs);
    when(state.idTenant()).thenReturn(7);
    when(state.precision(anyString())).thenReturn(2);
    Cashaccount cash = new Cashaccount();
    cash.setIdSecuritycashAccount(CASH);
    cash.setName("Overdraft");
    cash.setCurrency("CHF");
    return service.open(state, List.of(cash));
  }
}
