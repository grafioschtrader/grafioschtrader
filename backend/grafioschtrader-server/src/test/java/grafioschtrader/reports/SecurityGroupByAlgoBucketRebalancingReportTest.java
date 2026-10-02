package grafioschtrader.reports;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import grafioschtrader.algo.RebalancingPlan;
import grafioschtrader.entities.Security;
import grafioschtrader.reportviews.securityaccount.SecurityPositionDynamicGrandSummary;
import grafioschtrader.reportviews.securityaccount.SecurityPositionDynamicGroupSummary;
import grafioschtrader.reportviews.securityaccount.SecurityPositionSummary;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.service.GlobalparametersService;
import grafioschtrader.types.AlgoRecommendationAction;

/** Exercises the report enrichment after holdings valuation, without starting a Spring context or accessing a DB. */
class SecurityGroupByAlgoBucketRebalancingReportTest {
  private final SecurityJpaRepository securities = mock(SecurityJpaRepository.class);
  private final GlobalparametersService parameters = mock(GlobalparametersService.class);

  @Test
  void disjointPortfolioIncludesEveryTargetWithoutChangingValuation() {
    var report = report();
    var grand = summary();
    var unallocated = new SecurityPositionDynamicGroupSummary<String>("Unallocated");
    var holding = position(99);
    holding.units = 10;
    holding.accountValueSecurityMC = 100;
    unallocated.addToGroupSummaryAndCalcGroupTotals(holding);
    grand.calcGrandTotal(unallocated);
    when(securities.findAllById(any())).thenReturn(List.of(security(1), security(2)));

    report.completeComparison(grand);

    assertEquals(100.0, grand.overallAllocationMismatchPercentage);
    assertEquals(100.0, grand.grandAccountValueSecurityMC);
    assertEquals(List.of("First", "Second", "Empty", "Unallocated"), grand.securityPositionGroupSummaryList.stream()
        .map(group -> ((SecurityPositionDynamicGroupSummary<?>) group).groupField).toList());
    for (int index = 0; index < 2; index++) {
      var group = grand.securityPositionGroupSummaryList.get(index);
      var target = group.securityPositionSummaryList.getFirst();
      assertTrue(target.comparisonOnly);
      assertEquals(0, target.units);
      assertEquals(0, target.valueSecurityMC);
      assertEquals(0, group.groupAccountValueSecurityMC);
      assertEquals(50.0, target.targetPercentage);
      assertEquals(0.0, target.actualPercentage);
      assertEquals(-50.0, target.deviationPercentage);
      assertEquals(AlgoRecommendationAction.REBALANCE_BUY, target.recommendedAction);
      assertEquals(50.0, target.recommendedAmount);
      assertNull(target.usedIdSecurityaccount);
    }
    assertFalse(holding.comparisonOnly);
    assertEquals(1, unallocated.securityPositionSummaryList.size());
    verify(securities).findAllById(List.of(1, 2));
  }

  @Test
  void retainedClosedPositionIsNotDuplicatedAndMissingPositionIsStillAdded() {
    var report = report();
    var grand = summary();
    var first = new SecurityPositionDynamicGroupSummary<String>("First");
    var closed = position(1);
    closed.gainLossSecurityMC = 12;
    first.addToGroupSummaryAndCalcGroupTotals(closed);
    grand.calcGrandTotal(first);
    when(securities.findAllById(any())).thenReturn(List.of(security(2)));

    report.completeComparison(grand);

    assertEquals(1, first.securityPositionSummaryList.size());
    assertFalse(closed.comparisonOnly);
    assertEquals(50.0, closed.targetPercentage);
    assertEquals(12.0, grand.grandGainLossSecurityMC);
    verify(securities).findAllById(List.of(2));
  }

