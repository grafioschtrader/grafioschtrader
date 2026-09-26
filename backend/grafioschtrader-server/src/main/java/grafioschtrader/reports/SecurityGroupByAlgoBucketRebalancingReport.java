package grafioschtrader.reports;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import grafioschtrader.algo.RebalancingPlan;
import grafioschtrader.algo.strategy.model.StrategyHelper;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Tenant;
import grafioschtrader.reportviews.DateTransactionCurrencypairMap;
import grafioschtrader.reportviews.securityaccount.SecurityPositionDynamicGrandSummary;
import grafioschtrader.reportviews.securityaccount.SecurityPositionDynamicGroupSummary;
import grafioschtrader.reportviews.securityaccount.SecurityPositionGroupSummary;
import grafioschtrader.reportviews.securityaccount.SecurityPositionSummary;

/**
 * The tenant portfolio seen through one AlgoTop: holdings and cash grouped by the strategy's allocation buckets,
 * completed with zero-holding rows for strategy securities absent from the portfolio. Every strategy row carries the
 * comparison against its target, including when the portfolio holds none of the target instruments.
 *
 * <p>
 * Grouping by bucket rather than by asset class type is what makes the comparison meaningful. A target exists per
 * bucket, and a bucket is not an asset class: a named tactical bucket has no asset class type at all, and one asset
 * class type can be split over several buckets. Two groups complete the picture and have no target of their own - cash,
 * which is what is left outside the investment budget, and the holdings the hierarchy does not mention, which the user
 * can see and reduce but which no target describes.
 * </p>
 *
 * <p>
 * The allocation figures are not recalculated here. They are taken from the {@link RebalancingPlan}, which resolved the
 * parent-relative weights of the hierarchy against total net equity once, so the report, the persisted recommendations
 * and the notification cannot disagree about what the target is.
 * </p>
 *
 * <p>
 * Built per request rather than as a component: it carries the plan of one AlgoTop and the language of one user.
 * </p>
 */
public class SecurityGroupByAlgoBucketRebalancingReport extends SecurityGroupByBaseReport<String> {

  private final RebalancingPlan plan;
  private final String cashLabel;
  private final String unallocatedLabel;
  private final Map<Integer, String> bucketOfSecurity = new HashMap<>();
  private final Map<Integer, RebalancingPlan.Line> lineOfSecurity = new LinkedHashMap<>();
  private final Map<String, RebalancingPlan.Line> lineOfBucket = new LinkedHashMap<>();

  /**
   * @param plan             the resolved comparison of the selected AlgoTop
   * @param cashLabel        translated name of the group holding the cash accounts
   * @param unallocatedLabel translated name of the group holding positions the hierarchy does not mention
   */
  public SecurityGroupByAlgoBucketRebalancingReport(RebalancingPlan plan, String cashLabel, String unallocatedLabel) {
    super(null);
    this.plan = plan;
    this.cashLabel = cashLabel;
    this.unallocatedLabel = unallocatedLabel;
    Map<Integer, String> labelOfNode = new HashMap<>();
    for (RebalancingPlan.Line line : plan.lines()) {
      if (StrategyHelper.ASSET_CLASS_LEVEL_LETTER.equals(line.levelType())) {
        labelOfNode.put(line.idNode(), line.label());
        lineOfBucket.put(line.label(), line);
      }
    }
    for (RebalancingPlan.Line line : plan.lines()) {
      if (StrategyHelper.SECURITY_LEVEL_LETTER.equals(line.levelType()) && line.idSecuritycurrency() != null) {
        lineOfSecurity.put(line.idSecuritycurrency(), line);
        bucketOfSecurity.put(line.idSecuritycurrency(), labelOfNode.get(line.idParentNode()));
      }
    }
  }

