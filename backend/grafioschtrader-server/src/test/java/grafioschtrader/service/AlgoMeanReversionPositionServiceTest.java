package grafioschtrader.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.*;
import java.util.*;

import org.junit.jupiter.api.Test;

import grafiosch.service.EntityLimitService;
import grafioschtrader.entities.*;
import grafioschtrader.repository.*;
import grafioschtrader.types.TransactionType;

/** Fill accounting is rebuilt from transactions, so retries and edits cannot accumulate phantom quantities. */
class AlgoMeanReversionPositionServiceTest {
  private final SimulationSourceRepository source = mock(SimulationSourceRepository.class);
  private final AlgoExecutionStateJpaRepository states = mock(AlgoExecutionStateJpaRepository.class);
  private final SecuritysplitJpaRepository splits = mock(SecuritysplitJpaRepository.class);
  private final EntityLimitService limits = mock(EntityLimitService.class);
  private final AlgoMeanReversionPositionService service = new AlgoMeanReversionPositionService(source, states, splits,
      limits, mock(TenantJpaRepository.class), mock(AlgoAlertScopeResolver.class));
  private final LocalDate day = LocalDate.of(2026, 9, 7);
  private final Security security = mock(Security.class);

  Transaction trade(int id, int strategy, int daysAgo, TransactionType type, double units, double price) {
    Transaction t = mock(Transaction.class);
    when(t.getId()).thenReturn(id);
    when(t.getIdAlgoStrategy()).thenReturn(strategy);
    when(t.getConnectedIdTransaction()).thenReturn(null);
    when(t.getSecurity()).thenReturn(security);
    when(t.getUnits()).thenReturn(units);
    when(t.getQuotation()).thenReturn(price);
    when(t.getTransactionType()).thenReturn(type);
    when(t.getTransactionTime()).thenReturn(day.minusDays(daysAgo).atTime(12, 0));
    when(t.getTransactionDateAsLocalDate()).thenReturn(day.minusDays(daysAgo));
    return t;
  }

  void ledger(Transaction... trades) {
    when(security.getId()).thenReturn(3);
    when(splits.getSecuritysplitMapByIdSecuritycurrency(3)).thenReturn(Map.of());
    when(source.transactions(1, day.plusDays(1))).thenReturn(List.of(trades));
  }

  @Test
  void additionSignalsCountOnceAndOpeningPartialFillsRemainOpening() {
    var open1 = trade(10, 2, 5, TransactionType.ACCUMULATE, 4, 100);
    var open2 = trade(11, 2, 4, TransactionType.ACCUMULATE, 6, 100);
    var add1 = trade(12, 2, 3, TransactionType.ACCUMULATE, 2, 80);
    var add2 = trade(13, 2, 2, TransactionType.ACCUMULATE, 3, 80);
    when(open1.getAlgoSignalId()).thenReturn("entry");
    when(open2.getAlgoSignalId()).thenReturn("entry");
    when(add1.getAlgoSignalId()).thenReturn("addition");
    when(add2.getAlgoSignalId()).thenReturn("addition");
    ledger(open1, open2, add1, add2);
    var p = service.reconcile(1, 2, security, day);
    assertEquals(10, p.initialUnits());
    assertEquals(1, p.executedAdds());
    assertEquals(1400.0 / 15, p.averagePrice(), 1e-8);
    ledger(open1, open2);
    assertEquals(0, service.reconcile(1, 2, security, day).executedAdds());
    ledger(open1, open2, add1, add2, trade(14, 2, 1, TransactionType.ACCUMULATE, 1, 75));
    assertEquals(2, service.reconcile(1, 2, security, day).executedAdds());
  }

