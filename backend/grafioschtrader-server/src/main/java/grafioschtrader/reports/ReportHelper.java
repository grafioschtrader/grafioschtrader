package grafioschtrader.reports;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.security.core.context.SecurityContextHolder;

import grafiosch.entities.User;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitycurrency;
import grafioschtrader.entities.projection.CurrencyCount;
import grafioschtrader.reportviews.DateTransactionCurrencypairMap;
import grafioschtrader.repository.CurrencypairJpaRepository;
import grafioschtrader.repository.HistoryquoteJpaRepository;
import grafioschtrader.types.SamplingPeriodType;

/**
 * Comprehensive utility class providing specialized data loading and processing capabilities for financial reporting
 * and analysis. Offers sophisticated currency conversion, historical price loading, and statistical transformation
 * services for multi-currency portfolio analysis with tenant-specific optimization.
 * 
 * <p>
 * This helper class forms the foundation for financial reports by handling the intricate details of currency
 * normalization, historical data retrieval, and price series transformation. It enables consistent analysis across
 * different currencies, time periods, and sampling frequencies while maintaining data integrity, performance
 * optimization, and tenant isolation.
 * </p>
 */
public abstract class ReportHelper {
  private final static String WHERE_WORD = " WHERE ";

  /**
   * Loads tenant-specific historical exchange rate data for the specified until date if not already cached. Uses the
   * current user's tenant context to retrieve only the currency conversion data relevant to that tenant's portfolio and
   * trading history.
   * 
   * <p>
   * This method implements the first tier of the two-level currency loading strategy, focusing on tenant-specific
   * currency context rather than analysis-specific requirements. It loads historical exchange rates only for currencies
   * that the tenant has actually used in their transactions and holdings, optimizing both performance and data
   * relevance.
   * </p>
   * 
   * <p>
   * The loaded currency data is cached in the DateTransactionCurrencypairMap to avoid redundant database queries and
   * provide efficient access to historical conversion rates for subsequent analysis operations within the same tenant
   * context.
   * </p>
   * 
   * @param historyquoteJpaRepository repository for accessing historical quote data
   * @param dateCurrencyMap           currency mapping context that tracks loaded data and conversion rates
   */
  public static void loadUntilDateHistoryquotes(final HistoryquoteJpaRepository historyquoteJpaRepository,
      DateTransactionCurrencypairMap dateCurrencyMap) {
    if (!dateCurrencyMap.isUntilDateEqualNowOrAfter() && !dateCurrencyMap.isUntilDateDataLoaded()) {
      final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
      loadUntilDateHistoryquotesWithoutCheck(user.getIdTenant(), historyquoteJpaRepository, dateCurrencyMap);
    }
  }

  /**
   * Loads tenant-specific historical exchange rate data for the specified until date and tenant if not already cached.
   * Provides tenant-isolated currency conversion data for accurate historical analysis while maintaining multi-tenant
   * data separation.
   * 
   * <p>
   * This method enables loading currency data for a specific tenant without relying on the security context, making it
   * suitable for batch processing, administrative operations, or cross-tenant analysis scenarios. It maintains the same
   * tenant-specific optimization by loading only currencies relevant to the specified tenant's portfolio.
   * </p>
   * 
   * @param idTenant                  the tenant identifier for accessing appropriate tenant-specific currency data
   * @param historyquoteJpaRepository repository for accessing historical quote data
   * @param dateCurrencyMap           currency mapping context that tracks loaded data and conversion rates
   */
  public static void loadUntilDateHistoryquotes(final Integer idTenant,
      final HistoryquoteJpaRepository historyquoteJpaRepository, DateTransactionCurrencypairMap dateCurrencyMap) {
    if (!dateCurrencyMap.isUntilDateEqualNowOrAfter() && !dateCurrencyMap.isUntilDateDataLoaded()) {
      loadUntilDateHistoryquotesWithoutCheck(idTenant, historyquoteJpaRepository, dateCurrencyMap);
    }
  }

  /**
   * Forces loading of tenant-specific historical exchange rate data without checking cache status. Retrieves currency
   * conversion rates for the specified date and tenant, updating the currency map with only the currencies that are
   * relevant to that tenant's trading history and portfolio.
   * 
   * @param idTenant                  the tenant identifier for accessing tenant-specific currency usage data
   * @param historyquoteJpaRepository repository for accessing historical quote data
   * @param dateCurrencyMap           currency mapping context to populate with tenant-relevant conversion rates
   */
  public static void loadUntilDateHistoryquotesWithoutCheck(final Integer idTenant,
      final HistoryquoteJpaRepository historyquoteJpaRepository, DateTransactionCurrencypairMap dateCurrencyMap) {
    List<Object[]> currencyList = historyquoteJpaRepository.getUsedCurrencyHistoryquotesByIdTenantAndDate(idTenant,
        dateCurrencyMap.getUntilDate());
    dateCurrencyMap.putToDateFromCurrencyMap(currencyList);
    dateCurrencyMap.untilDateDataIsLoaded();
  }

