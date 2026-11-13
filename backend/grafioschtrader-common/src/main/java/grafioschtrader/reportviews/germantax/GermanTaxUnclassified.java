package grafioschtrader.reportviews.germantax;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import grafiosch.BaseConstants;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A sale or an income of an instrument the German tax help does not calculate. The amount is the value of the
 * booking in euro and no gain, so a gap stays visible without entering any sum.
 */
@Schema(description = "A booking of an instrument that is in no sum, with its amount in euro and the reason")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GermanTaxUnclassified {

  @Schema(description = "SALE_PROCEEDS, INCOME or MARGIN_CLOSE, the kind of the amount")
  public String amountKind;

  @Schema(description = "A REASON_ name of GermanTaxKeys")
  public String reason;

  public Integer idTransaction;
  public Integer idSecuritycurrency;
  public String securityName;

  @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
  public LocalDate date;

  @Schema(description = "Value of the booking in euro, empty when the exchange rate is missing")
  public Double amountEur;

  public GermanTaxUnclassified() {
  }

  public GermanTaxUnclassified(String amountKind, String reason, Integer idTransaction, Integer idSecuritycurrency,
      String securityName, LocalDate date, Double amountEur) {
    this.amountKind = amountKind;
    this.reason = reason;
    this.idTransaction = idTransaction;
    this.idSecuritycurrency = idSecuritycurrency;
    this.securityName = securityName;
    this.date = date;
    this.amountEur = amountEur;
  }
}