  @Test
  void allExistingPositionsNeedNoMetadataLookup() {
    var report = report();
    var grand = summary();
    for (int id = 1; id <= 2; id++) {
      var group = new SecurityPositionDynamicGroupSummary<String>(id == 1 ? "First" : "Second");
      group.addToGroupSummaryAndCalcGroupTotals(position(id));
      grand.calcGrandTotal(group);
    }
    report.completeComparison(grand);
    verifyNoInteractions(securities);
    assertEquals(3, grand.securityPositionGroupSummaryList.size());
  }

  @Test
  void topLevelAndImplicitCashTargetAreReported() {
    var report = report(100_000, 15_000, 85_000, 80);
    var grand = summary();
    var cash = new SecurityPositionDynamicGroupSummary<String>("Cash");
    grand.calcGrandTotal(cash);
    when(securities.findAllById(any())).thenReturn(List.of(security(1), security(2)));

    report.completeComparison(grand);

    assertEquals(80.0, grand.getTopTargetPercentage());
    assertEquals(85.0, grand.getTopActualPercentage());
    assertEquals(5.0, grand.getTopDeviationPercentage());
    assertEquals(20.0, cash.getGroupTargetPercentage());
    assertEquals(15.0, cash.getGroupActualPercentage());
    assertEquals(-5.0, cash.getGroupDeviationPercentage());
  }

  @Test
  void zeroNetEquityLeavesTheTopShareAndCashTargetOpen() {
    var report = report(0, 0, 0, 80);
    var grand = summary();
    var cash = new SecurityPositionDynamicGroupSummary<String>("Cash");
    grand.calcGrandTotal(cash);
    when(securities.findAllById(any())).thenReturn(List.of(security(1), security(2)));

    report.completeComparison(grand);

    assertEquals(80.0, grand.getTopTargetPercentage());
    assertNull(grand.getTopActualPercentage());
    assertNull(grand.getTopDeviationPercentage());
    assertNull(cash.getGroupTargetPercentage());
    assertNull(cash.getGroupDeviationPercentage());
  }

  private SecurityGroupByAlgoBucketRebalancingReport report() {
    return report(100, 0, 100, 100);
  }

  private SecurityGroupByAlgoBucketRebalancingReport report(double netEquity, double actualCash, double grossExposure,
      double topPercentage) {
    var plan = new RebalancingPlan(7, 227, 1, "Strategy", LocalDate.of(2026, 9, 20), "CHF", netEquity, actualCash,
        grossExposure, netEquity * topPercentage / 100, 0, topPercentage, 5, false, false, true,
        List.of(line("A", 10, 227, null, "First", 50), line("S", 11, 10, 1, "One", 50),
            line("A", 20, 227, null, "Second", 50), line("S", 21, 20, 2, "Two", 50),
            line("A", 30, 227, null, "Empty", 0)));
    var report = new SecurityGroupByAlgoBucketRebalancingReport(plan, "Cash", "Unallocated");
    ReflectionTestUtils.setField(report, "securityJpaRepository", securities);
    ReflectionTestUtils.setField(report, "globalparametersService", parameters);
    when(parameters.getCurrencyPrecision()).thenReturn(Map.of("CHF", 2));
    return report;
  }

  private RebalancingPlan.Line line(String type, int node, int parent, Integer security, String label, double target) {
    return new RebalancingPlan.Line(type, node, parent, security, label, false, target, 0.0, -target, target, 0.0,
        AlgoRecommendationAction.REBALANCE_BUY, target, 5.0, "REBALANCE_CLASS_INCREASE");
  }

  private SecurityPositionDynamicGrandSummary<SecurityPositionDynamicGroupSummary<String>> summary() {
    return new SecurityPositionDynamicGrandSummary<>("CHF", 2);
  }

  private SecurityPositionSummary position(int id) {
    return new SecurityPositionSummary("CHF", security(id), Map.of("CHF", 2));
  }

  private Security security(int id) {
    var security = new Security();
    security.setIdSecuritycurrency(id);
    security.setName("Security " + id);
    security.setCurrency("CHF");
    return security;
  }
}
