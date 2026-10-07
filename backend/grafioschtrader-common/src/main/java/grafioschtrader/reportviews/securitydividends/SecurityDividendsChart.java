package grafioschtrader.reportviews.securitydividends;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.fasterxml.jackson.annotation.JsonIgnore;

import grafiosch.common.DataHelper;
import grafioschtrader.entities.Assetclass;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Income and cost figures of the dividends report, aggregated for its charts. The values are built from the same
 * transactions and with the same exchange rates as {@link SecurityDividendsGrandTotal}, so the yearly totals of the
 * charts reconcile with the dividends table. Individual securities are deliberately not broken down; the finest
 * grouping is the asset class.
 */
@Schema(description = """
    Income and cost figures of the dividends report aggregated per year, per month and per asset class in the main
    currency, intended for charts. Interest means distributions of instruments in the asset class category
    FIXED_INCOME or CONVERTIBLE_BOND, dividends all other distributions.""")
public class SecurityDividendsChart {

  public static final int MONTHS = 12;

  @Schema(description = "ISO currency code of the tenant's main currency in which all amounts are expressed")
  public final String mainCurrency;

  @JsonIgnore
  private final int precisionMC;

  @JsonIgnore
  private final TreeMap<Integer, IncomeYear> incomeYearMap = new TreeMap<>();

  @JsonIgnore
  private final Map<Integer, Map<Integer, IncomeAssetclass>> incomeAssetclassMap = new TreeMap<>();

  @JsonIgnore
  private final Map<Integer, Assetclass> assetclassMap = new HashMap<>();

  public SecurityDividendsChart(String mainCurrency, int precisionMC) {
    this.mainCurrency = mainCurrency;
    this.precisionMC = precisionMC;
  }

  /**
   * Returns the year entry, creating it when it does not exist yet.
   *
   * @param year the calendar year
   * @return the income of that year
   */
  public IncomeYear getOrCreateIncomeYear(int year) {
    return incomeYearMap.computeIfAbsent(year, _ -> new IncomeYear(year, precisionMC));
  }

  /**
   * Adds a distribution to the asset class breakdown of the given year.
   *
   * @param year       the calendar year of the distribution
   * @param assetclass the asset class of the distributing instrument
   * @param netMC      the received amount in the main currency
   * @param taxMC      the withheld tax in the main currency
   */
  public void addAssetclassIncome(int year, Assetclass assetclass, double netMC, double taxMC) {
    assetclassMap.putIfAbsent(assetclass.getIdAssetClass(), assetclass);
    IncomeAssetclass incomeAssetclass = incomeAssetclassMap.computeIfAbsent(year, _ -> new HashMap<>()).computeIfAbsent(
        assetclass.getIdAssetClass(), _ -> new IncomeAssetclass(year, assetclass.getIdAssetClass(), precisionMC));
    incomeAssetclass.netMC += netMC;
    incomeAssetclass.taxMC += taxMC;
  }

  /**
   * Adds empty entries for the years between the first and the last year, so that a chart shows a continuous axis even
   * when a year had no income at all.
   */
  public void fillYearGaps() {
    if (!incomeYearMap.isEmpty()) {
      int firstYear = incomeYearMap.firstKey();
      int lastYear = incomeYearMap.lastKey();
      for (int year = firstYear; year < lastYear; year++) {
        getOrCreateIncomeYear(year);
      }
    }
  }

  @Schema(description = "Income and costs per year, ascending by year, without gaps between the first and last year")
  public List<IncomeYear> getIncomeYears() {
    return new ArrayList<>(incomeYearMap.values());
  }

  @Schema(description = "Distributions per year and asset class, ascending by year")
  public List<IncomeAssetclass> getIncomeAssetclasses() {
    return incomeAssetclassMap.values().stream().flatMap(m -> m.values().stream()).toList();
  }

  @Schema(description = "The asset classes referenced by incomeAssetclasses, used to build their display names")
  public List<Assetclass> getAssetclasses() {
    return assetclassMap.values().stream().sorted(Comparator.comparing(Assetclass::getIdAssetClass)).toList();
  }

  @Schema(description = "Income and costs of one calendar year in the main currency, as totals and per month")
  public static class IncomeYear {
    @Schema(description = "Calendar year")
    public final int year;

    @JsonIgnore
    private final int precisionMC;

    public double dividendNetMC;
    public double dividendTaxMC;
    public double interestNetMC;
    public double interestTaxMC;
    public double cashInterestMC;
    public double financeCostCfdMC;
    public double financeCostForexMC;
    public double feeMC;

    public final double[] dividendNetMonthMC = new double[MONTHS];
    public final double[] dividendTaxMonthMC = new double[MONTHS];
    public final double[] interestNetMonthMC = new double[MONTHS];
    public final double[] interestTaxMonthMC = new double[MONTHS];
    public final double[] cashInterestMonthMC = new double[MONTHS];

    public IncomeYear(int year, int precisionMC) {
      this.year = year;
      this.precisionMC = precisionMC;
    }

