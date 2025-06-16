package grafioschtrader.reports;

import java.lang.reflect.InvocationTargetException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.entities.User;
import grafioschtrader.dto.AnnualisedPerformance;
import grafioschtrader.dto.AnnualisedPerformance.AnnualisedYears;
import grafioschtrader.dto.AnnualisedPerformance.LastYears;
import grafioschtrader.dto.StatisticsSummary;
import grafioschtrader.dto.StatisticsSummary.StatsProperty;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitycurrency;
import grafioschtrader.entities.Tenant;
import grafioschtrader.entities.projection.SecurityYearClose;
import grafioschtrader.reports.ReportHelper.ClosePricesCurrencyClose;
import grafioschtrader.reports.ReportHelper.CurrencyAvailableRequired;
import grafioschtrader.reports.ReportHelper.CurrencyRequired;
import grafioschtrader.repository.CurrencypairJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.repository.TenantJpaRepository;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.SamplingPeriodType;
import grafioschtrader.types.TimePeriodType;

/**
 * Comprehensive statistical analysis service for individual financial instruments, providing detailed performance
 * metrics and risk analysis in both the instrument's native currency and the tenant's main currency. Calculates
 * annualized returns, volatility measures, and distribution statistics across multiple time horizons.
 * 
 * <p>
 * This service performs sophisticated financial analysis including yield calculations, annualized performance over
 * various periods (1, 3, 5, 10 years), and comprehensive statistical measures such as standard deviation, minimum, and
 * maximum returns. All calculations consider dividend reinvestment and are performed with proper currency conversion
 * when the instrument's currency differs from the tenant's main currency.
 * </p>
 * 
 * <h3>Key Analysis Features:</h3>
 * <ul>
 * <li>Annualized performance calculations for standard investment horizons</li>
 * <li>Multi-period statistical analysis (daily, monthly, annual)</li>
 * <li>Risk metrics including volatility and return distribution</li>
 * <li>Automatic currency conversion with missing pair creation</li>
 * <li>Dividend-adjusted return calculations</li>
 * <li>Performance comparison in multiple currencies</li>
 * </ul>
 * 
 * <h3>Currency Handling:</h3>
 * <p>
 * The service intelligently handles multi-currency scenarios by automatically detecting when currency conversion is
 * needed, locating appropriate currency pairs, and creating missing pairs as necessary. This ensures accurate
 * performance analysis regardless of the instrument's trading currency.
 * </p>
 * 
 * <h3>Statistical Rigor:</h3>
 * <p>
 * Uses Apache Commons Math for robust statistical calculations, ensuring professional-grade accuracy in all
 * mathematical operations including standard deviation, annualized returns, and compound growth calculations.
 * </p>
 */
public class InstrumentStatisticsSummary {

  /** Standard investment horizon periods for annualized performance analysis. */
  private final static byte[] ANNUALISED_YEARS = { 1, 3, 5, 10 };

  private final SecurityJpaRepository securityJpaRepository;
  private final TenantJpaRepository tenantJpaRepository;
  private final CurrencypairJpaRepository currencypairJpaRepository;

  private String tenantCurrency = null;
  private Securitycurrency<?> securityCurrency;
  private boolean securityTenantSameCurrency;
  /**
   * List of currency pairs required for converting the security's native currency to the tenant's main currency. May
   * contain existing pairs from the database or newly created pairs if the required conversion didn't exist. Null when
   * no currency conversion is needed (securityTenantSameCurrency = true).
   */
  private List<Currencypair> currencypairs = null;
  private String currencyOfSecurity = null;

  public InstrumentStatisticsSummary(SecurityJpaRepository securityJpaRepository,
      TenantJpaRepository tenantJpaRepository, CurrencypairJpaRepository currencypairJpaRepository) {
    this.securityJpaRepository = securityJpaRepository;
    this.tenantJpaRepository = tenantJpaRepository;
    this.currencypairJpaRepository = currencypairJpaRepository;
  }

