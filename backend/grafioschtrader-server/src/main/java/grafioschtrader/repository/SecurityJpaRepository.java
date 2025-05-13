package grafioschtrader.repository;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.EntityGraph.EntityGraphType;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.query.Procedure;

import grafiosch.rest.UpdateCreateJpaRepository;
import grafioschtrader.dto.IHistoryquoteQualityFlat;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.projection.IFormulaSecurityLoad;
import grafioschtrader.entities.projection.IdSecurityCurrencyPairInfo;
import grafioschtrader.entities.projection.SecurityYearClose;
import grafioschtrader.priceupdate.historyquote.SecurityCurrencyMaxHistoryquoteData;
import grafioschtrader.reportviews.historyquotequality.IHistoryquoteQualityWithSecurityProp;
import grafioschtrader.reportviews.securityaccount.SecurityPositionSummary;

/**
 * Spring Data JPA repository for managing Security entities 
 */
public interface SecurityJpaRepository extends SecurityCurrencypairJpaRepository<Security>,
    JpaSpecificationExecutor<Security>, SecurityJpaRepositoryCustom, UpdateCreateJpaRepository<Security> {

  /**
   * Executes the `deleteUpdateHistoryQuality` stored procedure to refresh the
   * historyquote_quality metrics for all securities.
   * <p>
   * This procedure clears existing quality records and regenerates completeness
   * and creation‐type statistics (missing days, connectorCreated, manualImported, etc.)
   * along with quality percentages based on current historyquote data. :contentReference[oaicite:0]{index=0}:contentReference[oaicite:1]{index=1}
   */
  @Procedure(procedureName = "deleteUpdateHistoryQuality")
  void deleteUpdateHistoryQuality();

  Security findByName(String name);

  List<Security> findByIdSecuritycurrencyInOrderByName(Set<Integer> idSecuritycurrencyIds);

  Security findByIsinAndCurrency(String isin, String currencySecurity);

  List<Security> findByIsin(String isin);

  List<Security> findAllByIsinIn(Set<String> isinSet);

  Security findByTickerSymbolAndCurrency(String tickerSymbol, String currencySecurity);

  List<Security> findByActiveToDateGreaterThanEqualAndIdTenantPrivateIsNullOrIdTenantPrivateOrderByName(Date date,
      Integer idTenantPrivate);

  List<Security> findByTickerSymbolInOrderByIdSecuritycurrency(Set<String> tickers);

  List<Security> findByAssetClass_CategoryTypeInAndAssetClass_SpecialInvestmentInstrumentInAndActiveToDateAfterAndIdTenantPrivateIsNull(
      Set<Byte> categoryTypes, Set<Byte> specialInvestmentInstruments, Date date);

  //Catch history quotes as well for this Security
  @Override
  @EntityGraph(value = "graph.security.historyquote", type = EntityGraphType.FETCH)
  Security findByIdSecuritycurrency(Integer idSecuritycurrency);

  //@formatter:off
  /**
   * Retrieves a Security by its ID only if it is publicly accessible or belongs to the specified tenant.
   * <p>
   * The query selects a Security where:
   * <ul>
   *   <li><code>idSecuritycurrency</code> matches the given ID, and</li>
   *   <li><code>idTenantPrivate</code> is null (public) or equals the provided tenantPrivate ID.</li>
   * </ul>
   *
   * @param idSecuritycurrency the identifier of the Security to retrieve
   * @param idTenantPrivate    the tenant ID for private securities (ignored for public securities)
   * @return the matching Security entity, or null if no accessible security is found
   */
  //@formatter:on
  @Query(value = "SELECT s FROM Security s WHERE s.idSecuritycurrency = ?1 AND (s.idTenantPrivate IS NULL OR s.idTenantPrivate = ?2)")
  Security findByIdTenantPrivateIsNullOrIdTenantPrivateAndIdSecuritycurrency(Integer idSecuritycurrency,
      Integer idTenantPrivate);
  
  //@formatter:off
  /**
   * Finds active securities in the given watchlist that have experienced failed intraday price loads
   * and still have a configured intraday connector (e.g., Yahoo Finance).
   * <p>
   * Filters for:
   * <ul>
   *   <li>retryIntraLoad > 0: one or more previous failures to fetch intraday prices</li>
   *   <li>activeToDate ≥ today: security is still active</li>
   *   <li>idConnectorIntra is not null: a valid external intraday price connector is configured</li>
   * </ul>
   *
   * @param idTenant    the tenant ID owning the watchlist
   * @param idWatchlist the watchlist ID
   * @return a list of Security entities that need intraday price reload attempts
   */
  //@formatter:on
  @Query(value = """
      SELECT s FROM Watchlist w JOIN w.securitycurrencyList s
      WHERE w.idTenant = ?1 AND w.idWatchlist = ?2 AND s.retryIntraLoad > 0 AND s.activeToDate >= CURDATE()
      AND s.idConnectorIntra IS NOT NULL""")
  List<Security> findWithConnectorByIdTenantAndIdWatchlistWhenRetryIntraGreaterThan0(Integer idTenant,
      Integer idWatchlist);

  //@formatter:off
  /**
   * Finds derived securities (those linked via idLinkSecuritycurrency) in the specified watchlist
   * that have experienced failed intraday price loads and are still active.
   * <p>
   * A “derived” security is one whose price is computed from another security via a formula
   * (idLinkSecuritycurrency not null).  This query selects only those entries where:
   * <ul>
   *   <li>retryIntraLoad > 0: at least one previous failure to fetch intraday prices</li>
   *   <li>activeToDate ≥ today: the security is not yet deactivated</li>
   * </ul>
   * These derived securities will need recalculation once their base security prices are available.
   *
   * @param idTenant    the tenant ID owning the watchlist
   * @param idWatchlist the watchlist ID
   * @return a list of derived Security entities pending intraday price recalculation
   */
  //@formatter:on
  @Query(value = """
      SELECT s FROM Watchlist w JOIN w.securitycurrencyList s
      WHERE w.idTenant = ?1 AND w.idWatchlist = ?2 AND s.retryIntraLoad > 0
      AND s.activeToDate >= CURDATE() AND s.idLinkSecuritycurrency IS NOT NULL""")
  List<Security> findDerivedByIdTenantAndIdWatchlistWhenRetryIntraGreaterThan0(Integer idTenant, Integer idWatchlist);

  //@formatter:off
  /**
   * Retrieves all derived securities (i.e., those whose prices are computed from another security)
   * that have not yet loaded any historical quotes and have retryHistoryLoad below the specified threshold.
   * <p>
   * Filters for:
   * <ul>
   *   <li>`idLinkSecuritycurrency` is not null: security is derived from another security</li>
   *   <li>`retryHistoryLoad < retryHistoryLoad`: has remaining retry attempts</li>
   *   <li>no entries in `historyquoteList`: history quotes have not been loaded</li>
   * </ul>
   *
   * @param retryHistoryLoad the maximum retryHistoryLoad value; only securities with a lower count are returned
   * @return a list of derived Security entities pending their first history quote load
   */
  //@formatter:on
  @Query(value = """
      SELECT s FROM Security s WHERE s.idLinkSecuritycurrency IS NOT NULL
      AND s.retryHistoryLoad < ?1 AND NOT EXISTS (SELECT h FROM s.historyquoteList h)""")
  List<Security> findDerivedEmptyHistoryquoteByMaxRetryHistoryLoad(short retryHistoryLoad);

  //@formatter:off
  /**
   * Retrieves the most recent history quote date for each security in the specified watchlist
   * that has pending historical-reload failures and an active history connector.
   * <p>
   * Filters for:
   * <ul>
   *   <li>retryHistoryLoad > 0: one or more failed history-load attempts</li>
   *   <li>activeToDate ≥ quote date: security still active at the time of its latest quote</li>
   *   <li>idConnectorHistory not null: a configured external history connector</li>
   * </ul>
   * Results are grouped by security and include only those with at least one quote.
   *
   * @param idTenant    the tenant ID owning the watchlist
   * @param idWatchlist the watchlist ID
   * @return a list of SecurityCurrencyMaxHistoryquoteData projections containing each Security
   *         and its latest history-quote date
   */
  //@formatter:on
  @Query(value = """
      SELECT s as securityCurrency, MAX(h.date) AS date FROM Watchlist w JOIN w.securitycurrencyList s JOIN s.historyquoteList h
      WHERE w.idTenant = ?1 AND w.idWatchlist = ?2 AND s.retryHistoryLoad > 0 AND s.activeToDate >= h.date
      AND s.idConnectorHistory IS NOT NULL GROUP BY s.idSecuritycurrency HAVING s.activeToDate > MAX(h.date)""")
  List<SecurityCurrencyMaxHistoryquoteData<Security>> findByIdTenantAndIdWatchlistWhenRetryHistroyGreaterThan0(
      Integer idTenant, Integer idWatchlist);

  //@formatter:off
  /**
   * Retrieves all tradable securities in the specified watchlist for a tenant.
   * <p>
   * Filters for:
   * <ul>
   *   <li>Watchlist belonging to the given tenant (idTenant) and watchlist ID (idWatchlist).</li>
   *   <li>Securities whose special investment instrument is not NON_INVESTABLE_INDICES (10).</li>
   * </ul>
   * Results are ordered by the security name.
   *
   * @param idTenant    the tenant ID owning the watchlist
   * @param idWatchlist the watchlist ID
   * @return a list of Security entities that are tradable within the watchlist
   */
  //@formatter:on
  @Query(value = """
      SELECT s FROM Watchlist w JOIN w.securitycurrencyList s
      WHERE w.idTenant = ?1 AND w.idWatchlist = ?2 AND s.assetClass.specialInvestmentInstrument <> 10 ORDER BY s.name""")
  List<Security> getTradableSecuritiesByTenantAndIdWatschlist(Integer idTenant, Integer idWatchlist);

  

  //@formatter:off
  /**
   * Retrieves the most recent history-quote date for each active Security that has a configured history connector
   * and remaining retry attempts below the specified threshold.
   * <p>
   * - Filters for Security entities where retryHistoryLoad &lt; maxHistoryRetry and idConnectorHistory is not null.  
   * - Joins to historyquoteList to find the maximum quote date per security.  
   * - Includes only securities still active at their latest quote date (activeToDate ≥ MAX(h.date)).  
   * - Orders results by security ID.
   * 
   * With or without this clause "ORDER BY s.idSecuritycurrency" we get more rows than without it. Why? 
   * 
   * @param maxHistoryRetry the upper limit for retryHistoryLoad; securities with lower values are included
   * @return a list of SecurityCurrencyMaxHistoryquoteData projections, each containing a Security and its latest quote date
   */
  //@formatter:on
  @Query(value = """
      SELECT s as securityCurrency, MAX(h.date) AS date FROM Security s JOIN s.historyquoteList h
      WHERE s.retryHistoryLoad < ?1 AND s.idConnectorHistory IS NOT NULL GROUP BY h.idSecuritycurrency
      HAVING s.activeToDate > MAX(h.date) ORDER BY s.idSecuritycurrency""")
  List<SecurityCurrencyMaxHistoryquoteData<Security>> getMaxHistoryquoteWithConnector(short maxHistoryRetry);

  /**
   * Retrieves the latest history-quote date for each formula-derived security
   * (where idLinkSecuritycurrency is not null) that still has retry attempts
   * remaining.
   *
   * @param maxHistoryRetry maximum retryHistoryLoad value allowed
   * @return list of projections pairing each derived security with its most
   *         recent quote date
   */
  @Query(value = """
      SELECT s as securityCurrency, MAX(h.date) AS date FROM Security s JOIN s.historyquoteList h
      WHERE s.retryHistoryLoad < ?1 AND s.idLinkSecuritycurrency IS NOT NULL GROUP BY h.idSecuritycurrency
      HAVING s.activeToDate > MAX(h.date) ORDER BY s.idSecuritycurrency""")
  List<SecurityCurrencyMaxHistoryquoteData<Security>> getMaxHistoryquoteWithCalculation(short maxHistoryRetry);

  /**
   * Checks whether any transactions exist for the specified security.
   * <p>
   * Executes a native SQL EXISTS query to determine if at least one transaction
   * references the given security ID.
   *
   * @param idSecuritycurrency the ID of the security to check
   * @return true if one or more transactions exist for the security; false
   *         otherwise
   */
  @Query(value = "SELECT IF(EXISTS(SELECT * FROM transaction t WHERE t.id_securitycurrency=?1) = 1, 'true', 'false' ); ", nativeQuery = true)
  boolean hasSecurityTransaction(Integer idSecuritycurency);

  /**
   * Finds the latest history quote date for each active Security in the given
   * exchanges that has a configured history connector and whose retryHistoryLoad
   * is below the threshold.
   * <p>
   * Uses JPQL to select Security entities with retryHistoryLoad <
   * maxHistoryRetry, idConnectorHistory not null, and stockexchange ID in
   * idsStockexchange, grouping by security.
   *
   * @param maxHistoryRetry  maximum allowed retryHistoryLoad before exclusion
   * @param idsStockexchange list of stock exchange IDs to include
   * @return a list of projections containing each Security and its most recent
   *         quote date
   */
  @Query(nativeQuery = false)
  List<SecurityCurrencyMaxHistoryquoteData<Security>> getMaxHistoryquoteWithConnectorForExchange(short maxHistoryRetry,
      List<Integer> idsStockexchange);

  /**
   * A new payment of interest or dividend is expected: - Last payment with this
   * client plus the addition of days determined based on the frequency of
   * payments. Instruments with entries in the Dividend entity that have a payment
   * date are ignored.</br>
   * TODO First interest of a bond determined on the basis of the trading date
   * plus addition of the frequency.</br>
   */
  @Query(nativeQuery = true)
  List<CheckSecurityTransIntegrity> getPossibleMissingDivInterestByFrequency();

  /**
   * If an instrument was held during the period of a dividend payment, the
   * dividend entity is used to determine whether there is a corresponding
   * dividend transaction. The payment date of the dividend is used.
   *
   *
   * @param daysLookBack            Number of days in the past that are checked
   *                                from the current date.
   * @param acceptDaysAroundPayDate There may be certain differences between the
   *                                transaction date and the official dividend
   *                                payment date. This indicates the number of
   *                                days for this tolerance.
   */
  @Query(nativeQuery = true)
  List<CheckSecurityTransIntegrity> getPossibleMissingDividentsByDividendTable(int daysLookBack,
      int acceptDaysAroundPayDate);

  /**
   * Retrieves securities that are still held by tenants but have become inactive
   * (active_to_date before today) and have not yet triggered a notification.
   *
   * @return a list of CheckSecurityTransIntegrity projection
   */
  @Query(nativeQuery = true)
  List<CheckSecurityTransIntegrity> getHoldingsOfInactiveSecurties();

  /**
   * Lists securities eligible for assignment to an algorithmic asset‐class that
   * are not yet part of the specified algo_assetclass_security and match the
   * tenant’s private flag.
   *
   * @param idTenantPrivate          tenant-private ID filter (nullable)
   * @param idAlgoAssetclassSecurity the algorithmic asset‐class ID
   * @return a list of Securities available for the given algo_assetclass
   */
  @Query(nativeQuery = true)
  List<Security> getUnusedSecurityForAlgo(Integer idTenantPrivate, Integer idAlgoAssetclassSecurity);

  /**
   * Streams flat projections of historyquote quality metrics grouped by
   * connector. Each projection includes connector ID, stockexchange name and ID,
   * category type, special investment instrument, counts of securities, active
   * securities, and aggregated create_type counts, plus average
   * qualityPercentage.
   *
   * @return a stream of IHistoryquoteQualityFlat projections for connector-level
   *         quality
   */
  @Query(nativeQuery = true)
  Stream<IHistoryquoteQualityFlat> getHistoryquoteQualityConnectorFlat();

  /**
   * Streams flat projections of historyquote quality metrics grouped by
   * stockexchange. Each projection includes stockexchange ID and name, connector
   * ID, category type, special investment instrument, counts of securities,
   * active securities, aggregated create_type counts, plus average
   * qualityPercentage.
   *
   * @return a stream of IHistoryquoteQualityFlat projections for exchange-level
   *         quality
   */
  @Query(nativeQuery = true)
  Stream<IHistoryquoteQualityFlat> getHistoryquoteQualityStockexchangeFlat();

  /**
   * Streams security‐currency tooltip data for pending task_data_change entries.
   * Each projection contains the securitycurrency ID and a display tooltip (name,
   * ticker, ISIN or from/to pair).
   *
   * @return a stream of IdSecurityCurrencyPairInfo projections for task
   *         notifications
   */
  @Query(nativeQuery = true)
  Stream<IdSecurityCurrencyPairInfo> getAllTaskDataChangeSecurityCurrencyPairInfoWithId();

  /**
   * Retrieves detailed historyquote quality entries for securities matching the
   * given connector, exchange, assetclass category, and special investment
   * instrument.
   *
   * @param idConnectorHistory          connector identifier
   * @param idStockexchange             stock exchange ID
   * @param categoryType                asset class category type
   * @param specialInvestmentInstrument special investment instrument code
   * @return a list of IHistoryquoteQualityWithSecurityProp projections with
   *         security properties
   */
  @Query(nativeQuery = true)
  List<IHistoryquoteQualityWithSecurityProp> getHistoryquoteQualityByIds(String idConnectorHistory,
      Integer idStockexchange, Byte categoryType, Byte specialInvestmentInstrument);

  /**
   * Retrieves load formulas and link IDs for derived securities based on a parent
   * security link.
   *
   * @param idLinkSecuritycurrency the parent securitycurrency ID
   * @return a list of IFormulaSecurityLoad projections with split/formula data
   */
  @Query(nativeQuery = true)
  List<IFormulaSecurityLoad> getBySecurityDerivedLinkByIdSecurityLink(Integer idLinkSecuritycurrency);

  /**
   * Retrieves year-end close price and annual dividend sum per year for the given
   * security.
   *
   * @param idSecurity the securitycurrency ID
   * @return a list of SecurityYearClose projections with date (year-end),
   *         securityClose, and yearDiv
   */
  @Query(nativeQuery = true)
  List<SecurityYearClose> getSecurityYearCloseDivSum(Integer idSecurity);

  /**
   * Retrieves year-end close price, annual dividend sum, and corresponding
   * currency-pair close for the given security and currency pair.
   *
   * @param idSecurity     the securitycurrency ID for dividends
   * @param idCurrencypair the currency-pair ID for currencyClose
   * @return a list of SecurityYearClose projections with date, securityClose,
   *         yearDiv, and currencyClose
   */
  @Query(nativeQuery = true)
  List<SecurityYearClose> getSecurityYearDivSumCurrencyClose(Integer idSecurity, Integer idCurrencypair);

  /**
   * Counts all working and non-functioning historical price data connectors,
   * grouped by connector. Security and currency pair connectors are taken into
   * account. Only securities after a certain date are taken into account when
   * counting non-functioning ones.
   *
   * @param dateBackAfter    Securities are taken into account for the count of
   *                         non-functioning ones if their most recent historical
   *                         price data is younger than this date.
   * @param retry            Instruments are included in the count of
   *                         non-functioning instruments if the repetition counter
   *                         is equal to or greater than this value.
   * @param percentageFailed The result set contains the connector if this
   *                         percentage value of non-functioning connectors
   *                         exceeds this value.
   * @return a list of MonitorFailedConnector
   */
  @Query(nativeQuery = true)
  List<MonitorFailedConnector> getFailedHistoryConnector(LocalDate dateBackAfter, int retry, int percentageFailed);

  /**
   * Counts all working and non-functioning intraday connectors. Both securities
   * and currency pairs are taken into account.
   *
   * @param dateBackOrRetry  In addition to checking the repeat counter overrun,
   *                         the date of the most recent update can also be
   *                         checked. The date given here must be older than that
   *                         of the instrument, otherwise it will be evaluated as
   *                         non-functioning.
   * @param retry            Instruments are included in the count of
   *                         non-functioning instruments if the repetition counter
   *                         is equal to or greater than this value.
   * @param percentageFailed The result set contains the connector if this
   *                         percentage value of non-functioning connectors
   *                         exceeds this value.
   */
  @Query(nativeQuery = true)
  List<MonitorFailedConnector> getFailedIntradayConnector(LocalDate dateBackOrRetry, int retry, int percentageFailed);

  @Override
  void calcGainLossBasedOnDateOrNewestPrice(List<SecurityPositionSummary> securitycurrencyPositionSummary,
      Date untilDate);

  public static class SplitAdjustedHistoryquotesResult {
    public SplitAdjustedHistoryquotes sah;
    public Integer addDaysForNextAttempt;

    public SplitAdjustedHistoryquotesResult(SplitAdjustedHistoryquotes sah, Integer addDaysForNextAttempt) {
      this.sah = sah;
      this.addDaysForNextAttempt = addDaysForNextAttempt;
    }

  }

  public enum SplitAdjustedHistoryquotes {
    // It cannot be determined whether the historical data have been adjusted.
    NOT_DETCTABLE,
    // Probably, the historical data of the last split is correctly mapped in the
    // persistence.
    ADJUSTED_WITH_CONNECTOR,
    // The connector provides adjusted data in the persistence they were not
    // adjusted. All historical data must be reloaded.
    ADJUSTED_NOT_LOADED
  }

  //@formatter:off
  /**
   * Projection interface representing security transaction integrity alerts for notification purposes.
   * <p>
   * Instances are returned by queries that identify:
   * <ul>
   *   <li>Securities with missing dividend or interest transactions beyond expected frequency,</li>
   *   <li>Dividend records without corresponding transaction entries within a tolerance window,</li>
   *   <li>Active holdings of securities that have become inactive.</li>
   * </ul>
   * Each projection includes the user to notify, locale preferences, security details, and the date on which
   * the alert should be marked.
   */
  //@formatter:on
  public static interface CheckSecurityTransIntegrity {

    /**
     * The user ID to whom the integrity alert applies.
     *
     * @return the identifier of the user
     */
    int getIdUser();

    /**
     * The user’s locale string, used for formatting messages or dates.
     *
     * @return the locale code (e.g., "en_US")
     */
    String getLocaleStr();

    /**
     * The currency code of the security in question.
     *
     * @return the ISO currency code (e.g., "USD")
     */
    String getCurrency();

    /**
     * The identifier of the securitycurrency entity being checked.
     *
     * @return the securitycurrency ID
     */
    int getIdSecuritycurrency();

    /**
     * The human-readable name of the security.
     *
     * @return the security name
     */
    String getName();

    /**
     * The date on which the integrity alert should be marked or triggered.
     *
     * @return the notification date
     */
    LocalDate getMarkDate();
  }

  /**
   * Projection interface for monitoring the performance of historical data
   * connectors.
   * <p>
   * Instances represent aggregated statistics per connector, including total
   * requests, failures, and computed failure percentage.
   */
  public static interface MonitorFailedConnector {

    /**
     * The identifier (name or key) of the data connector.
     *
     * @return the connector name
     */
    String getConnector();

    /**
     * The total number of connector attempts (currency-pair + security) considered.
     *
     * @return the total attempt count
     */
    int getTotal();

    /**
     * The number of attempts that failed (retry threshold exceeded).
     *
     * @return the count of failed attempts
     */
    int getFailed();

    /**
     * The percentage of failed attempts, rounded to the nearest whole number.
     *
     * @return the failure rate as a percentage (0–100)
     */
    int getPercentageFailed();
  }

}
