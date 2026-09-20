package grafioschtrader.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AlgoReplayStateTest {
  private static final LocalDate DAY = LocalDate.of(2020, 1, 1);
  private final AlgoReplayState state = mock(AlgoReplayState.class, CALLS_REAL_METHODS);

  @BeforeEach
  void setup() {
    ReflectionTestUtils.setField(state, "valuationDates", new ArrayList<>());
    ReflectionTestUtils.setField(state, "decisionEquity", new LinkedHashMap<>());
    ReflectionTestUtils.setField(state, "externalCashFlows", new TreeMap<>());
  }

  @Test
  void idleDaysNeverValueTheLedgerAndFeesKeepThePreOrderEquity() {
    AtomicInteger valuations = new AtomicInteger();
    AtomicInteger equity = new AtomicInteger(1000);
    for (int i = 0; i < 1711; i++) {
      state.observeEquity(DAY.plusDays(i), () -> {
        valuations.incrementAndGet();
        return snapshot(equity.get());
      });
    }
    assertThat(valuations.get()).isZero();
    assertThat(state.equityNow()).isEqualTo(1000);
    equity.set(900); // A fee or a funding movement must not alter the base of the next fee on this day.
    assertThat(state.equityNow()).isEqualTo(1000);
    assertThat(valuations.get()).isEqualTo(1);
    state.observeEquity(DAY.plusDays(1711), () -> snapshot(equity.get()));
    assertThat(state.equityNow()).isEqualTo(900);
  }

  @Test
  void deferredReportKeepsFutureDepositsOutOfEarlierReturns() {
    state.addExternalCashFlow(DAY.plusDays(1), "USD", 100);
    state.addExternalCashFlow(DAY.plusDays(2), "USD", -20);
    state.addExternalCashFlow(DAY.plusDays(4), "CHF", 50);
    assertThat(state.consumeExternalCashFlow(DAY, Map.of("USD", 0.8))).isZero();
    // Day 1 is unpriced: the report consumes nothing until day 2, using day 2's conversion rate.
    assertThat(state.consumeExternalCashFlow(DAY.plusDays(2), Map.of("USD", 0.9))).isEqualTo(72);
    assertThat(state.consumeExternalCashFlow(DAY.plusDays(3), Map.of())).isZero();
    assertThat(state.consumeExternalCashFlow(DAY.plusDays(4), Map.of("CHF", 1.0))).isEqualTo(50);
    assertThat(state.externalCashFlows).isEmpty();
  }

  private AlgoHistoricalValuationService.Snapshot snapshot(double equity) {
    return new AlgoHistoricalValuationService.Snapshot("CHF", List.of(), Map.of(), Map.of("CHF", 1.0), equity, 0,
        List.of());
  }
}
