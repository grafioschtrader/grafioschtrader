package grafioschtrader.service;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDate;
import java.util.*;

import org.junit.jupiter.api.Test;

import grafioschtrader.service.AlgoClassRebalancingAllocator.Candidate;

/** Hand-calculated class adjustments prove ranking, bands and limits independently of repositories. */
class AlgoClassRebalancingAllocatorTest {
  private static final LocalDate DAY = LocalDate.of(2020, 2, 7);

  @Test
  void buysMostUnderweightFirstAndUsesItsFullBand() {
    var result = allocate(100_000, 60_000, 5, 1, new Candidate(1, 50, 40_000, 999L, true),
        new Candidate(2, 50, 20_000, 1L, true));
    assertThat(result.changes()).containsExactly(entry(2, 35_000.0));
    assertThat(result.residual()).isEqualTo(5_000);
    assertThat(result.reason()).isEqualTo("REBALANCE_TRADE_LIMIT");
  }

  @Test
  void withinBandNamesCanAbsorbClassPurchases() {
    var result = allocate(100_000, 94_000, 5, 1, new Candidate(1, 50, 47_000, 200L, true),
        new Candidate(2, 50, 47_000, 100L, true));
    assertThat(result.changes()).containsExactly(entry(1, 6_000.0));
    assertThat(result.residual()).isZero();
  }

  @Test
  void salesPreferOverweightAndStopAtLowerBand() {
    var result = allocate(100_000, 160_000, 5, 1, new Candidate(1, 50, 90_000, null, true),
        new Candidate(2, 50, 70_000, 999L, true));
    assertThat(result.changes()).containsExactly(entry(1, -45_000.0));
    assertThat(result.residual()).isEqualTo(-15_000);
  }

  @Test
  void missingVolumeRanksAfterZeroAndDoesNotMakeAnInstrumentIneligible() {
    var result = allocate(100, 80, 5, 1, new Candidate(1, 50, 40, null, true), new Candidate(2, 50, 40, 0L, true));
    assertThat(result.changes()).containsExactly(entry(2, 15.0));
    assertThat(allocate(100, 0, 5, 1, new Candidate(1, 100, 0, null, true)).changes()).containsExactly(entry(1, 100.0));
  }

  @Test
  void randomTiesAreReproducibleAndIndependentOfInputOrder() {
    var a = new Candidate(1, 50, 40, 100L, true);
    var b = new Candidate(2, 50, 40, 100L, true);
    assertThat(allocate(100, 80, 5, 1, a, b)).isEqualTo(allocate(100, 80, 5, 1, b, a));
  }

  @Test
  void zeroBandRestoresExactTargetsAndZeroWeightNeverBuys() {
    var result = allocate(100, 50, 0, 3, new Candidate(1, 60, 10, 1L, true), new Candidate(2, 40, 40, 1L, true),
        new Candidate(3, 0, 0, 999L, true));
    assertThat(result.changes()).containsExactly(entry(1, 50.0));
  }

  @Test
  void zeroTargetLiquidatesWithoutDivisionByZero() {
    var result = allocate(0, 100, 5, 3, new Candidate(1, 50, 20, null, true), new Candidate(2, 50, 80, null, true));
    assertThat(result.changes()).containsExactly(entry(2, -80.0), entry(1, -20.0));
    assertThat(result.residual()).isZero();
  }

  @Test
  void unavailableCapacityLeavesResidualAndDoesNotConsumeTradeSlot() {
    var result = allocate(100, 10, 5, 1, new Candidate(1, 50, 0, 1L, false), new Candidate(2, 50, 10, null, true));
    assertThat(result.changes()).containsExactly(entry(2, 45.0));
    assertThat(result.residual()).isEqualTo(45);
    var noCapacity = allocate(100, 10, 5, 3, new Candidate(1, 50, 10, null, false));
    assertThat(noCapacity.changes()).isEmpty();
    assertThat(noCapacity.reason()).isEqualTo("REBALANCE_BAND_OR_ELIGIBILITY_LIMIT");
  }

  @Test
  void doesNotTradeInOppositeDirectionToFixInternalDrift() {
    var result = allocate(100, 90, 5, 3, new Candidate(1, 50, 70, 1L, true), new Candidate(2, 50, 20, 1L, true));
    assertThat(result.changes()).containsExactly(entry(2, 10.0));
  }

  @Test
  void bandEdgesAreInclusiveAndInvalidSettingsAreRejected() {
    assertThat(allocate(100, 100, 5, 1, new Candidate(1, 100, 100, null, true)).changes()).isEmpty();
    for (double invalid : new double[] { -1, 101, Double.NaN, Double.POSITIVE_INFINITY })
      assertThatThrownBy(() -> allocate(100, 0, invalid, 1)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> allocate(100, 0, 5, 0)).isInstanceOf(IllegalArgumentException.class);
  }

  private AlgoClassRebalancingAllocator.Selection allocate(double target, double actual, double band, int limit,
      Candidate... candidates) {
    return AlgoClassRebalancingAllocator.allocate(target, actual, band, limit, List.of(candidates), 237, 238, DAY);
  }
}