  /**
   * Loads closing price data for a list of securities, optionally adjusted into a single target currency, over a given
   * date range and sampling period. This ensures that the prices of all transferred securities are always available for
   * each date.
   *
   * @param jdbcTemplate              the Spring JDBC template to execute queries
   * @param currencypairJpaRepository repository to fetch currency‐pair conversion info
   * @param securitycurrencyList      list of security‐currency entities to load
   * @param samplingPeriod            daily, monthly, or annual sampling
   * @param dateFrom                  inclusive lower bound for dates
   * @param dateTo                    exclusive upper bound for dates
   * @param adjustToSingleCurrency    If true, the currency pairs that allow normalization to a currency are also added
   *                                  to the query
   * @return a ClosePricesCurrencyClose wrapping a date→prices map and any currency‐conversion info
   */
  public static ClosePricesCurrencyClose loadCloseData(JdbcTemplate jdbcTemplate,
      CurrencypairJpaRepository currencypairJpaRepository, List<Securitycurrency<?>> securitycurrencyList,
      SamplingPeriodType samplingPeriod, LocalDate dateFrom, LocalDate dateTo, boolean adjustToSingleCurrency) {
    CurrencyRequired cr = null;
    StringBuilder qSelect = new StringBuilder("SELECT h0.date");
    StringBuilder qFrom = new StringBuilder(" FROM ");
    StringBuilder qWhere = new StringBuilder(WHERE_WORD);
    List<Integer> securityCurrencyIds = securitycurrencyList.stream().map(sc -> sc.getIdSecuritycurrency())
        .collect(Collectors.toList());

    if (adjustToSingleCurrency) {
      cr = adjustToSingleCurrency(currencypairJpaRepository, securitycurrencyList);
      cr.carList.forEach(cfa -> {
        securityCurrencyIds.add(cfa.idSecuritycurrency);
        cfa.column = securityCurrencyIds.size() - 1;
      });
    }

    for (int i = 0; i < securityCurrencyIds.size(); i++) {
      qSelect.append(", h" + i + ".close AS c" + i);
      if (i == 0) {
        qFrom.append(Historyquote.TABNAME + " h" + i);
      } else {
        qFrom.append(" JOIN " + Historyquote.TABNAME + " h" + i + " ON " + "h0.date=h" + i + ".date");
      }
      if (i > 0) {
        qWhere.append(" AND ");
      }
      qWhere.append("h" + i + ".id_securitycurrency=" + securityCurrencyIds.get(i));
    }
    addDateBoundry(dateFrom, qWhere, ">");
    addDateBoundry(dateTo, qWhere, "<");
    StringBuilder qGroup = addMonthYearGroupBy(samplingPeriod);

    String query = qSelect.append(qFrom).append(WHERE_WORD.endsWith(qWhere.toString()) ? "" : qWhere).append(qGroup)
        .append(" ORDER BY h0.date").toString();
    return new ClosePricesCurrencyClose(getQueryDateCloseAsTreeMap(jdbcTemplate, query, securityCurrencyIds.size()),
        cr);
  }

  /**
   * Applies currency conversion to loaded price data if currency adjustment is required. Convenience method that checks
   * for currency adjustment requirements before processing.
   * 
   * @param securitycurrencyList the list of securities for which prices were loaded
   * @param cpcc                 the price data container with optional currency conversion information
   */
  public static void adjustCloseToSameCurrency(List<Securitycurrency<?>> securitycurrencyList,
      ClosePricesCurrencyClose cpcc) {
    if (cpcc.currencyRequired != null && cpcc.currencyRequired.needCurrencyAdjustment()) {
      adjustCloseToSameCurrency(securitycurrencyList, cpcc, cpcc.currencyRequired);
    }
  }

