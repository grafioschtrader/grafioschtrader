package grafioschtrader.reportviews.realizedgain;

import java.util.ArrayList;
import java.util.List;

import grafiosch.common.DataHelper;
import grafioschtrader.entities.Portfolio;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Top of the realized gain report: the years with their sales and the sums over all years, all in the main currency.
 */
@Schema(description = "Realized gains per year with the totals over all years")
public class RealizedGainGrandTotal {

  /** Value of {@link #appliedMethod} when the security accounts of the report calculate by different methods. */
  public static final String MIXED_METHODS = "MIXED";

  @Schema(description = "Main currency of the tenant, the currency of every sum")
  public String mainCurrency;

  @Schema(description = """
      The cost basis method of all sales as name of CostBasisMethod, or MIXED when the security accounts calculate by
      different methods. The method of a single sale is on its row.""")
  public String appliedMethod;

  @Schema(description = "Sum of the gains of the market sales over all years")
  public double grandGainMC;

  @Schema(description = "Sum of the losses of the market sales over all years, a positive amount")
  public double grandLossMC;

  @Schema(description = "Gains minus losses of the market sales over all years")
  public double grandNetMC;

  @Schema(description = "Gains minus losses of the system bookings over all years")
  public double grandSystemNetMC;

  @Schema(description = "True when a year of the report is incomplete")
  public boolean incomplete;

  @Schema(description = "The years with sales, the newest first")
  public List<RealizedGainYearGroup> years = new ArrayList<>();

  @Schema(description = """
      ISO 3166-1 alpha-2 country code of the tenant. The German tax help is offered when it is DE and the main currency
      is EUR.""")
  public String tenantCountry;

  @Schema(description = "The portfolios of the tenant with their accounts, for the selection of the security accounts")
  public List<Portfolio> portfolioList;

  public RealizedGainGrandTotal() {
  }

  public RealizedGainGrandTotal(String mainCurrency, String appliedMethod) {
    this.mainCurrency = mainCurrency;
    this.appliedMethod = appliedMethod;
  }

  /**
   * Adds a finished year and its sums to the totals.
   *
   * @param yearGroup   the year, already rounded
   * @param precisionMC decimal places of the main currency
   */
  public void add(RealizedGainYearGroup yearGroup, int precisionMC) {
    years.add(yearGroup);
    grandGainMC = DataHelper.round(grandGainMC + yearGroup.yearGainMC, precisionMC);
    grandLossMC = DataHelper.round(grandLossMC + yearGroup.yearLossMC, precisionMC);
    grandNetMC = DataHelper.round(grandGainMC - grandLossMC, precisionMC);
    grandSystemNetMC = DataHelper.round(grandSystemNetMC + yearGroup.systemNetMC, precisionMC);
    incomplete |= yearGroup.incomplete;
  }
}
