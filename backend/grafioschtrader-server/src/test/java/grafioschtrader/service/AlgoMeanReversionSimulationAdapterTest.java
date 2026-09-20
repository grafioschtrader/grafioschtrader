package grafioschtrader.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.*;
import java.util.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import grafiosch.entities.User;
import grafioschtrader.entities.*;
import grafioschtrader.repository.*;
import grafioschtrader.service.AlgoMeanReversionDecisionService.*;
import grafioschtrader.types.TransactionType;

class AlgoMeanReversionSimulationAdapterTest {
  private final AlgoTradingRepository data = mock(AlgoTradingRepository.class);
  private final TransactionJpaRepository transactions = mock(TransactionJpaRepository.class);
  private final AlgoMeanReversionPositionService positions = mock(AlgoMeanReversionPositionService.class);
  private final AlgoMeanReversionSimulationAdapter adapter = new AlgoMeanReversionSimulationAdapter(data, transactions,
      positions, mock(AlgoMeanReversionFillBudgetService.class));
  private final LocalDate day = LocalDate.of(2026, 9, 7);
  private final Context context = new Context(2, 3, 4, day, (_, _) -> List.of(), Position.empty(), 10000, 5000, 5000,
      5000, 0, 100, true);
  private final Decision decision = new Decision(Action.ENTRY, 1, 10, 100, "MEAN_REVERSION_ENTRY",
      "2:3:4:0:2026-09-07:ENTRY:1", AlgoMessageAlert.NO_TRANCHE);

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  void login(Integer parent) {
    var user = mock(User.class);
    when(user.getActualIdTenant()).thenReturn(1);
    var auth = new UsernamePasswordAuthenticationToken("test", "unused");
    auth.setDetails(user);
    SecurityContextHolder.getContext().setAuthentication(auth);
    Tenant tenant = new Tenant();
    tenant.setIdParentTenant(parent);
    when(data.lockTenant(2)).thenReturn(tenant);
  }

  Transaction fill(double units) {
    var t = new Transaction();
    var security = mock(Security.class);
    when(security.getId()).thenReturn(4);
    t.setQuotation(100.0);
    t.setSecuritycurrency(security);
    t.setUnits(units);
    t.setTransactionType(TransactionType.ACCUMULATE);
    t.setTransactionTime(day.plusDays(1).atTime(18, 0));
    return t;
  }

  @Test
  void excludedInstrumentCannotBypassTheReplayOrderBuilder() {
    login(1);
    var attempted = fill(4);
    when(attempted.getSecurity().isSimulationTradingExcluded()).thenReturn(true);
    var failure = assertThrows(IllegalArgumentException.class,
        () -> adapter.fill(context, decision, "forbidden", attempted));
    assertEquals("REPLAY_INSTRUMENT_EXCLUDED", failure.getMessage());
    verifyNoInteractions(transactions);
  }

  @Test
  void partialFillBooksOnlyThroughPortfolioPathAndReplayIsIdempotent() throws Exception {
    login(1);
    var first = fill(4);
    when(transactions.saveOnlyAttributes(any(), isNull(), any())).thenAnswer(i -> i.getArgument(0));
    assertSame(first, adapter.fill(context, decision, "fill-1", first));
    assertEquals(2, first.getIdTenant());
    assertEquals(3, first.getIdAlgoStrategy());
    when(data.fill(2, "fill-1")).thenReturn(Optional.of(first));
    assertSame(first, adapter.fill(context, decision, "fill-1", fill(4)));
    verify(transactions, times(1)).saveOnlyAttributes(any(), isNull(), any());
    verify(positions).reconcile(2, 3, first.getSecurity(), day.plusDays(1));
    when(data.filledUnits(2, decision.identity())).thenReturn(4.0);
    assertThrows(IllegalArgumentException.class, () -> adapter.fill(context, decision, "fill-2", fill(7)));
  }