  /**
   * Performs the actual currency conversion of price data using the provided exchange rates. Converts all security
   * prices to the target currency for unified analysis and comparison.
   * 
   * @param securitycurrencyList the list of securities for which prices are being converted
   * @param cpcc                 the price data container with currency conversion rates
   * @param cr                   the currency requirements and conversion configuration
   */
  private static void adjustCloseToSameCurrency(List<Securitycurrency<?>> securitycurrencyList,
      ClosePricesCurrencyClose cpcc, CurrencyRequired cr) {
    for (int col = 0; col < securitycurrencyList.size(); col++) {
      if (securitycurrencyList.get(col) instanceof Security
          && !((Security) securitycurrencyList.get(col)).getCurrency().equals(cpcc.currencyRequired.adjustCurrency)) {
        Security s = (Security) securitycurrencyList.get(col);
        CurrencyAvailableRequired car = cr.get2ndCurrency(s.getCurrency());
        for (double[] closeRow : cpcc.dateCloseTree.values()) {
          closeRow[col] *= cr.isAdjustCurrencyEqualsFromCurrency(car) ? 1.0 / closeRow[car.column]
              : closeRow[car.column];
        }
      }
    }
  }

  /**
   * Transforms a time series of price data into percentage change data for return analysis. Calculates
   * period-over-period percentage changes for statistical and performance analysis.
   * 
   * @param closeValuesMap chronologically ordered map of price data by date
   * @param columns        number of securities/instruments in the price series
   * @return two-dimensional array of percentage changes with rows representing time periods and columns representing
   *         securities
   */
  public static double[][] transformToPercentageChange(Map<LocalDate, double[]> closeValuesMap, int columns) {
    double[][] data = new double[closeValuesMap.size() - 1][columns];
    double[] prevCloseRow = null;
    int l = 0;
    for (double[] closeRow : closeValuesMap.values()) {
      if (prevCloseRow == null) {
        prevCloseRow = closeRow;
      } else {
        for (int colCounter = 0; colCounter < columns; colCounter++) {
          data[l][colCounter] = (closeRow[colCounter] / prevCloseRow[colCounter] - 1) * 100.0;
        }
        prevCloseRow = closeRow;
        l++;
      }
    }
    return data;
  }

  /**
   * Executes the constructed SQL query and transforms results into a chronologically ordered map. Uses efficient result
   * set processing to handle large price datasets with optimal memory usage.
   * 
   * @param jdbcTemplate the JDBC template for query execution
   * @param query        the complete SQL query string
   * @param columns      number of price columns to extract from each result row
   * @return tree map with dates as keys and price arrays as values, maintaining chronological order
   */
  private static TreeMap<LocalDate, double[]> getQueryDateCloseAsTreeMap(JdbcTemplate jdbcTemplate, String query,
      int columns) {
    return jdbcTemplate.query(query, new ResultSetExtractor<TreeMap<LocalDate, double[]>>() {
      @Override
      public TreeMap<LocalDate, double[]> extractData(ResultSet rs) throws SQLException, DataAccessException {
        TreeMap<LocalDate, double[]> resultCloseMap = new TreeMap<>();
        while (rs.next()) {
          var closeColumns = new double[columns];
          for (int i = 0; i < columns; i++) {
            closeColumns[i] = rs.getDouble(2 + i);
          }
          resultCloseMap.put(rs.getDate(1).toLocalDate(), closeColumns);
        }
        return resultCloseMap;
      }
    });
  }

  /**
   * Analyzes currency requirements and determines the optimal target currency for normalization. Identifies existing
   * currency pairs and determines which additional pairs need to be created.
   * 
   * @param currencypairJpaRepository repository for currency pair operations
   * @param securitycurrencyList      list of securities and currencies to analyze
   * @return currency requirements object containing target currency and needed conversions
   */
  private static CurrencyRequired adjustToSingleCurrency(CurrencypairJpaRepository currencypairJpaRepository,
      List<Securitycurrency<?>> securitycurrencyList) {
    Map<String, Integer> requiredCurrenciesSet = new HashMap<>();
    CurrencyRequired cr = new CurrencyRequired();
    for (int i = 0; i < securitycurrencyList.size(); i++) {
      Securitycurrency<?> sc = securitycurrencyList.get(i);
      if (sc instanceof Security) {
        requiredCurrenciesSet.merge(((Security) sc).getCurrency(), 1, Integer::sum);
      } else {
        Currencypair cp = (Currencypair) sc;
        cr.carList.add(
            new CurrencyAvailableRequired(i, cp.getIdSecuritycurrency(), cp.getFromCurrency(), cp.getToCurrency()));
      }
    }
    determineMissingCurrencyPairs(currencypairJpaRepository, requiredCurrenciesSet, cr);
    return cr;
  }

