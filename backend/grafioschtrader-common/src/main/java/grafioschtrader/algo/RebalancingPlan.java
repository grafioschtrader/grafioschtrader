package grafioschtrader.algo;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;
import grafioschtrader.algo.strategy.model.StrategyHelper;
import grafioschtrader.types.AlgoRebalancingTrigger;
import grafioschtrader.types.AlgoRecommendationAction;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The comparison of an AlgoTop against the holdings it is supposed to describe, at one closing date.
 *
 * <p>
 * Target and actual percentages remain shares of total net equity for report compatibility. Parent deviations are
 * separate: asset classes use the target investment budget, and securities use their target class amount. The portfolio
 * strategy first triggers a class adjustment and then selects securities within their allocation bands.
 * </p>
 *
 * <p>
 * The plan is a calculation and carries no persistence of its own. The evaluation turns it into
 * {@code AlgoRecommendation} rows and notifications, the report turns it into columns, and a historical replay will
 * turn it into simulated orders; none of the three re-derives the numbers.
 * </p>
 */
@Schema(description = """
    Targets, actual exposures and selected trades for an AlgoTop. Target and actual percentages refer to net equity;
    parent deviations and class diagnostics describe the denominators used for rebalancing.""")
public record RebalancingPlan(Integer idTenant, Integer idAlgoTop, Integer idAlgoStrategy, String algoTopName,
    @Schema(description = "Closing day the holdings and prices were read at") @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT) LocalDate valuationDate,
    @Schema(description = "Tenant currency of every monetary amount below") String currency,
    @Schema(description = "Net equity: signed position values, cash and liabilities") double netEquity,
    @Schema(description = "Actual cash across all cash accounts, shown separately from equity") double actualCash,
    @Schema(description = "Sum of absolute position exposures, before offsetting long against short") double grossExposure,
    @Schema(description = "Maximum investment budget, that is net equity times the AlgoTop ceiling") double investmentBudget,
    @Schema(description = "Budget of tactical buckets that no rebalancing may spend") double unusedTacticalBudget,
    @Schema(description = "The AlgoTop ceiling in percentage points of net equity") double topPercentage,
    @Schema(description = "Class drift tolerance in percentage points of the target investment budget") double tolerancePercentage,
    @Schema(description = """
        Gross exposure is above the permitted ceiling, or net equity is not positive. Exposure increasing lines are
        blocked while this holds; reductions stay available.""") boolean exposureBreach,
    @Schema(description = "The configured number of redeployments per year has elapsed") boolean periodicDue,
    @Schema(description = "At least one allocation drifted beyond the tolerance") boolean driftDue, List<Line> lines,
    List<ClassAdjustment> classAdjustments) {

  /** Compatibility constructor for callers building a plan without class-selection diagnostics. */
  public RebalancingPlan(Integer idTenant, Integer idAlgoTop, Integer idAlgoStrategy, String algoTopName,
      LocalDate valuationDate, String currency, double netEquity, double actualCash, double grossExposure,
      double investmentBudget, double unusedTacticalBudget, double topPercentage, double tolerancePercentage,
      boolean exposureBreach, boolean periodicDue, boolean driftDue, List<Line> lines) {
    this(idTenant, idAlgoTop, idAlgoStrategy, algoTopName, valuationDate, currency, netEquity, actualCash,
        grossExposure, investmentBudget, unusedTacticalBudget, topPercentage, tolerancePercentage, exposureBreach,
        periodicDue, driftDue, lines, List.of());
  }

  /** Signed adjustments increase exposure when positive. Parent deviation is measured against the investment budget. */
  public record ClassAdjustment(Integer idNode, double parentDeviation, double securityDeviationPercentage,
      int maxTradedSecuritiesPerAssetclass, double requestedAdjustment, double plannedAdjustment, double residual,
      String limitingReason) {
  }

  /**
   * Whether this plan asks for anything. The two configured values answer two different questions and are therefore a
   * conjunction rather than two competing triggers: the interval decides whether the allocation is compared at all, and
   * the tolerance decides which of its lines are then traded. A drift on a day between two checkpoints is recorded in
   * {@link #driftDue()} and reported, but it is not an action.
   *
   * <p>
   * Only selected security lines count as executable. A class with a residual but no eligible capacity still carries
   * diagnostics; replay records that residual and advances the checkpoint without attempting an order.
   * </p>
   *
   * @return {@link AlgoRebalancingTrigger#PERIODIC} when the plan is to be executed, otherwise
   *         {@link AlgoRebalancingTrigger#NONE}
   */
  public AlgoRebalancingTrigger trigger() {
    return periodicDue && !executableLines().isEmpty() ? AlgoRebalancingTrigger.PERIODIC : AlgoRebalancingTrigger.NONE;
  }

  /**
   * The lines an execution can actually turn into an order: selected instruments not reserved for an entry strategy,
   * with a usable unit value. This is the single definition of that set, so what makes a plan actionable and what a
   * replay books cannot disagree.
   *
   * @return those lines in hierarchy order, empty when the plan asks for nothing executable
   */
  public List<Line> executableLines() {
    return lines.stream().filter(line -> StrategyHelper.SECURITY_LEVEL_LETTER.equals(line.levelType())
        && line.actionable() && !line.tactical() && line.recommendedUnits() != null).toList();
  }

  /**
   * One node of the hierarchy.
   *
   * @param levelType          discriminator letter of the hierarchy: T, A or S
   * @param idNode             the AlgoTop, AlgoAssetclass or AlgoSecurity this line describes
   * @param idParentNode       the bucket a security line hangs below, null on the other two levels
   * @param idSecuritycurrency the instrument of a security line, null on the other two levels
   * @param label              what the report names the node, already in the user's language
   * @param tactical           the node is reached only by an entry strategy, so its target is a ceiling rather than
   *                           something a rebalancing may fill
   * @param targetPercentage   target share of net equity in percentage points
   * @param actualPercentage   actual share of net equity in percentage points, measured as gross exposure
   * @param deviation          actual minus target, in percentage points
   * @param targetAmount       target exposure in tenant currency
   * @param actualAmount       actual gross exposure in tenant currency
   * @param action             direction of the proposed trade, not of the exposure
   * @param recommendedAmount  size of the proposed trade in tenant currency, null when nothing is proposed
   * @param recommendedUnits   size of the proposed trade in units, null when the instrument cannot be priced
   * @param rationale          why the line reads as it does; also the text of the notification
   * @param parentDeviation    deviation in percentage points of the parent target budget, when applicable
   * @param exposureChange     signed selected exposure adjustment; independent of the long/short trade direction
   */
  public record Line(String levelType, Integer idNode, Integer idParentNode, Integer idSecuritycurrency, String label,
      boolean tactical, Double targetPercentage, Double actualPercentage, Double deviation, Double targetAmount,
      Double actualAmount, AlgoRecommendationAction action, Double recommendedAmount, Double recommendedUnits,
      String rationale, Double parentDeviation, Double exposureChange) {

    public Line(String levelType, Integer idNode, Integer idParentNode, Integer idSecuritycurrency, String label,
        boolean tactical, Double targetPercentage, Double actualPercentage, Double deviation, Double targetAmount,
        Double actualAmount, AlgoRecommendationAction action, Double recommendedAmount, Double recommendedUnits,
        String rationale) {
      this(levelType, idNode, idParentNode, idSecuritycurrency, label, tactical, targetPercentage, actualPercentage,
          deviation, targetAmount, actualAmount, action, recommendedAmount, recommendedUnits, rationale, null,
          recommendedAmount == null ? null : Math.copySign(recommendedAmount, -deviation));
    }

    /** Selected direction, which can differ from the direction of the exact-target gap when using band capacity. */
    public boolean reducesExposure() {
      return exposureChange != null && exposureChange < 0;
    }

    /** A line the user can act on, which is what a notification is worth raising for. */
    public boolean actionable() {
      return action == AlgoRecommendationAction.REBALANCE_BUY || action == AlgoRecommendationAction.REBALANCE_SELL;
    }
  }

}
