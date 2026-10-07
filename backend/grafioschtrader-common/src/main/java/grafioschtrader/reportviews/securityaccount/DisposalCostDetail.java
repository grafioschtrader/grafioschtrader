package grafioschtrader.reportviews.securityaccount;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One line of the explanation of a disposal cost estimate: which rule priced a part of the hypothetical sale in one
 * security account, or why that part could not be priced. The frontend shows these lines as a tooltip, translating
 * {@link #reason} as an NLS key.
 */
@Schema(description = """
    One part of a disposal cost estimate for one security account: the matched rule, or the reason why the part
    could not be estimated""")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DisposalCostDetail(
    @Schema(description = "Name of the security account the units are sold from") String securityaccountName,
    @Schema(description = "The cost component this line explains") Part part,
    @Schema(description = "Name of the matched rule, null when the part could not be estimated") String rule,
    @Schema(description = "NLS key of the reason why the part is unknown, null when a rule matched") String reason,
    @Schema(description = "Additional untranslated detail, such as the country of a tax model or an error text") String detail,
    @Schema(description = "Estimated amount in the currency of the security, null when unknown") Double amount) {

  /** The three components of the cost of a hypothetical sale. */
  public enum Part {
    /** Commission of the fee model */
    COMMISSION,
    /** Transaction tax of the simulation tax model, such as a stamp duty */
    TAX,
    /** Currency conversion markup of the fx section of the fee model */
    FX
  }

  public static DisposalCostDetail matched(String securityaccountName, Part part, String rule, String detail,
      double amount) {
    return new DisposalCostDetail(securityaccountName, part, rule, null, detail, amount);
  }

  public static DisposalCostDetail unknown(String securityaccountName, Part part, String reason, String detail) {
    return new DisposalCostDetail(securityaccountName, part, null, reason, detail, null);
  }
}