  @Test
  void partialFillsAverageCostAndReconstruction() {
    ledger(trade(10, 2, 4, TransactionType.ACCUMULATE, 10, 100), trade(11, 2, 3, TransactionType.ACCUMULATE, 10, 80),
        trade(12, 2, 2, TransactionType.REDUCE, 5, 120), trade(13, 99, 1, TransactionType.ACCUMULATE, 200, 50));
    var first = service.reconcile(1, 2, security, day);
    assertEquals(15, first.signedUnits());
    assertEquals(90, first.averagePrice());
    assertEquals(100, first.initialPrice());
    assertEquals(3, first.tradesIn30Days());
    // The opening fill sizes the profit taking plan; the later add does not enlarge it, the exit counts against it.
    assertEquals(10, first.initialUnits());
    assertEquals(5, first.realizedExitUnits());
    assertEquals(first, service.reconcile(1, 2, security, day));
  }

  @Test
  void closeReopenCreatesFreshLifecycleAndKeepsExitCooldown() {
    ledger(trade(10, 2, 4, TransactionType.ACCUMULATE, 10, 100), trade(11, 2, 2, TransactionType.REDUCE, 10, 120),
        trade(12, 2, 1, TransactionType.ACCUMULATE, 5, 80));
    var p = service.reconcile(1, 2, security, day);
    assertEquals(12, p.lifecycle());
    assertEquals(5, p.signedUnits());
    assertEquals(80, p.initialPrice());
    assertEquals(day.minusDays(2), p.lastExit());
    // A new lifecycle starts a new plan: what the closed one gave back cannot settle a tranche of this one.
    assertEquals(5, p.initialUnits());
    assertEquals(0, p.realizedExitUnits());
  }

  @Test
  void shortFillsMirrorLongAndOvercloseIsRejected() {
    when(security.isMarginInstrument()).thenReturn(true);
    var close = trade(11, 2, 2, TransactionType.ACCUMULATE, 4, 80);
    when(close.getConnectedIdTransaction()).thenReturn(10);
    ledger(trade(10, 2, 4, TransactionType.REDUCE, 10, 100), close);
    var covered = service.reconcile(1, 2, security, day);
    assertEquals(-6, covered.signedUnits());
    assertEquals(10, covered.initialUnits());
    assertEquals(4, covered.realizedExitUnits());
    ledger(trade(10, 2, 4, TransactionType.REDUCE, 10, 100), trade(11, 2, 2, TransactionType.ACCUMULATE, 11, 80));
    assertThrows(RuntimeException.class, () -> service.reconcile(1, 2, security, day));
  }

  @Test
  void removedLedgerDeletesStaleProjection() {
    ledger();
    AlgoExecutionState old = new AlgoExecutionState();
    old.setLifecycle(10);
    when(states.findByIdTenantAndIdAlgoStrategyAndIdSecuritycurrencyOrderByLifecycle(1, 2, 3)).thenReturn(List.of(old));
    assertEquals(0, service.reconcile(1, 2, security, day).signedUnits());
    verify(states).deleteAll(argThat(rows -> {
      for (var row : rows)
        if (row == old)
          return true;
      return false;
    }));
  }

  @Test
  void unassigningOpeningCannotOrphanLaterAssignedExit() {
    var opening = trade(10, 2, 4, TransactionType.ACCUMULATE, 10, 100);
    var closing = trade(11, 2, 2, TransactionType.REDUCE, 10, 100);
    ledger(opening, closing);
    when(source.transactions(1, LocalDate.of(9999, 12, 31))).thenReturn(List.of(opening, closing));
    var edited = trade(10, 2, 4, TransactionType.ACCUMULATE, 10, 100);
    when(edited.getIdAlgoStrategy()).thenReturn(null);
    when(edited.getIdTenant()).thenReturn(1);
    assertThrows(RuntimeException.class, () -> service.validateAssignment(edited));
  }

  @Test
  void onlyUnassignedOpenQuantitiesBlockEvaluation() {
    var opening = trade(10, 2, 4, TransactionType.ACCUMULATE, 10, 100);
    var closing = trade(11, 2, 2, TransactionType.REDUCE, 10, 100);
    when(opening.getIdAlgoStrategy()).thenReturn(null);
    when(closing.getIdAlgoStrategy()).thenReturn(null);
    ledger(opening, closing);
    assertTrue(service.assignmentResolved(1, security, day));
    ledger(opening);
    assertFalse(service.assignmentResolved(1, security, day));
  }
}