  @Override
  protected SecurityPositionDynamicGrandSummary<SecurityPositionDynamicGroupSummary<String>> createGroupsAndCalcGrandTotal(
      final Tenant tenant, List<SecurityPositionSummary> securityPositionSummaryList,
      DateTransactionCurrencypairMap dateCurrencyMap) throws Exception {
    addCashaccountAsASecurity(tenant, securityPositionSummaryList, dateCurrencyMap);
    var grandSummary = super.createGroupsAndCalcGrandTotal(tenant, securityPositionSummaryList, dateCurrencyMap);
    completeComparison(grandSummary);
    return grandSummary;
  }

  /** Complete the valued holdings with strategy-only rows, then attach the comparison to all rows and groups. */
  void completeComparison(SecurityPositionDynamicGrandSummary<SecurityPositionDynamicGroupSummary<String>> grand) {
    addMissingStrategyPositions(grand);
    applyPlan(grand);
  }

  /** Add zero holdings after valuation: target-only instruments must neither affect totals nor require FX quotes. */
  private void addMissingStrategyPositions(
      SecurityPositionDynamicGrandSummary<SecurityPositionDynamicGroupSummary<String>> grand) {
    Map<String, SecurityPositionDynamicGroupSummary<String>> groups = new LinkedHashMap<>();
    Set<Integer> present = new HashSet<>();
    for (SecurityPositionGroupSummary group : grand.securityPositionGroupSummaryList) {
      @SuppressWarnings("unchecked")
      var dynamic = (SecurityPositionDynamicGroupSummary<String>) group;
      groups.put(dynamic.groupField, dynamic);
      group.securityPositionSummaryList
          .forEach(position -> present.add(position.getSecurity().getIdSecuritycurrency()));
    }
    for (String bucket : lineOfBucket.keySet()) {
      if (!groups.containsKey(bucket)) {
        var group = new SecurityPositionDynamicGroupSummary<>(bucket);
        groups.put(bucket, group);
        grand.securityPositionGroupSummaryList.add(group);
      }
    }
    List<Integer> missing = lineOfSecurity.keySet().stream().filter(id -> !present.contains(id)).toList();
    if (missing.isEmpty()) {
      return;
    }
    Map<Integer, Security> securities = securityJpaRepository.findAllById(missing).stream()
        .collect(Collectors.toMap(Security::getIdSecuritycurrency, Function.identity()));
    for (Integer id : missing) {
      Security security = securities.get(id);
      if (security == null) {
        throw new IllegalStateException("Strategy security no longer exists: " + id);
      }
      var position = new SecurityPositionSummary(grand.currency, security,
          globalparametersService.getCurrencyPrecision());
      position.comparisonOnly = true;
      groups.get(bucketOfSecurity.get(id)).addToGroupSummaryAndCalcGroupTotals(position);
    }
  }

  /**
   * A cash account arrives as a pseudo security with a negative id, and a holding the hierarchy never mentions has no
   * bucket. Both are groups of their own rather than an omission: leaving them out would show an allocation that adds
   * up while the portfolio it describes does not.
   */
  @Override
  protected String getGroupValue(Security security) {
    if (security.getIdSecuritycurrency() < 0) {
      return cashLabel;
    }
    String bucket = bucketOfSecurity.get(security.getIdSecuritycurrency());
    return bucket == null ? unallocatedLabel : bucket;
  }

  private void applyPlan(SecurityPositionDynamicGrandSummary<SecurityPositionDynamicGroupSummary<String>> grand) {
    for (SecurityPositionGroupSummary group : grand.securityPositionGroupSummaryList) {
      applyToGroup((SecurityPositionDynamicGroupSummary<?>) group);
    }
    sortByPlanOrder(grand);
    grand.grandNetEquityMC = plan.netEquity();
    grand.grandActualCashMC = plan.actualCash();
    grand.grandGrossExposureMC = plan.grossExposure();
    grand.grandInvestmentBudgetMC = plan.investmentBudget();
    grand.grandUnusedTacticalBudgetMC = plan.unusedTacticalBudget();
    grand.classAdjustments = plan.classAdjustments();
    grand.toleranceThreshold = plan.tolerancePercentage();
    grand.overallAllocationMismatchPercentage = plan.overallAllocationMismatchPercentage();
    grand.exposureBreach = plan.exposureBreach();
    grand.valuationDate = plan.valuationDate();
    grand.periodicDue = plan.periodicDue();
    grand.lastCheckpointDate = plan.lastCheckpointDate();
    grand.nextCheckpointDate = plan.nextCheckpointDate();
  }