  /**
   * Prepares the security and currency pair configuration for statistical analysis. Determines whether the security
   * requires currency conversion and sets up the necessary currency pairs for accurate multi-currency calculations.
   * 
   * <p>
   * This method handles both direct securities and currency pairs, automatically detecting the analysis requirements
   * and configuring the appropriate currency conversion mechanisms. For securities in different currencies than the
   * tenant's main currency, it locates or creates the necessary currency pairs.
   * </p>
   * 
   * @param idSecuritycurrency the unique identifier of the security or currency pair to analyze
   */
  @Transactional
  public void prepareSecurityCurrencypairs(Integer idSecuritycurrency) {
    Optional<Currencypair> currencypairOpt = currencypairJpaRepository.findById(idSecuritycurrency);
    if (currencypairOpt.isEmpty()) {
      loadSecurityAndPerhapsCurrencyForSecurity(idSecuritycurrency);
    } else {
      securityCurrency = currencypairOpt.get();
      securityTenantSameCurrency = true;
    }
  }

  /**
   * Loads security information and determines currency conversion requirements. Analyzes the security's currency
   * against the tenant's main currency and sets up necessary currency pairs for conversion calculations.
   * 
   * <p>
   * This method handles scenarios including securities in foreign currencies and automatically creates missing currency
   * pairs when needed for comprehensive multi-currency analysis.
   * </p>
   * 
   * @param idSecuritycurrency the security identifier to load and analyze
   */
  private void loadSecurityAndPerhapsCurrencyForSecurity(Integer idSecuritycurrency) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    Tenant tenant = tenantJpaRepository.getReferenceById(user.getIdTenant());
    tenantCurrency = tenant.getCurrency();
    securityCurrency = securityJpaRepository
        .findByIdTenantPrivateIsNullOrIdTenantPrivateAndIdSecuritycurrency(idSecuritycurrency, user.getIdTenant());
    currencyOfSecurity = ((Security) securityCurrency).getCurrency();
    securityTenantSameCurrency = tenantCurrency.equals(currencyOfSecurity)
        || ((Security) securityCurrency).getAssetClass().getCategoryType() == AssetclassType.CURRENCY_PAIR;
    if (!securityTenantSameCurrency) {
      currencypairs = currencypairJpaRepository
          .findByFromCurrencyAndToCurrencyOrToCurrencyAndFromCurrency(currencyOfSecurity, tenantCurrency);
      if (currencypairs.isEmpty()) {
        // Required currency pair is missing
        currencypairs = new ArrayList<>();
        currencypairs.add(
            currencypairJpaRepository.createNonExistingCurrencypair(tenant.getCurrency(), currencyOfSecurity, false));
      }
    }
  }

  /**
   * Calculates comprehensive annualized performance metrics for the security across multiple investment horizons.
   * Provides both year-by-year performance and annualized compound returns for standard investment periods (1, 3, 5, 10
   * years).
   * 
   * <p>
   * The analysis includes dividend reinvestment calculations and provides results in both the security's native
   * currency and the tenant's main currency. The method handles currency conversion automatically and ensures accurate
   * performance measurement regardless of currency differences.
   * </p>
   * 
   * <p>
   * Performance calculations use compound annual growth rate (CAGR) methodology for annualized returns and include
   * dividend distributions for total return analysis. The results enable comprehensive performance evaluation and
   * comparison across different investment horizons.
   * </p>
   * 
   * @return comprehensive annualized performance data including year-by-year returns and multi-year compound annual
   *         growth rates in both currencies
   */
  public AnnualisedPerformance getAnnualisedSecurityPerformance() {
    List<SecurityYearClose> sycList = securityTenantSameCurrency
        ? securityJpaRepository.getSecurityYearCloseDivSum(securityCurrency.getIdSecuritycurrency())
        : securityJpaRepository.getSecurityYearDivSumCurrencyClose(securityCurrency.getIdSecuritycurrency(),
            currencypairs.getFirst().getIdSecuritycurrency());
    AnnualisedPerformance ap = calculateYearPerformance(sycList, divideOrMultiplyCurrency(tenantCurrency));
    calculateAnnualisedYears(ap);
    return ap;
  }

  /**
   * Calculates year-by-year performance including dividend distributions and currency effects. Processes historical
   * price and dividend data to compute annual returns in both the security's currency and the tenant's main currency.
   * 
   * <p>
   * This method performs sophisticated total return calculations that include dividend reinvestment and proper currency
   * conversion using historical exchange rates. The calculations ensure accurate performance measurement that reflects
   * the true investment experience including all sources of return.
   * </p>
   * 
   * @param sycList historical year-end closing prices and dividend data
   * @param divide  flag indicating currency conversion direction for calculation accuracy
   * @return annualized performance object with year-by-year returns in both currencies
   */
  private AnnualisedPerformance calculateYearPerformance(List<SecurityYearClose> sycList, boolean divide) {
    var ap = new AnnualisedPerformance(currencyOfSecurity, tenantCurrency, sycList.get(sycList.size() - 1).getDate(),
        sycList.get(0).getDate());
    for (int i = 0; i < sycList.size() - 1; i++) {
      SecurityYearClose syc = sycList.get(i);
      double fcur = securityTenantSameCurrency ? 1.0 : divide ? 1.0 / syc.getCurrencyClose() : syc.getCurrencyClose();
      SecurityYearClose sycBefore = sycList.get(i + 1);
      double fcurBefore = securityTenantSameCurrency ? 1.0
          : divide ? 1.0 / sycBefore.getCurrencyClose() : sycBefore.getCurrencyClose();
      double performance = ((syc.getSecurityClose() + syc.getYearDiv()) / sycBefore.getSecurityClose() - 1.0) * 100.0;
      double performanceMC = ((syc.getSecurityClose() + syc.getYearDiv()) * fcur
          / (sycBefore.getSecurityClose() * fcurBefore) - 1.0) * 100.0;
      ap.lastYears.add(new LastYears(syc.getDate().getYear(), performance, performanceMC));
    }
    return ap;
  }

  /**
   * Calculates compound annual growth rates for standard investment horizons. Computes annualized returns for 1, 3, 5,
   * and 10-year periods using compound growth methodology to provide meaningful long-term performance metrics.
   * 
   * <p>
   * This method implements sophisticated compound annual growth rate (CAGR) calculations that account for the
   * compounding effect of returns over multiple years. The analysis excludes the current year from multi-year
   * calculations to avoid incomplete period distortion in the results.
   * </p>
   * 
   * @param ap the annualized performance object to populate with multi-year CAGR data
   */
  private void calculateAnnualisedYears(AnnualisedPerformance ap) {
    int i = 0;
    int numberOfYears = 0;
    double performance = 1;
    double performanceMC = 1;
    for (LastYears lastYear : ap.lastYears) {
      if (lastYear.year == LocalDate.now().getYear()) {
        continue;
      }
      numberOfYears++;
      performance *= (1 + lastYear.performanceYear / 100);
      performanceMC *= (1 + lastYear.performanceYearMC / 100);
      if (i < ANNUALISED_YEARS.length && ANNUALISED_YEARS[i] == numberOfYears) {
        addAnnualisedYear(ap, numberOfYears, performance, performanceMC);
        i++;
      }
    }
    if (numberOfYears > ANNUALISED_YEARS[ANNUALISED_YEARS.length - 1]) {
      addAnnualisedYear(ap, numberOfYears, performance, performanceMC);
    }
  }

  /**
   * Adds a calculated annualized return for a specific time period to the performance analysis. Converts compound
   * returns to annualized percentage returns using mathematical precision.
   * 
   * @param ap            the performance object to update
   * @param numberOfYears the investment horizon period
   * @param performance   the compound performance in the security's currency
   * @param performanceMC the compound performance in the main currency
   */
  private void addAnnualisedYear(AnnualisedPerformance ap, int numberOfYears, double performance,
      double performanceMC) {
    ap.annualisedYears.add(new AnnualisedYears(numberOfYears, (Math.pow(performance, 1.0 / numberOfYears) - 1.0) * 100,
        (Math.pow(performanceMC, 1.0 / numberOfYears) - 1.0) * 100));
  }

  /**
   * Determines the currency conversion direction based on currency pair configuration. Analyzes the relationship
   * between the security's currency and the tenant's main currency to ensure proper conversion calculations.
   * 
   * @param mainCurrency the tenant's main currency for conversion reference
   * @return true if currency values should be divided, false if they should be multiplied
   */
  private boolean divideOrMultiplyCurrency(String mainCurrency) {
    boolean divide = false;
    if (!securityTenantSameCurrency) {
      if (currencypairs.getFirst().getFromCurrency().equals(mainCurrency)) {
        divide = true;
      }
    }
    return divide;
  }

  /**
   * Calculates comprehensive statistical measures including volatility, return distribution, and risk metrics across
   * multiple time periods. Provides detailed statistical analysis for risk assessment and performance evaluation in
   * both currencies.
   * 
   * <p>
   * This method computes professional-grade statistical measures using Apache Commons Math for maximum accuracy and
   * reliability. The analysis includes:
   * </p>
   * <ul>
   * <li>Daily volatility and return distribution statistics</li>
   * <li>Monthly volatility for medium-term risk assessment</li>
   * <li>Annual volatility for long-term risk evaluation</li>
   * <li>Minimum and maximum return values for range analysis</li>
   * </ul>
   * 
   * <p>
   * All calculations properly handle currency conversion and provide results in both the instrument's native currency
   * and the tenant's main currency for comprehensive analysis and comparison purposes.
   * </p>
   * 
   * @param jdbcTemplate   database template for efficient price data retrieval
   * @param dateFrom       inclusive start date for the statistical analysis period
   * @param dateTo         exclusive end date for the statistical analysis period
   * @param adjustCurrency flag indicating whether to perform currency conversion
   * @return comprehensive statistical summary with volatility and distribution measures
   */
  public StatisticsSummary getStandardDeviation(JdbcTemplate jdbcTemplate, LocalDate dateFrom, LocalDate dateTo,
      boolean adjustCurrency) throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {

    StatisticsSummary statsSummary = new StatisticsSummary();
    statsSummary.addProperties(TimePeriodType.DAILY, StatisticsSummary.STANDARD_DEVIATION, StatisticsSummary.MIN,
        StatisticsSummary.MAX);
    statsSummary.addProperties(TimePeriodType.MONTHLY, StatisticsSummary.STANDARD_DEVIATION);
    statsSummary.addProperties(TimePeriodType.ANNUAL, StatisticsSummary.STANDARD_DEVIATION);

    for (TimePeriodType key : statsSummary.statsPropertyMap.keySet()) {
      if (key != TimePeriodType.ANNUAL) {
        createStatisticsSummaryByPeriod(jdbcTemplate, dateFrom, dateTo, adjustCurrency, key, statsSummary);
      }
    }
    statsSummary.createAnnualByMonthly();

    return statsSummary;
  }

  /**
   * Creates statistical analysis for a specific time period (daily or monthly). Loads price data, calculates percentage
   * changes, and computes statistical measures using professional mathematical libraries for accuracy and reliability.
   * 
   * <p>
   * This method handles the process of loading price data with proper currency conversion, transforming prices to
   * percentage returns, and computing statistical measures using Apache Commons Math. The analysis provides both native
   * currency and main currency statistics for comprehensive evaluation.
   * </p>
   * 
   * @param jdbcTemplate   database template for price data loading
   * @param dateFrom       start date for the analysis period
   * @param dateTo         end date for the analysis period
   * @param adjustCurrency whether to perform currency adjustment
   * @param timePeriodType the time period granularity (daily or monthly)
   * @param statsSummary   the statistics summary object to populate
   */
  private void createStatisticsSummaryByPeriod(JdbcTemplate jdbcTemplate, LocalDate dateFrom, LocalDate dateTo,
      boolean adjustCurrency, TimePeriodType timePeriodType, StatisticsSummary statsSummary)
      throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
    List<Securitycurrency<?>> securitycurrencyList = new ArrayList<>(Arrays.asList(securityCurrency));
    CurrencyAvailableRequired currencyAvailableRequired = prepareFakeSecurityWhenNotTenantCurrency(
        securitycurrencyList);

    ClosePricesCurrencyClose closePrices = ReportHelper.loadCloseData(jdbcTemplate, currencypairJpaRepository,
        securitycurrencyList, SamplingPeriodType.getSamplingPeriodTypeByValue(timePeriodType.getValue()), dateFrom,
        dateTo, adjustCurrency);
    adjustCloseOfDifferentTenantCurrency(securitycurrencyList, currencyAvailableRequired, closePrices);
    int columns = currencyAvailableRequired == null ? 1 : 2;
    double[][] percentageChange = ReportHelper.transformToPercentageChange(closePrices.dateCloseTree, columns);
    SummaryStatistics[] stats = Stream.iterate(0, x -> x + 1).limit(columns).map(i -> new SummaryStatistics())
        .toArray(SummaryStatistics[]::new);
    for (double[] element : percentageChange) {
      for (int col = 0; col < columns; col++) {
        stats[col].addValue(element[col]);
      }
    }

    List<StatsProperty> statsProperties = statsSummary.getPropertiesBySamplingPeriodType(timePeriodType);
    for (StatsProperty statsProperty : statsProperties) {
      statsProperty.value = (double) PropertyUtils.getProperty(stats[0], statsProperty.property);
      if (stats.length == 2) {
        statsProperty.valueMC = (double) PropertyUtils.getProperty(stats[1], statsProperty.property);
      }
    }
  }

  /**
   * Prepares the security list with currency conversion configuration for multi-currency analysis. Creates a
   * specialized setup that enables currency conversion calculations by adding synthetic securities and currency pairs
   * to the analysis framework.
   * 
   * <p>
   * This method implements a sophisticated workaround for multi-currency statistical analysis by creating temporary
   * security configurations that enable the statistical calculation framework to handle currency conversion properly.
   * </p>
   * 
   * @param securitycurrencyList the list of securities to configure for analysis
   * @return currency conversion configuration if multi-currency analysis is needed, null otherwise
   */
  private CurrencyAvailableRequired prepareFakeSecurityWhenNotTenantCurrency(
      List<Securitycurrency<?>> securitycurrencyList) {
    if (!securityTenantSameCurrency) {
      Currencypair cp = currencypairs.getFirst();
      Security security = (Security) securityCurrency;
      Security fakeSecurity = new Security();
      fakeSecurity.setIdSecuritycurrency(security.getIdSecuritycurrency());
      fakeSecurity.setCurrency(security.getCurrency());
      securitycurrencyList.add(fakeSecurity);
      securitycurrencyList.add(cp);
      return new CurrencyAvailableRequired(securitycurrencyList.size() - 1, cp.getIdSecuritycurrency(),
          cp.getFromCurrency(), cp.getToCurrency());
    }
    return null;
  }

  /**
   * Applies currency conversion to loaded price data for accurate multi-currency statistical analysis. Configures the
   * currency conversion framework and applies appropriate exchange rate adjustments to ensure statistical calculations
   * reflect performance in the target currency.
   * 
   * <p>
   * This method temporarily modifies security currency settings to enable the currency conversion process, applies the
   * conversion, and then restores the original configuration to maintain data integrity.
   * </p>
   * 
   * @param securitycurrencyList      the configured security list for analysis
   * @param currencyAvailableRequired currency conversion configuration if needed
   * @param closePrices               the price data structure to apply currency conversion to
   */
  private void adjustCloseOfDifferentTenantCurrency(List<Securitycurrency<?>> securitycurrencyList,
      CurrencyAvailableRequired currencyAvailableRequired, ClosePricesCurrencyClose closePrices) {
    if (currencyAvailableRequired != null) {
      Security security = (Security) securitycurrencyList.get(0);
      String realCurrency = security.getCurrency();
      security.setCurrency(tenantCurrency);
      var cr = new CurrencyRequired(tenantCurrency);
      cr.carList.add(currencyAvailableRequired);
      closePrices.currencyRequired = cr;
      ReportHelper.adjustCloseToSameCurrency(securitycurrencyList, closePrices);
      security.setCurrency(realCurrency);
    }
  }

}
