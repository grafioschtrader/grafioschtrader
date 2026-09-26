package grafioschtrader.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = """
    Rule-based transaction fee model configuration parsed from YAML. Supports two mutually exclusive formats:
    (1) flat 'rules' array — always applicable regardless of date, or
    (2) 'periods' array — time-based fee schedules with nested rules per period.""")
public class FeeModelConfig {

  @Schema(description = "Independently resolved currency conversion markup tariff.")
  private FxFeeConfig fx;

  public FxFeeConfig getFx() {
    return fx;
  }

  public void setFx(FxFeeConfig fx) {
    this.fx = fx;
  }

  private CustodyFeeConfig custody;

  public CustodyFeeConfig getCustody() {
    return custody;
  }

  public void setCustody(CustodyFeeConfig custody) {
    this.custody = custody;
  }

  @Schema(description = "Fee rules evaluated top-to-bottom; first matching condition wins. Mutually exclusive with 'periods'.")
  private List<FeeRule> rules;

  @Schema(description = """
      Time-based fee periods, each with its own rules array. The first period matching the transaction date is used.
      Mutually exclusive with 'rules'.""")
  private List<FeeModelPeriod> periods;

  public FeeModelConfig() {
  }

  public FeeModelConfig(List<FeeRule> rules) {
    this.rules = rules;
  }

  public List<FeeRule> getRules() {
    return rules;
  }

  public void setRules(List<FeeRule> rules) {
    this.rules = rules;
  }

  public List<FeeModelPeriod> getPeriods() {
    return periods;
  }

  public void setPeriods(List<FeeModelPeriod> periods) {
    this.periods = periods;
  }
}