  private void applyToGroup(SecurityPositionDynamicGroupSummary<?> group) {
    for (SecurityPositionSummary position : group.securityPositionSummaryList) {
      RebalancingPlan.Line line = lineOfSecurity.get(position.getSecurity().getIdSecuritycurrency());
      if (line != null) {
        position.targetPercentage = line.targetPercentage();
        position.parentDeviation = line.parentDeviation();
        plan.classAdjustments().stream().filter(a -> a.idNode().equals(line.idParentNode())).findFirst()
            .ifPresent(a -> position.securityDeviationPercentage = a.securityDeviationPercentage());
        position.actualPercentage = line.actualPercentage();
        position.deviationPercentage = line.deviation();
        position.recommendedAction = line.action();
        position.recommendedAmount = line.recommendedAmount();
        position.recommendedUnits = line.recommendedUnits();
        position.recommendationReason = line.rationale();
      }
    }
    RebalancingPlan.Line bucket = lineOfBucket.get(group.groupField);
    if (bucket != null) {
      group.groupTargetPercentage = bucket.targetPercentage();
      group.groupActualPercentage = bucket.actualPercentage();
      group.groupDeviationPercentage = bucket.deviation();
      group.groupRecommendedAction = bucket.action();
      group.groupRecommendedAmount = bucket.recommendedAmount();
      group.groupRecommendationReason = bucket.rationale();
      plan.classAdjustments().stream().filter(a -> a.idNode().equals(bucket.idNode())).findFirst().ifPresent(a -> {
        group.groupParentDeviation = a.parentDeviation();
        group.groupSecurityDeviationPercentage = a.securityDeviationPercentage();
        group.groupMaxTradedSecuritiesPerAssetclass = a.maxTradedSecuritiesPerAssetclass();
        group.groupRequestedAdjustment = a.requestedAdjustment();
        group.groupRecommendedAmount = Math.abs(a.plannedAdjustment());
        group.groupResidual = a.residual();
        if (a.limitingReason() != null)
          group.groupRecommendationReason = a.limitingReason();
        if (Math.abs(a.plannedAdjustment()) <= 1e-8)
          group.groupRecommendedAction = grafioschtrader.types.AlgoRecommendationAction.REBALANCE_HOLD;
      });
    } else if (cashLabel.equals(group.groupField)) {
      group.groupActualPercentage = share(plan.actualCash());
    } else if (unallocatedLabel.equals(group.groupField)) {
      group.groupActualPercentage = share(plan.grossExposure() - allocatedExposure());
    }
  }

  private Double share(double amount) {
    return plan.netEquity() == 0 ? null : amount / plan.netEquity() * 100.0;
  }

  private double allocatedExposure() {
    return lineOfBucket.values().stream().mapToDouble(line -> line.actualAmount() == null ? 0 : line.actualAmount())
        .sum();
  }

  /**
   * Groups are collected in a hash map, so their order is arbitrary. The plan has an order - the buckets as the
   * hierarchy lists them, then what the hierarchy does not describe - and a report the user compares against a
   * configuration should follow the configuration.
   */
  private void sortByPlanOrder(SecurityPositionDynamicGrandSummary<SecurityPositionDynamicGroupSummary<String>> grand) {
    List<String> order = new ArrayList<>(lineOfBucket.keySet());
    order.add(cashLabel);
    order.add(unallocatedLabel);
    grand.securityPositionGroupSummaryList.sort(Comparator.comparingInt(group -> {
      int index = order.indexOf(((SecurityPositionDynamicGroupSummary<?>) group).groupField);
      return index < 0 ? order.size() : index;
    }));
  }

}
