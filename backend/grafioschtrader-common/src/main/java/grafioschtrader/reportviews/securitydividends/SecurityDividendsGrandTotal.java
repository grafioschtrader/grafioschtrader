package grafioschtrader.reportviews.securitydividends;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import grafiosch.common.DataHelper;
import grafioschtrader.GlobalConstants;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Portfolio;
import grafioschtrader.reportviews.DateTransactionCurrencypairMap;
import grafioschtrader.reportviews.SecurityCostGrand;
import grafioschtrader.reportviews.SecurityCostGroup;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Grand total aggregator for the comprehensive dividends and interest report. This class serves as the top-level
 * container that consolidates dividend and interest data across all years, portfolios, and accounts within the system.
 * 
 * <p>
 * The class extends {@link SecurityCostGrand} to provide yearly grouping functionality through
 * {@link SecurityDividendsYearGroup} instances. It maintains grand totals for various financial metrics including fees,
 * interest, dividends, and taxable amounts, all converted to the main currency.
 * </p>
 * 
 * <p>
 * Key responsibilities include:
 * </p>
 * <ul>
 * <li>Aggregating financial data across multiple years</li>
 * <li>Managing portfolio and account information</li>
 * <li>Calculating grand totals with appropriate currency precision</li>
 * <li>Integrating historical quotes for position valuations</li>
 * </ul>
 */
public class SecurityDividendsGrandTotal extends SecurityCostGrand<Integer, SecurityDividendsYearGroup> {

  @Schema(description = "Grand total of all interest income across all years in the main currency.")
  public double grandInterestMC;

  @Schema(description = "Grand total of all fees charged across all years in the main currency.")
  public double grandFeeMC;

  @Schema(description = "Grand total of all real received dividends and interest (net of taxes) across all years in the main currency.")
  public double grandRealReceivedDivInterestMC;

  @Schema(description = "Grand total of all taxable dividend and interest amounts across all years in the main currency.")
  public double grandTaxableAmountMC;

  @Schema(description = "List of all portfolios included in this dividends report.")
  public List<Portfolio> portfolioList;

  /**
   * Constructs a new SecurityDividendsGrandTotal with the specified currency settings. Initializes the underlying
   * TreeMap for managing year-based groupings.
   * 
   * @param mainCurrency         the main currency code for all calculations and conversions
   * @param currencyPrecisionMap map containing precision settings for different currencies, where key is currency code
   *                             and value is number of decimal places
   */
  public SecurityDividendsGrandTotal(String mainCurrency, Map<String, Integer> currencyPrecisionMap) {
    super(mainCurrency, new TreeMap<>(), currencyPrecisionMap);
  }

  /**
   * Creates a new SecurityDividendsYearGroup instance for the specified year. This method is called by the parent class
   * when a new year group needs to be created.
   * 
   * @param key the year (as Integer) for which to create the group
   * @return a new SecurityDividendsYearGroup instance configured with appropriate precision settings
   */
  @Override
  protected SecurityDividendsYearGroup createInstance(Integer key) {
    return new SecurityDividendsYearGroup(key,
        currencyPrecisionMap.getOrDefault(mainCurrency, GlobalConstants.FID_STANDARD_FRACTION_DIGITS),
        currencyPrecisionMap);
  }

  /**
   * Calculates dividend and interest totals across all year groups. This method iterates through all
   * SecurityDividendsYearGroup instances, triggers their individual calculations, and then aggregates the results into
   * the grand totals maintained by this class.
   */
  public void calcDivInterest() {
    groupMap.values().forEach(securityDividendsYearGroup -> {
      securityDividendsYearGroup.calcDivInterest();
      this.grandFeeMC += securityDividendsYearGroup.yearFeeMC;
      this.grandInterestMC += securityDividendsYearGroup.yearInterestMC;
      this.grandRealReceivedDivInterestMC += securityDividendsYearGroup.yearRealReceivedDivInterestMC;
      this.grandTaxableAmountMC += securityDividendsYearGroup.yearTaxableAmountMC;
    });

  }

  /**
   * Attaches historical quotes to all year groups and calculates position totals. This method propagates historical
   * quote data and currency information to all SecurityDividendsYearGroup instances, enabling them to calculate
   * position valuations at specific dates (typically end-of-year).
   * 
   * <p>
   * After all year groups have processed their historical data, this method calls the grand summary calculation to
   * finalize all totals.
   * </p>
   * 
   * @param historyquoteYearIdMap nested map structure containing historical quotes, where outer key is year, inner key
   *                              is security ID, and value is the quote
   * @param dateCurrencyMap       currency pair mapping and date information for conversions
   */
  public void attachHistoryquoteAndCalcPositionTotal(Map<Integer, Map<Integer, Historyquote>> historyquoteYearIdMap,
      DateTransactionCurrencypairMap dateCurrencyMap) {
    groupMap.values().forEach(securityDividendsYearGroup -> securityDividendsYearGroup
        .attachHistoryquoteAndCalcPositionTotal(historyquoteYearIdMap, dateCurrencyMap));
    this.caclulateGrandSummary();
  }

  public Collection<SecurityDividendsYearGroup> getSecurityDividendsYearGroup() {
    return groupMap.values();
  }

  @Override
  public SecurityCostGroup getSecurityCostGroup(SecurityDividendsYearGroup groupSummary) {
    return groupSummary.securityCostGroup;
  }

  /**
   * Calculates the total number of security accounts across all portfolios. This method iterates through all portfolios
   * in the portfolioList and sums up the number of security accounts in each portfolio.
   * 
   * @return the total count of security accounts across all portfolios
   */
  public Integer getNumberOfSecurityAccounts() {
    return portfolioList.stream().map(portfolio -> portfolio.getSecurityaccountList()).mapToInt(List::size).sum();
  }

  /**
   * Calculates the total number of cash accounts across all portfolios. This method iterates through all portfolios in
   * the portfolioList and sums up the number of cash accounts in each portfolio.
   * 
   * @return the total count of cash accounts across all portfolios
   */
  public Integer getNumberOfCashAccounts() {
    return portfolioList.stream().map(portfolio -> portfolio.getCashaccountList()).mapToInt(List::size).sum();
  }

  public double getGrandFeeMC() {
    return DataHelper.round(grandFeeMC, precisionMC);
  }

  public double getGrandInterestMC() {
    return DataHelper.round(grandInterestMC, precisionMC);
  }

  public double getGrandRealReceivedDivInterestMC() {
    return DataHelper.round(grandRealReceivedDivInterestMC, precisionMC);
  }

  public double getGrandTaxableAmountMC() {
    return DataHelper.round(grandTaxableAmountMC, precisionMC);
  }

}
