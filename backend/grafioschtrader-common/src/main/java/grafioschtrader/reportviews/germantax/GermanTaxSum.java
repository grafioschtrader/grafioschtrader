package grafioschtrader.reportviews.germantax;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One sum of the German tax report. A sum is either complete or it has no amount at all: a gap in its derivation never
 * shows as 0.
 */
@Schema(description = "A sum of the German tax report, complete or without an amount")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GermanTaxSum {

  @Schema(description = "Block of the report, a BLOCK_ name of GermanTaxKeys")
  public String block;

  @Schema(description = "Category within the blocks WITHHELD, UNREVIEWED and LEGACY, otherwise empty")
  public String category;

  @Schema(description = "Line of the sum, a LINE_ name of GermanTaxKeys")
  public String line;

  @Schema(description = "Fund class of the sum as name of FundTaxClass, only for funds")
  public String fundTaxClass;

  @Schema(description = "Amount in euro. Empty when a gap in the derivation affects the sum")
  public Double amountEur;

  @Schema(description = """
      The amount in euro after the partial exemption of the fund class. Only funds have it, and the form takes the
      amount before the partial exemption. Empty when a gap in the derivation affects the sum""")
  public Double amountAfterPartialExemptionEur;

  @Schema(description = "False when a gap in the derivation of the sum leaves it without an amount")
  public boolean complete = true;

  public GermanTaxSum() {
  }

  public GermanTaxSum(String block, String category, String line, String fundTaxClass) {
    this.block = block;
    this.category = category;
    this.line = line;
    this.fundTaxClass = fundTaxClass;
  }

  /** The identity of the sum within a report. */
  public String key() {
    return block + "/" + (category == null ? "" : category) + "/" + line + "/"
        + (fundTaxClass == null ? "" : fundTaxClass);
  }
}
