package grafioschtrader.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A time-bounded fee period containing its own set of fee rules. Allows brokers' changing fee schedules
 * to be modelled within a single YAML document. Periods are evaluated top-to-bottom; the first period
 * whose date range covers the transaction date is used.
 */
@Schema(description = """
    A fee period with a date range and nested rules. Used inside the 'periods' array of a fee model
    configuration to support time-varying fee schedules.""")
public class FeeModelPeriod {

  @Schema(description = "Start date (inclusive) in YYYY-MM-DD format. Required.")
  private String validFrom;

  @Schema(description = "End date (inclusive) in YYYY-MM-DD format. Omit for open-ended (until further notice).")
  private String validTo;

  @Schema(description = "Fee rules for this period, evaluated top-to-bottom; first matching condition wins.")
  private List<FeeRule> rules;

  public FeeModelPeriod() {
  }

  public FeeModelPeriod(String validFrom, String validTo, List<FeeRule> rules) {
    this.validFrom = validFrom;
    this.validTo = validTo;
    this.rules = rules;
  }

  public String getValidFrom() {
    return validFrom;
  }

  public void setValidFrom(String validFrom) {
    this.validFrom = validFrom;
  }

  public String getValidTo() {
    return validTo;
  }

  public void setValidTo(String validTo) {
    this.validTo = validTo;
  }

  public List<FeeRule> getRules() {
    return rules;
  }

  public void setRules(List<FeeRule> rules) {
    this.rules = rules;
  }
}
