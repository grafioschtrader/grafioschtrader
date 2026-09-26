package grafioschtrader.algo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import grafioschtrader.types.AlgoRecommendationAction;

class RebalancingPlanMismatchTest {
  @Test
  void disjointAndCashOnlyHoldingsHaveCompleteMismatch() {
    assertEquals(100.0, plan(100, line(1, 60, 0), line(2, 40, 0)).overallAllocationMismatchPercentage());
    assertEquals(100.0, plan(0, line(1, 60, 0), line(2, 40, 0)).overallAllocationMismatchPercentage());
  }

  @Test
  void matchingCompositionIgnoresTotalInvestedAmount() {
    assertEquals(0.0, plan(200, line(1, 60, 120), line(2, 40, 80)).overallAllocationMismatchPercentage(), 1e-10);
  }

  @Test
  void overweightAndMissingSharesBothContribute() {
    assertEquals(25.0, plan(100, line(1, 50, 75), line(2, 50, 25)).overallAllocationMismatchPercentage(), 1e-10);
    // Half the actual exposure belongs to an instrument outside the strategy.
    assertEquals(50.0, plan(100, line(1, 50, 50), line(2, 50, 0)).overallAllocationMismatchPercentage(), 1e-10);
  }

  @Test
  void duplicateAssignmentsSumTargetsButDoNotDuplicateActualExposure() {
    assertEquals(0.0,
        plan(100, line(1, 25, 50), line(1, 25, 50), line(2, 50, 50)).overallAllocationMismatchPercentage(), 1e-10);
  }

  @Test
  void absentOrNonPositiveTargetsAreUnavailable() {
    assertNull(plan(100).overallAllocationMismatchPercentage());
    assertNull(plan(100, line(1, 0, 100)).overallAllocationMismatchPercentage());
    assertNull(plan(100, line(1, -100, 100)).overallAllocationMismatchPercentage());
  }

  private RebalancingPlan plan(double gross, RebalancingPlan.Line... lines) {
    return new RebalancingPlan(7, 227, 1, "Strategy", LocalDate.of(2026, 9, 20), "CHF", 100, 100 - gross, gross, 100, 0,
        100, 5, false, false, false, List.of(lines));
  }

  private RebalancingPlan.Line line(int id, double target, double actual) {
    return new RebalancingPlan.Line("S", id, 10, id, "Security " + id, false, target, actual, actual - target, target,
        actual, AlgoRecommendationAction.REBALANCE_HOLD, null, null, "REBALANCE_NOT_SELECTED");
  }
}