  /**
   * Determines which currency pairs are missing for complete currency conversion coverage and coordinates their
   * creation. Selects the optimal target currency based on availability and creates missing conversion pairs as needed.
   * 
   * @param currencypairJpaRepository repository for currency pair management
   * @param requiredCurrenciesSet     set of currencies that need conversion capability
   * @param cr                        currency requirements object to populate with conversion needs
   */
  private static void determineMissingCurrencyPairs(CurrencypairJpaRepository currencypairJpaRepository,
      Map<String, Integer> requiredCurrenciesSet, CurrencyRequired cr) {

    if (requiredCurrenciesSet.size() > 1) {
      List<CurrencyCount> cc = currencypairJpaRepository.countCurrencyGroupByCurrency(requiredCurrenciesSet.keySet());
      cr.adjustCurrency = cc.get(0).getCurrency();
      List<String> cpairList = new ArrayList<>();
      for (Map.Entry<String, Integer> entry : requiredCurrenciesSet.entrySet()) {
        if (cr.adjustCurrency.equals(entry.getKey())) {
          continue;
        } else {
          Optional<CurrencyAvailableRequired> loadedCurrencyOpt = cr.containsCurrencypairIgnoreFromTo(cr.adjustCurrency,
              entry.getKey());
          if (loadedCurrencyOpt.isEmpty()) {
            cr.carList.add(new CurrencyAvailableRequired(-1, -1, cr.adjustCurrency, entry.getKey()));
            cpairList.add(cr.adjustCurrency + entry.getKey());
          } else {
            cr.carList.add(loadedCurrencyOpt.get());
          }
        }
      }
      completeCurrencyAvailableRequired(cr, currencypairJpaRepository.getPairsByFromAndToCurrency(cpairList));
      createMissingCurrencypair(currencypairJpaRepository, cr);
    }
  }

  /**
   * Updates currency requirements with actual currency pair data from the database. Matches found currency pairs with
   * the requirements and updates the configuration.
   * 
   * @param cr             currency requirements to update with found pairs
   * @param possibleCpList list of existing currency pairs found in the database
   */
  private static void completeCurrencyAvailableRequired(CurrencyRequired cr, List<Currencypair> possibleCpList) {
    for (Currencypair currencypair : possibleCpList) {
      Optional<CurrencyAvailableRequired> carOpt = cr.containsCurrencypairIgnoreFromTo(currencypair.getFromCurrency(),
          currencypair.getToCurrency());
      if (carOpt.isPresent() && carOpt.get().idSecuritycurrency.equals(-1)) {
        CurrencyAvailableRequired car = carOpt.get();
        car.adjust(currencypair.getIdSecuritycurrency(), currencypair.getFromCurrency(), currencypair.getToCurrency());
      }
    }
  }

  /**
   * Creates missing currency pairs that are needed for complete currency conversion coverage. Automatically generates
   * new currency pairs for conversions that don't exist in the system.
   * 
   * @param currencypairJpaRepository repository for creating new currency pairs
   * @param cr                        currency requirements containing the missing pairs to create
   */
  private static void createMissingCurrencypair(CurrencypairJpaRepository currencypairJpaRepository,
      CurrencyRequired cr) {
    for (CurrencyAvailableRequired car : cr.getMissingCurrencypair()) {
      Currencypair cp = currencypairJpaRepository.createNonExistingCurrencypair(car.formCurrency, car.toCurrency,
          false);
      car.adjust(cp.getIdSecuritycurrency(), cp.getFromCurrency(), cp.getToCurrency());
    }
  }

  /**
   * Adds date boundary conditions to the SQL WHERE clause for efficient query filtering.
   * 
   * @param date     the boundary date to add (null values are ignored)
   * @param qWhere   the WHERE clause builder to modify
   * @param lessMore the comparison operator (">" for greater than, "<" for less than)
   */
  private static void addDateBoundry(LocalDate date, StringBuilder qWhere, String lessMore) {
    if (date != null) {
      qWhere.append(" AND h0.date " + lessMore + "= \"" + date + "\" ");
    }
  }

  /**
   * Constructs GROUP BY clause for different sampling periods to aggregate price data. Adds appropriate grouping for
   * monthly or annual sampling while leaving daily data ungrouped.
   * 
   * @param samplingPeriodType the desired sampling frequency for data aggregation
   * @return SQL GROUP BY clause appropriate for the sampling period
   */
  private static StringBuilder addMonthYearGroupBy(SamplingPeriodType samplingPeriodType) {
    StringBuilder qGroup = new StringBuilder("");
    if (samplingPeriodType != SamplingPeriodType.DAILY_RETURNS) {
      qGroup.append(" GROUP BY YEAR(h0.date)");
      if (samplingPeriodType == SamplingPeriodType.MONTHLY_RETURNS) {
        qGroup.append(", MONTH(h0.date)");
      }
    }
    return qGroup;
  }