  @Test
  void aTrancheBooksAReductionAndSpendsOnlyItsOwnSignal() throws Exception {
    login(1);
    var scaleOut = new Decision(Action.SCALE_OUT, 1, 3, 120, "SCALE_OUT", "2:3:4:10:2026-09-07:SCALE_OUT:1:t1", "t1");
    when(transactions.saveOnlyAttributes(any(), isNull(), any())).thenAnswer(i -> i.getArgument(0));
    var sell = fill(2);
    sell.setTransactionType(TransactionType.REDUCE);
    assertSame(sell, adapter.fill(context, scaleOut, "fill-t1-a", sell));
    assertEquals("2:3:4:10:2026-09-07:SCALE_OUT:1:t1", sell.getAlgoSignalId());
    // A partial fill leaves the rest of that tranche outstanding, and nothing beyond it can be booked on it.
    when(data.filledUnits(2, scaleOut.identity())).thenReturn(2.0);
    var rest = fill(1);
    rest.setTransactionType(TransactionType.REDUCE);
    assertSame(rest, adapter.fill(context, scaleOut, "fill-t1-b", rest));
    when(data.filledUnits(2, scaleOut.identity())).thenReturn(3.0);
    var excess = fill(1);
    excess.setTransactionType(TransactionType.REDUCE);
    assertThrows(IllegalArgumentException.class, () -> adapter.fill(context, scaleOut, "fill-t1-c", excess));
    // A buy cannot fill a signal that gives part of the position back.
    assertThrows(IllegalArgumentException.class, () -> adapter.fill(context, scaleOut, "fill-t1-d", fill(1)));
  }

  @Test
  void additionsUseEntryDirectionAndRejectAClosedLifecycle() throws Exception {
    login(1);
    when(transactions.saveOnlyAttributes(any(), isNull(), any())).thenAnswer(i -> i.getArgument(0));
    for (int direction : new int[] { 1, -1 }) {
      var p = new Position(10, direction * 10, 10, 0, 100, 100, day.minusDays(3), null, 1);
      var c = new Context(2, 3, 4, day, context.market(), p, 10000, 5000, 5000, 5000, 1000, 100, true);
      var add = new Decision(Action.ADD, direction, 5, 100, "AVERAGE_DOWN_ADD", "addition:" + direction, "");
      when(positions.reconcile(eq(2), eq(3), any(), any())).thenReturn(p);
      var fill = fill(2);
      fill.setTransactionType(direction > 0 ? TransactionType.ACCUMULATE : TransactionType.REDUCE);
      assertSame(fill, adapter.fill(c, add, "partial:" + direction, fill));
      when(positions.reconcile(eq(2), eq(3), any(), any())).thenReturn(Position.empty());
      var stale = fill(1);
      stale.setTransactionType(fill.getTransactionType());
      assertThrows(IllegalArgumentException.class, () -> adapter.fill(c, add, "stale:" + direction, stale));
    }
  }

  @Test
  void executedTrancheRetainsItsTargetMetadata() throws Exception {
    login(1);
    var scale = new Decision(Action.SCALE_OUT, 1, 3, 120, "SCALE_OUT", "scale", "t1", Map.of("t1", 3.0));
    when(transactions.saveOnlyAttributes(any(), isNull(), any())).thenAnswer(i -> i.getArgument(0));
    var fill = fill(1);
    fill.setTransactionType(TransactionType.REDUCE);
    adapter.fill(context, scale, "partial-scale", fill);
    assertEquals(Map.of("t1", 3.0), AlgoTrancheTargets.read(fill.getAlgoTrancheTargets(), 1));
  }

  @Test
  void mainTenantAndForeignSimulationCannotBook() {
    login(null);
    assertThrows(SecurityException.class, () -> adapter.fill(context, decision, "a", fill(1)));
    login(99);
    assertThrows(SecurityException.class, () -> adapter.fill(context, decision, "a", fill(1)));
    verifyNoInteractions(transactions);
  }
}
