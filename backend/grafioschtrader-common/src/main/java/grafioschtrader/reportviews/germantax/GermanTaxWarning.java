package grafioschtrader.reportviews.germantax;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A gap in the derivation of the German tax report: its cause, the year, the transaction or security concerned and the
 * sums that stay without an amount because of it.
 */
@Schema(description = "A gap in the derivation of the report and the sums it leaves without an amount")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GermanTaxWarning {

  @Schema(description = "Cause, a WARN_ name of GermanTaxKeys")
  public String code;

  @Schema(description = "Year the gap concerns, the year of the transaction or of the advance lump sum")
  public Integer year;

  @Schema(description = "Transaction concerned")
  public Integer idTransaction;

  @Schema(description = "Security concerned")
  public Integer idSecuritycurrency;

  @Schema(description = "Name of the security concerned")
  public String securityName;

  @Schema(description = "Further detail of the cause, for example the currency of a missing exchange rate")
  public String detail;

  @Schema(description = "The sums that stay without an amount, as block/category/line/fund class")
  public List<String> affectedSums = new ArrayList<>();

  public GermanTaxWarning() {
  }

  public GermanTaxWarning(String code, Integer year, Integer idTransaction, Integer idSecuritycurrency,
      String securityName, String detail) {
    this.code = code;
    this.year = year;
    this.idTransaction = idTransaction;
    this.idSecuritycurrency = idSecuritycurrency;
    this.securityName = securityName;
    this.detail = detail;
  }
}
