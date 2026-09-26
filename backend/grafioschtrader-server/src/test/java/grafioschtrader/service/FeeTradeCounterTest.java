package grafioschtrader.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafioschtrader.service.FeeTradeCounter.FeeTradeCounts;

/** Calendar periods and idempotency of the trade counts fee rules can refer to. */
class FeeTradeCounterTest {

  @Test
  @DisplayName("Trades are counted per calendar month, quarter and year of the priced trade")
  void calendarBoundaries() {
    FeeTradeCounter counter = new FeeTradeCounter();
    counter.record(1, 10, LocalDate.of(2025, 12, 30), "a");
    counter.record(1, 10, LocalDate.of(2026, 1, 15), "b");
    counter.record(1, 11, LocalDate.of(2026, 2, 3), "c");
    counter.record(1, 10, LocalDate.of(2026, 3, 31), "d");

    assertThat(counter.counts(1, 10, LocalDate.of(2026, 3, 31))).isEqualTo(new FeeTradeCounts(1, 3, 3, 1));
    assertThat(counter.counts(1, 10, LocalDate.of(2026, 4, 1))).isEqualTo(new FeeTradeCounts(0, 0, 3, 0));
    assertThat(counter.counts(1, 11, LocalDate.of(2026, 2, 20))).isEqualTo(new FeeTradeCounts(1, 2, 2, 1));
    assertThat(counter.counts(1, 10, LocalDate.of(2026, 1, 1))).as("the December trade belongs to another year")
        .isEqualTo(new FeeTradeCounts(0, 0, 0, 0));
  }

  @Test
  @DisplayName("Later trades, other accounts and repeated identities never count")
  void onlyEarlierTradesOfTheSameAccountCountOnce() {
    FeeTradeCounter counter = new FeeTradeCounter();
    counter.record(1, 10, LocalDate.of(2026, 5, 10), "a");
    counter.record(1, 10, LocalDate.of(2026, 5, 10), "a");
    counter.record(2, 10, LocalDate.of(2026, 5, 2), "b");
    counter.record(1, 10, LocalDate.of(2026, 5, 20), "c");

    assertThat(counter.counts(1, 10, LocalDate.of(2026, 5, 15))).isEqualTo(new FeeTradeCounts(1, 1, 1, 1));
    assertThat(counter.counts(3, 10, LocalDate.of(2026, 5, 15))).isEqualTo(FeeTradeCounts.NONE);
  }
}
