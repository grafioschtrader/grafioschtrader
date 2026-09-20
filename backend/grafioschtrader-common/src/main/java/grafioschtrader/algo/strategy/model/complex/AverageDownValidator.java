package grafioschtrader.algo.strategy.model.complex;

import static grafioschtrader.algo.strategy.model.complex.ConfigChecks.*;

import grafioschtrader.algo.strategy.model.complex.downside.DownsideManagementConfig;
import grafioschtrader.algo.strategy.model.complex.enums.*;

/** Executable averaging contract, shared by repository validation and the daily decision engine. */
public final class AverageDownValidator {
  private AverageDownValidator() {
  }

  /** Validates the selected averaging variant without enabling an independent stop-loss variant. */
  public static void validate(DownsideManagementConfig d) {
    var a = d.variant_B_average_down;
    require(a != null && Boolean.TRUE.equals(a.enabled), "An enabled averaging variant is required");
    require(d.variant_A_sell_loss == null || !Boolean.TRUE.equals(d.variant_A_sell_loss.enabled),
        "Disable the sell-loss variant when averaging is selected");
    MeanReversionConfigValidator.sizing(a.add_sizing);
    require(a.max_adds != null && a.max_adds > 0, "max_adds must be positive");
    require(Boolean.TRUE.equals(a.recalculate_avg_cost), "recalculate_avg_cost must be true");
    var step = a.add_step_rule;
    require(step != null && step.type != null && step.type != AddStepType.custom,
        "Use each_n_pct_drop or indicator_based addition steps");
    if (step.type == AddStepType.each_n_pct_drop) {
      require(step.reference != null, "An addition step reference is required");
      fraction(step.drop_pct_step, "drop_pct_step");
    } else {
      require(d.trigger != null
          && (d.trigger.decision_basis == DecisionBasis.indicator || d.trigger.decision_basis == DecisionBasis.hybrid),
          "Indicator steps require indicator confirmation");
    }
  }
}
