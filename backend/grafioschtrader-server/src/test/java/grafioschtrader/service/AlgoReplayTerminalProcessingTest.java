package grafioschtrader.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import grafioschtrader.entities.Assetclass;
import grafioschtrader.entities.Cashaccount;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Transaction;
import grafioschtrader.repository.SimulationSourceRepository;
import grafioschtrader.types.AlgoEventType;
import grafioschtrader.types.SpecialInvestmentInstruments;
import grafioschtrader.types.TransactionType;

@DisplayName("Replay terminal events load holdings only when due or retrying")
class AlgoReplayTerminalProcessingTest {
  private static final LocalDate DAY = LocalDate.of(2020, 5, 20);
  private final SimulationSourceRepository source = mock(SimulationSourceRepository.class);
  private final AlgoReplayBooking booking = mock(AlgoReplayBooking.class);
  private final AlgoReplayState state = mock(AlgoReplayState.class);
  private final AlgoHistoricalReplayService replay = new AlgoHistoricalReplayService();
  private final Map<Integer, Security> securities = new LinkedHashMap<>();
  private final Map<Integer, AlgoReplayInputs.Instrument> instruments = new LinkedHashMap<>();
  private final AlgoReplayTerminalSchedule schedule = new AlgoReplayTerminalSchedule();
  private final Cashaccount cash = new Cashaccount();

  @BeforeEach
  void setup() {
    ReflectionTestUtils.setField(replay, "source", source);
    ReflectionTestUtils.setField(replay, "booking", booking);
    ReflectionTestUtils.setField(state, "securities", securities);
    ReflectionTestUtils.setField(state, "terminalSchedule", schedule);
    ReflectionTestUtils.setField(state, "fx",
        new AlgoReplayFx(AlgoReplayFxTest.snapshot(Map.of()), "CHF", (_, _, _) -> 1.0, _ -> {
        }));
    ReflectionTestUtils.setField(state, "inputs",
        new AlgoReplayInputs.Snapshot(2, false, false, 0, Map.of(), Map.of(), instruments, List.of()));
    when(state.idTenant()).thenReturn(65);
    cash.setIdSecuritycashAccount(10);
    cash.setCurrency("USD");
  }

  @Test
  @DisplayName("Seventy-three completed expiries require only one ledger read across the entire run")
  void manyExpiredInstrumentsShareOneRead() throws Exception {
    List<Transaction> ledger = new ArrayList<>();
    for (int id = 1; id <= 73; id++) {
      Security security = instrument(id, DAY.minusYears(1), true);
      ledger.add(trade(security, DAY, TransactionType.ACCUMULATE));
    }
    when(source.transactions(65, DAY.plusDays(1))).thenReturn(ledger);
    when(booking.redeem(eq(state), any(), any(), eq(20), eq(cash), eq(100.0), eq(DAY)))
        .thenAnswer(call -> trade(call.getArgument(1), DAY, TransactionType.REDUCE));

    replay.processTerminalEvents(state, DAY);
    for (int day = 1; day < 1711; day++) {
      replay.processTerminalEvents(state, DAY.plusDays(day));
    }

    verify(source).transactions(65, DAY.plusDays(1));
    verifyNoMoreInteractions(source);
    var order = inOrder(booking);
    for (Security security : securities.values()) {
      order.verify(booking).redeem(state, security, instruments.get(security.getId()), 20, cash, 100, DAY);
    }
    verifyNoMoreInteractions(booking);
  }

  @Test
  @DisplayName("No due instruments, including legacy snapshots, require no ledger read")
  void noDueInstrumentsSkipRead() {
    instrument(1, DAY.plusDays(1), true);
    instrument(2, null, true);
    Security legacy = new Security();
    legacy.setIdSecuritycurrency(3);
    securities.put(3, legacy);

    replay.processTerminalEvents(state, DAY);

    verifyNoInteractions(source, booking);
  }

  @Test
  @DisplayName("Expired instruments without remaining holdings produce no booking")
  void closedAndAbsentPositionsAreIgnored() {
    Security sold = instrument(1, DAY, true);
    instrument(2, DAY, true);
    Transaction cashOnly = new Transaction();
    when(source.transactions(65, DAY.plusDays(1))).thenReturn(List.of(cashOnly,
        trade(sold, DAY.minusDays(1), TransactionType.ACCUMULATE), trade(sold, DAY, TransactionType.REDUCE)));

    replay.processTerminalEvents(state, DAY);
    replay.processTerminalEvents(state, DAY.plusDays(1));

    verify(source).transactions(65, DAY.plusDays(1));
    verifyNoMoreInteractions(source);
    verifyNoInteractions(booking);
  }