    /**
     * Adds a distribution of a security.
     *
     * @param monthIndex zero based month of the booking date
     * @param interest   true for a bond or convertible bond, false for a dividend
     * @param netMC      the received amount in the main currency
     * @param taxMC      the withheld tax in the main currency
     */
    public void addDistribution(int monthIndex, boolean interest, double netMC, double taxMC) {
      if (interest) {
        interestNetMC += netMC;
        interestTaxMC += taxMC;
        interestNetMonthMC[monthIndex] += netMC;
        interestTaxMonthMC[monthIndex] += taxMC;
      } else {
        dividendNetMC += netMC;
        dividendTaxMC += taxMC;
        dividendNetMonthMC[monthIndex] += netMC;
        dividendTaxMonthMC[monthIndex] += taxMC;
      }
    }

    /**
     * Adds interest credited or, when negative, debited on a cash account.
     *
     * @param monthIndex zero based month of the booking date
     * @param amountMC   the interest in the main currency
     */
    public void addCashInterest(int monthIndex, double amountMC) {
      cashInterestMC += amountMC;
      cashInterestMonthMC[monthIndex] += amountMC;
    }

    @Schema(description = "Received dividends of all instruments other than bonds, net of withholding tax")
    public double getDividendNetMC() {
      return round(dividendNetMC);
    }

    @Schema(description = "Withholding tax deducted from dividends")
    public double getDividendTaxMC() {
      return round(dividendTaxMC);
    }

    @Schema(description = "Received interest of bonds and convertible bonds, also through funds, net of withholding tax")
    public double getInterestNetMC() {
      return round(interestNetMC);
    }

    @Schema(description = "Withholding tax deducted from bond interest")
    public double getInterestTaxMC() {
      return round(interestTaxMC);
    }

    @Schema(description = "Interest on cash accounts; negative when negative interest was charged")
    public double getCashInterestMC() {
      return round(cashInterestMC);
    }

    @Schema(description = "Finance costs of CFD and other margin positions except Forex, usually negative")
    public double getFinanceCostCfdMC() {
      return round(financeCostCfdMC);
    }

    @Schema(description = "Finance costs of Forex positions, usually negative")
    public double getFinanceCostForexMC() {
      return round(financeCostForexMC);
    }

    @Schema(description = "Fees charged on cash and security accounts as a positive amount")
    public double getFeeMC() {
      return round(feeMC);
    }

    @Schema(description = """
        Net income: dividends, bond interest and cash account interest net of withholding tax, plus the (negative)
        finance costs of CFD and Forex. Fees are not included.""")
    public double getNetIncomeMC() {
      return round(dividendNetMC + interestNetMC + cashInterestMC + financeCostCfdMC + financeCostForexMC);
    }

    @Schema(description = """
        Dividends, bond interest and cash account interest net of withholding tax per month, index 0 is January""")
    public double[] getNetIncomeMonthMC() {
      double[] values = new double[MONTHS];
      for (int i = 0; i < MONTHS; i++) {
        values[i] = round(dividendNetMonthMC[i] + interestNetMonthMC[i] + cashInterestMonthMC[i]);
      }
      return values;
    }

    @Schema(description = "Withholding tax on dividends and bond interest per month, index 0 is January")
    public double[] getTaxMonthMC() {
      double[] values = new double[MONTHS];
      for (int i = 0; i < MONTHS; i++) {
        values[i] = round(dividendTaxMonthMC[i] + interestTaxMonthMC[i]);
      }
      return values;
    }

    @Schema(description = "Net dividends per month, index 0 is January")
    public double[] getDividendNetMonthMC() {
      return round(dividendNetMonthMC);
    }

    @Schema(description = "Withholding tax on dividends per month, index 0 is January")
    public double[] getDividendTaxMonthMC() {
      return round(dividendTaxMonthMC);
    }

    @Schema(description = "Net bond interest per month, index 0 is January")
    public double[] getInterestNetMonthMC() {
      return round(interestNetMonthMC);
    }

    @Schema(description = "Withholding tax on bond interest per month, index 0 is January")
    public double[] getInterestTaxMonthMC() {
      return round(interestTaxMonthMC);
    }

    @Schema(description = "Cash account interest per month, index 0 is January")
    public double[] getCashInterestMonthMC() {
      return round(cashInterestMonthMC);
    }

    private double round(double value) {
      return DataHelper.round(value, precisionMC);
    }

    private double[] round(double[] values) {
      return Arrays.stream(values).map(this::round).toArray();
    }
  }

  @Schema(description = "Distributions of one asset class in one calendar year in the main currency")
  public static class IncomeAssetclass {
    @Schema(description = "Calendar year")
    public final int year;

    @Schema(description = "Identifier of the asset class, see assetclasses for its properties")
    public final int idAssetClass;

    @JsonIgnore
    private final int precisionMC;

    public double netMC;

    public double taxMC;

    public IncomeAssetclass(int year, int idAssetClass, int precisionMC) {
      this.year = year;
      this.idAssetClass = idAssetClass;
      this.precisionMC = precisionMC;
    }

    @Schema(description = "Received distributions net of withholding tax")
    public double getNetMC() {
      return DataHelper.round(netMC, precisionMC);
    }

    @Schema(description = "Withholding tax deducted from the distributions")
    public double getTaxMC() {
      return DataHelper.round(taxMC, precisionMC);
    }
  }
}
