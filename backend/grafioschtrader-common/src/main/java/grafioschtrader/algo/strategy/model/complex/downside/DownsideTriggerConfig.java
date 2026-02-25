package grafioschtrader.algo.strategy.model.complex.downside;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import grafioschtrader.algo.strategy.model.complex.enums.DecisionBasis;
import grafioschtrader.algo.strategy.model.complex.enums.ReferencePrice;
import jakarta.validation.Valid;

/**
 * Trigger conditions for activating downside management: threshold, decision method, and optional indicator/statistical
 * rules for hybrid decisions.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DownsideTriggerConfig {

  public ReferencePrice down_reference;

  public Double down_threshold_pct;

  public DecisionBasis decision_basis;

  @Valid
  public List<@Valid IndicatorRuleConfig> indicator_rules;

  @Valid
  public List<@Valid StatisticalRuleConfig> statistical_rules;
}