  /**
   * Represents a currency conversion requirement with associated metadata for price data loading. Tracks the column
   * position, security currency ID, and conversion direction for a specific currency pair used in multi-currency price
   * analysis.
   * 
   * <p>
   * This class enables the sophisticated currency conversion logic by maintaining the relationship between database
   * entities and their position in the analysis matrix, supporting both existing and dynamically created currency
   * pairs.
   * </p>
   */
  public static class CurrencyAvailableRequired {
    public int column;
    public Integer idSecuritycurrency;
    public String formCurrency;
    public String toCurrency;

    public CurrencyAvailableRequired(int column, Integer idSecuritycurrency, String formCurrency, String toCurrency) {
      this.column = column;
      this.idSecuritycurrency = idSecuritycurrency;
      this.formCurrency = formCurrency;
      this.toCurrency = toCurrency;
    }

    public void adjust(Integer idSecuritycurrency, String formCurrency, String toCurrency) {
      this.idSecuritycurrency = idSecuritycurrency;
      this.formCurrency = formCurrency;
      this.toCurrency = toCurrency;
    }

    @Override
    public String toString() {
      return "CurrencyAvailableRequired [column=" + column + ", idSecuritycurrency=" + idSecuritycurrency
          + ", formCurrency=" + formCurrency + ", toCurrency=" + toCurrency + "]";
    }
  }

  /**
   * Manages currency conversion requirements for multi-currency price data analysis. Coordinates the identification of
   * target currency and required conversion pairs, enabling sophisticated automatic currency normalization across
   * diverse portfolios.
   * 
   * <p>
   * This class implements the intelligence for selecting optimal target currencies based on availability and managing
   * the relationships between different currency pairs needed for comprehensive multi-currency analysis.
   * </p>
   */
  public static class CurrencyRequired {
    /**
     * The target currency (ISO 4217) selected for normalization. Automatically chosen based on currency pair
     * availability and frequency of use in the analysis set.
     */
    public String adjustCurrency;
    /**
     * List of all currency conversion requirements needed for the analysis. Includes both existing currency pairs and
     * those that need to be created.
     */
    public final List<CurrencyAvailableRequired> carList = new ArrayList<>();

    public CurrencyRequired() {
    }

    public CurrencyRequired(String adjustCurrency) {
      this.adjustCurrency = adjustCurrency;
    }

    /**
     * Searches for an existing currency pair requirement regardless of conversion direction. Supports bidirectional
     * currency pair matching (e.g., EUR/USD matches USD/EUR).
     * 
     * @param c1 first currency code to search for
     * @param c2 second currency code to search for
     * @return optional containing the matching currency pair requirement if found
     */
    public Optional<CurrencyAvailableRequired> containsCurrencypairIgnoreFromTo(String c1, String c2) {
      return carList.stream().filter(cp -> cp.formCurrency.equals(c1) && cp.toCurrency.equals(c2)
          || cp.formCurrency.equals(c2) && cp.toCurrency.equals(c1)).findFirst();
    }

    public boolean isAdjustCurrencyEqualsFromCurrency(CurrencyAvailableRequired car) {
      return car.formCurrency.equals(adjustCurrency);
    }

    public CurrencyAvailableRequired get2ndCurrency(String currency) {
      return carList.stream().filter(cp -> cp.formCurrency.equals(currency) || cp.toCurrency.equals(currency)).findAny()
          .orElse(null);
    }

    public boolean needCurrencyAdjustment() {
      return adjustCurrency != null;
    }

    public List<CurrencyAvailableRequired> getMissingCurrencypair() {
      return carList.stream().filter(car -> car.idSecuritycurrency.equals(-1)).collect(Collectors.toList());
    }

  }

  /**
   * Container for loaded price data and associated currency conversion information. Provides a comprehensive data
   * structure for multi-currency price analysis with chronologically ordered price data and optional currency
   * normalization metadata.
   */
  public static class ClosePricesCurrencyClose {
    /**
     * Chronologically ordered map of price data by date. Each date maps to an array of closing prices for all
     * securities and currency pairs in the analysis. TreeMap ensures proper chronological ordering for time series
     * analysis.
     */
    public final TreeMap<LocalDate, double[]> dateCloseTree;
    /**
     * Currency conversion requirements and configuration for multi-currency analysis. Null when all securities use the
     * same currency and no conversion is needed.
     */
    public CurrencyRequired currencyRequired;

    public ClosePricesCurrencyClose(TreeMap<LocalDate, double[]> dateCloseTree, CurrencyRequired currencyRequired) {
      this.dateCloseTree = dateCloseTree;
      this.currencyRequired = currencyRequired;
    }

  }

}
