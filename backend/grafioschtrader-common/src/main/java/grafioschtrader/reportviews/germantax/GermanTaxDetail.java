package grafioschtrader.reportviews.germantax;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import grafiosch.BaseConstants;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One row of the derivation of the German tax report: a sale, an income or an advance lump sum with its amounts in
 * euro, the exchange rate it was converted with and the purchases a sale consumed. It is the derivation and no second
 * addend of a sum.
 */
@Schema(description = "A row of the derivation of the German tax report")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GermanTaxDetail {

  /** The part of a purchase a sale consumed, or that an advance lump sum was calculated for. */
  @Schema(description = "A purchase or a part of it")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class LotPart {
    @Schema(description = "The purchase transaction")
    public Integer idTransaction;

    @Schema(description = "Time of the purchase")
    @JsonFormat(pattern = BaseConstants.STANDARD_LOCAL_DATE_TIME_ISO)
    public LocalDateTime acquisitionTime;

    @Schema(description = "Units on the split basis of today")
    public double units;

    @Schema(description = "Cost of these units in euro")
    public double costEur;

    @Schema(description = "Advance lump sums already applied to these units in euro")
    public double preliminaryEur;

    public LotPart() {
    }

    public LotPart(Integer idTransaction, LocalDateTime acquisitionTime, double units, double costEur,
        double preliminaryEur) {
      this.idTransaction = idTransaction;
      this.acquisitionTime = acquisitionTime;
      this.units = units;
      this.costEur = costEur;
      this.preliminaryEur = preliminaryEur;
    }
  }

  @Schema(description = "Kind of the row, a KIND_ name of GermanTaxKeys")
  public String kind;

  @Schema(description = "Day of the sale or inflow of the income, of an advance lump sum the day it counts as received")
  @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
  public LocalDate date;

  public Integer idTransaction;
  public Integer idSecuritycurrency;
  public String securityName;
  public String isin;
  public Integer idSecurityaccount;
  public Integer idPortfolio;

  @Schema(description = "Block the row belongs to")
  public String block;

  @Schema(description = "Category within the blocks WITHHELD, UNREVIEWED and LEGACY")
  public String category;

  @Schema(description = "Fund class as name of FundTaxClass, only for funds")
  public String fundTaxClass;

  @Schema(description = "Currency of the security or the cash account")
  public String currency;

  public Double units;

  @Schema(description = "Gross income in euro")
  public Double grossEur;

  @Schema(description = "Proceeds of a sale after its costs in euro")
  public Double proceedsEur;

  @Schema(description = "Cost of the units sold in euro")
  public Double costEur;

  @Schema(description = "Gain or loss of a sale in euro. For a fund after the deduction of the advance lump sums")
  public Double gainEur;

  @Schema(description = "Advance lump sums of the units sold, which the gain of a fund is reduced by, in euro")
  public Double preliminaryDeductedEur;

  @Schema(description = "Foreign tax recorded with an income in euro")
  public Double foreignTaxEur;

  @Schema(description = "Calculated ceiling of the creditable part of the foreign tax in euro")
  public Double taxCeilingEur;

  @Schema(description = "Exchange rate to euro of the row")
  public Double rate;

  @Schema(description = "Source of the exchange rate: EUR, BOOKED for the rate of the transaction or CLOSE")
  public String rateSource;

  @Schema(description = "False when a gap leaves the row without its amounts")
  public boolean complete = true;

  @Schema(description = "The purchases a sale consumed")
  public List<LotPart> lots = new ArrayList<>();
}