  @Test
  @DisplayName("A refused close retries with fresh holdings and a committed close is never duplicated")
  void failedCloseRetriesWithoutCachingTheLedger() throws Exception {
    Security security = instrument(1, DAY, false);
    Transaction purchase = trade(security, DAY, TransactionType.ACCUMULATE);
    Transaction close = trade(security, DAY.plusDays(1), TransactionType.REDUCE);
    when(source.transactions(65, DAY.plusDays(1))).thenReturn(List.of(purchase));
    when(source.transactions(65, DAY.plusDays(2))).thenReturn(List.of(purchase));
    when(booking.terminalClose(state, security, 20, cash, 100, DAY, DAY))
        .thenThrow(new IllegalArgumentException("REPLAY_NO_FILL_PRICE"));
    when(booking.terminalClose(state, security, 20, cash, 100, DAY.plusDays(1), DAY)).thenReturn(close);

    replay.processTerminalEvents(state, DAY);
    replay.processTerminalEvents(state, DAY.plusDays(1));
    replay.processTerminalEvents(state, DAY.plusDays(2));

    verify(booking).terminalClose(state, security, 20, cash, 100, DAY, DAY);
    verify(booking).terminalClose(state, security, 20, cash, 100, DAY.plusDays(1), DAY);
    verify(booking, times(2)).terminalClose(eq(state), eq(security), eq(20), eq(cash), eq(100.0), any(), eq(DAY));
    verify(source).transactions(65, DAY.plusDays(1));
    verify(source).transactions(65, DAY.plusDays(2));
    verifyNoMoreInteractions(source);
    verify(state).write(eq(AlgoEventType.UNAVAILABLE), eq(DAY), isNull(), eq(1), isNull(), isNull(), isNull(), isNull(),
        any(), any());
    verify(state).write(AlgoEventType.TERMINAL_CLOSE, DAY.plusDays(1), null, 1, 100.0, 1.0, 100.0, "USD",
        "REPLAY_TERMINAL_CLOSE_CATCH_UP", null);
  }

  @Test
  @DisplayName("A future expiry reads holdings only on the first processed date on or after expiry")
  void futureExpiryWaitsWithoutReadingTheLedger() {
    instrument(1, DAY.plusDays(3), true);
    when(source.transactions(65, DAY.plusDays(5))).thenReturn(List.of());

    replay.processTerminalEvents(state, DAY);
    replay.processTerminalEvents(state, DAY.plusDays(2));
    verifyNoInteractions(source);
    replay.processTerminalEvents(state, DAY.plusDays(4));
    replay.processTerminalEvents(state, DAY.plusDays(5));

    verify(source).transactions(65, DAY.plusDays(5));
    verifyNoMoreInteractions(source);
    verifyNoInteractions(booking);
  }

  @Test
  @DisplayName("A partially successful expiry retries only the custody position left open")
  void partialFailureUsesFreshRemainingHoldings() throws Exception {
    Security security = instrument(1, DAY, false);
    Transaction first = trade(security, DAY, TransactionType.ACCUMULATE);
    Transaction second = trade(security, DAY, TransactionType.ACCUMULATE);
    second.setIdSecurityaccount(21);
    Transaction firstClose = trade(security, DAY, TransactionType.REDUCE);
    Transaction secondClose = trade(security, DAY.plusDays(1), TransactionType.REDUCE);
    secondClose.setIdSecurityaccount(21);
    when(source.transactions(65, DAY.plusDays(1))).thenReturn(List.of(first, second));
    when(source.transactions(65, DAY.plusDays(2))).thenReturn(List.of(first, second, firstClose));
    when(booking.terminalClose(state, security, 20, cash, 100, DAY, DAY)).thenReturn(firstClose);
    when(booking.terminalClose(state, security, 21, cash, 100, DAY, DAY))
        .thenThrow(new IllegalArgumentException("REPLAY_FILL_REJECTED"));
    when(booking.terminalClose(state, security, 21, cash, 100, DAY.plusDays(1), DAY)).thenReturn(secondClose);

    replay.processTerminalEvents(state, DAY);
    replay.processTerminalEvents(state, DAY.plusDays(1));
    replay.processTerminalEvents(state, DAY.plusDays(2));

    verify(booking).terminalClose(state, security, 20, cash, 100, DAY, DAY);
    verify(booking).terminalClose(state, security, 21, cash, 100, DAY, DAY);
    verify(booking).terminalClose(state, security, 21, cash, 100, DAY.plusDays(1), DAY);
    verify(booking, times(3)).terminalClose(eq(state), eq(security), anyInt(), eq(cash), eq(100.0), any(), eq(DAY));
    verify(source).transactions(65, DAY.plusDays(1));
    verify(source).transactions(65, DAY.plusDays(2));
    verifyNoMoreInteractions(source);
  }

  @Test
  @DisplayName("A redemption failure still aborts terminal processing")
  void redemptionFailureStillPropagates() throws Exception {
    Security security = instrument(1, DAY, true);
    when(source.transactions(65, DAY.plusDays(1)))
        .thenReturn(List.of(trade(security, DAY, TransactionType.ACCUMULATE)));
    when(booking.redeem(state, security, instruments.get(1), 20, cash, 100, DAY))
        .thenThrow(new IllegalArgumentException("redemption refused"));

    assertThatThrownBy(() -> replay.processTerminalEvents(state, DAY)).isInstanceOf(IllegalStateException.class)
        .hasCauseInstanceOf(IllegalArgumentException.class);
  }

  private Security instrument(int id, LocalDate expiry, boolean bond) {
    Security security = new Security();
    security.setIdSecuritycurrency(id);
    security.setCurrency("USD");
    Assetclass assetclass = new Assetclass();
    assetclass.setSpecialInvestmentInstrument(SpecialInvestmentInstruments.DIRECT_INVESTMENT);
    security.setAssetClass(assetclass);
    securities.put(id, security);
    instruments.put(id, new AlgoReplayInputs.Instrument("USD", null, null, null, null, null, bond, null, null, null,
        null, List.of(), List.of(), null, expiry));
    schedule.register(id, expiry);
    return security;
  }

  private Transaction trade(Security security, LocalDate date, TransactionType type) {
    Transaction transaction = new Transaction();
    transaction.setSecuritycurrency(security);
    transaction.setCashaccount(cash);
    transaction.setIdSecurityaccount(20);
    transaction.setTransactionTime(date.atStartOfDay());
    transaction.setTransactionType(type);
    transaction.setUnits(100.0);
    transaction.setQuotation(1.0);
    transaction.setCashaccountAmount(100.0);
    return transaction;
  }
}
