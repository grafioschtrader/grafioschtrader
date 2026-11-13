package grafioschtrader.reportviews.germantax;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The starting values of a tax year for the German tax return: Anlage KAP and Anlage KAP-INV in euro. It is no tax
 * calculation, it knows neither the saver's allowance, the tax rate nor loss carry-forwards.
 */
@Schema(description = "Starting values of a tax year for the German tax return, no tax calculation")
public class GermanTaxYearReport {

  /** A portfolio of the tenant and where its income appears in the report. */
  @Schema(description = "A portfolio and the withholding assigned to it")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class PortfolioAssignment {
    public Integer idPortfolio;
    public String name;

    @Schema(description = "Name of TaxWithholding, empty when the portfolio is not assigned yet")
    public String taxWithholding;

    @Schema(description = "True when the portfolio has security or cash accounts")
    public boolean hasAccounts;

    public PortfolioAssignment() {
    }

    public PortfolioAssignment(Integer idPortfolio, String name, String taxWithholding, boolean hasAccounts) {
      this.idPortfolio = idPortfolio;
      this.name = name;
      this.taxWithholding = taxWithholding;
      this.hasAccounts = hasAccounts;
    }
  }

  @Schema(description = "The tax year of the sales and the income")
  public int taxYear;

  @Schema(description = "True for the current year, whose values change with every booking")
  public boolean provisional;

  @Schema(description = "Currency of every amount, always EUR")
  public String mainCurrency = "EUR";

  @Schema(description = "True when a portfolio with accounts is not assigned to a withholding yet")
  public boolean portfolioAssignmentRequired;

  @Schema(description = "The portfolios and their assignment")
  public List<PortfolioAssignment> portfolios = new ArrayList<>();

  @Schema(description = "The sums of all blocks, a sum without amount for every gap")
  public List<GermanTaxSum> sums = new ArrayList<>();

  @Schema(description = "The gaps of the derivation with the sums they leave without an amount")
  public List<GermanTaxWarning> warnings = new ArrayList<>();

  @Schema(description = "Sales and income of instruments that are in no sum")
  public List<GermanTaxUnclassified> unclassified = new ArrayList<>();

  @Schema(description = "The derivation of the sums")
  public List<GermanTaxDetail> details = new ArrayList<>();
}
