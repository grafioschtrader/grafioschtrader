package grafioschtrader.repository;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import grafiosch.common.UpdateQuery;
import grafiosch.rest.UpdateCreateJpaRepository;
import grafioschtrader.dto.HistoryquoteDateClose;
import grafioschtrader.dto.IDateAndClose;
import grafioschtrader.dto.IHistoryquoteQuality;
import grafioschtrader.dto.IMinMaxDateHistoryquote;
import grafioschtrader.dto.ISecuritycurrencyIdDateClose;
import grafioschtrader.entities.Historyquote;
import jakarta.transaction.Transactional;

public interface HistoryquoteJpaRepository extends JpaRepository<Historyquote, Integer>,
    HistoryquoteJpaRepositoryCustom, UpdateCreateJpaRepository<Historyquote> {

  void deleteByIdSecuritycurrency(Integer idSecuritycurrency);

  void deleteByIdSecuritycurrencyAndDate(Integer idSecuritycurrency, Date date);

  int deleteByIdSecuritycurrencyAndCreateType(Integer idSecuritycurrency, byte createType);

  @Transactional
  int deleteByIdSecuritycurrencyAndDateGreaterThanEqual(Integer idSecuritycurrency, Date date);

  Optional<Historyquote> findByIdSecuritycurrencyAndDate(Integer idSecuritycurrency, Date date);

  List<Historyquote> findByIdSecuritycurrencyOrderByDateAsc(Integer idSecuritycurrency);

  List<SecurityCurrencyIdAndDate> findByIdSecuritycurrency(Integer idSecuritycurrency);

  List<Historyquote> findByIdSecuritycurrencyAndDateBetweenOrderByDate(Integer idSecuritycurrency, Date fromDate,
      Date toDate);

  List<Historyquote> findByIdSecuritycurrencyAndDateGreaterThanOrderByDateAsc(Integer idSecuritycurrency, Date date,
      Pageable pageable);

  void removeByIdSecuritycurrencyAndCreateType(Integer idSecuritycurrency, byte createType);

  List<IDateAndClose> getByIdSecuritycurrencyAndCreateTypeNotOrderByDate(Integer idSecuritycurrency, byte createType);

  List<Historyquote> getByIdSecuritycurrencyOrderByDate(Integer idSecuritycurrency);

  /**
   * For user interface, do not show history quotes which fills day holes.
   */
  @Query(value = "SELECT h FROM Historyquote h WHERE h.idSecuritycurrency = ?1 AND h.createType <> 1 ORDER BY h.date ASC", nativeQuery = false)
  List<Historyquote> findByIdSecuritycurrencyAndCreateTypeFalseOrderByDateAsc(Integer idSecuritycurrency);

  @Query(value = "SELECT h FROM Historyquote h WHERE h.idSecuritycurrency = ?1 AND DAYOFWEEK(h.date) IN (1, 6, 7) ORDER BY h.date DESC", nativeQuery = false)
  List<Historyquote> findByIdFridayAndWeekend(Integer idSecuritycurrency);

  @Query(value = "SELECT h FROM Historyquote h WHERE h.idSecuritycurrency = ?1 AND h.createType <> 1 ORDER BY h.date DESC", nativeQuery = false)
  List<Historyquote> findByIdSecuritycurrencyAndCreateTypeFalseOrderByDateDesc(Integer idSecuritycurrency);

  @Query(value = "SELECT h FROM Historyquote h WHERE h.idSecuritycurrency = ?1 AND h.date = ?2 AND h.createType = 1", nativeQuery = false)
  Historyquote findByIdSecuritycurrencyAndDateAndCreateTypeFilled(Integer idSecuritycurrency, Date date);

  @UpdateQuery(value = "DELETE FROM historyquote WHERE id_securitycurrency = ?1", nativeQuery = true)
  void removeAllSecurityHistoryquote(Integer idSecuritycurrency);

  @Query(value = "SELECT MAX(date) FROM Historyquote h WHERE h.idSecuritycurrency = ?1")
  Date getMaxDateByIdSecurity(Integer idSecuritycurrency);

  /**
   * Finds historical quotes for a specific security or currency pair, returning them as a list of date and close price
   * pairs, ordered by date.
   * <p>
   * This method queries Historyquote entities associated with the provided idSecuritycurrency. It specifically excludes
   * quotes that were generated to fill dates which are not official trading days (e.g., weekend quotes for currencies,
   * identified by createType = 1, corresponding to HistoryquoteCreateType.FILLED_NON_TRADE_DAY).
   * </p>
   * <p>
   * The results are projected into HistoryquoteDateClose objects, each containing the date and the closing price. The
   * list is sorted chronologically by the quote date in ascending order. This method is useful for retrieving a clean
   * series of quotes that occurred on actual or assumed trading days, omitting placeholder quotes for non-trading days.
   * </p>
   *
   * @param idSecuritycurrency The unique identifier of the security or currency pair.
   * @return A list of HistoryquoteDateClose objects, each containing a date and the corresponding closing price,
   *         ordered by date. Returns an empty list if no matching quotes are found for the given ID and criteria.
   */
  @Query(nativeQuery = false)
  List<HistoryquoteDateClose> findDateCloseByIdSecuritycurrencyAndCreateTypeFalseOrderByDateAsc(
      Integer idSecuritycurrency);

  /**
   * Calculates and retrieves a data quality report for the End-of-Day (EOD) historical quotes of a specific security.
   * <p>
   * This report, returned as an IHistoryquoteQuality object, offers various metrics to evaluate the completeness and
   * integrity of the historical price data for the specified security. The metrics are determined by comparing actual
   * quotes against expected trading days. Expected trading days are derived from the security's active period (between
   * its active_from_date and active_to_date or yesterday, whichever is earlier) and considering the trading calendar of
   * its associated stock exchange (excluding non-trading days like weekends and specific holidays for that exchange).
   * </p>
   * <p>
   * Key metrics in the IHistoryquoteQuality object include the date range of available quotes, counts of missing data
   * points (at the start, end, and overall total), the total number of expected quotes, a data quality percentage,
   * details on any quotes recorded on non-trading days (e.g., Saturdays, Sundays), and a summary of quotes categorized
   * by their creation method (e.g., connector-created, manually imported, linearly filled). For a comprehensive
   * definition of all returned fields, please refer to the schema documentation of the IHistoryquoteQuality interface.
   * </p>
   *
   * @param idSecuritycurrency The unique identifier of the security for which the quality report is to be generated.
   * @return An IHistoryquoteQuality object detailing the historical data quality metrics for the specified security.
   *         May return null or an object with default values if the security is not found or if relevant data for
   *         calculation is absent.
   */
  @Query(nativeQuery = true)
  IHistoryquoteQuality getMissingsDaysCountByIdSecurity(Integer idSecuritycurrency);

  /**
   * Retrieves a data quality report for the historical quotes of a specific security or currency pair.
   * <p>
   * This report, encapsulated in an IHistoryquoteQuality object, provides various metrics to assess the completeness
   * and integrity of the historical price data. Metrics include details on the date range of available quotes, counts
   * of missing data points against expected trading days, a quality percentage, information on quotes falling on
   * non-trading days, and a breakdown of quotes by their creation/modification type. The calculations are based on data
   * up to the day before the current date. For a detailed description of each metric, refer to the schema documentation
   * of the IHistoryquoteQuality interface.
   * </p>
   *
   * @param idSecuritycurrency The unique identifier of the security or currency pair.
   * @return An IHistoryquoteQuality object containing the detailed quality metrics. This may return null or an object
   *         with default/zero values if no relevant data is found for the specified ID.
   */
  @Query(nativeQuery = true)
  IHistoryquoteQuality getMissingsDaysCountByIdCurrency(Integer idSecuritycurrency);

  /**
   * Retrieves the most recent (youngest) historical quote for each security or currency pair associated with a specific
   * watchlist.
   * <p>
   * The query identifies all securities/currencies in the given watchlist, finds the maximum (latest) date for each
   * from their historical quotes, and then returns the security/currency ID, that latest date, and the corresponding
   * closing price.
   * </p>
   *
   * @param idWatchlist The ID of the watchlist.
   * @return A list of ISecuritycurrencyIdDateClose objects, each containing the ID of the security/currency, the date
   *         of its youngest quote, and the closing price on that date. Returns an empty list if the watchlist has no
   *         instruments or no historical quotes are found for them.
   */
  @Query(nativeQuery = true)
  List<ISecuritycurrencyIdDateClose> getYoungestHistorquoteForSecuritycurrencyByWatchlist(Integer idWatchlist);

  //@formatter:off
  /**
   * Retrieves the latest end-of-day quote for each security in the specified watchlist, scoped to the current tenant to
   * enforce data isolation.
   * - Uses id_tenant to prevent tenants from accessing each other’s data.
   * - Finds the maximum quote date per security via a subquery joining watchlist, its entries, and historyquote.
   * - Joins back to historyquote to fetch the complete record for each security’s max date.
   * - Returns results ordered by security ID in ascending order.
   * @param The identifier of the watchlist containing the instruments.
   * @param idTenant
   */
  //@formatter:on
  @Query(nativeQuery = true)
  List<Historyquote> getYoungestFeedHistorquoteForSecuritycurrencyByWatchlist(Integer idWatchlist, Integer idTenant);

  //@formatter:off
  /**
   * Returns the total number of times a given security is referenced by the current tenant,
   * summing counts from both transactions and watchlist entries.
   * - Filters transactions by id_tenant = ?1 and id_securitycurrency = ?2.
   * - Filters watchlist entries by id_tenant = ?1 via watchlist and watchlist_sec_cur joins for the same security.
   * - Uses UNION ALL to combine both counts and SUM() to produce a single total.
   */
  //@formatter:on
  @Query(nativeQuery = true)
  Integer countSecuritycurrencyForHistoryquoteAccess(Integer idTenant, Integer idSecuritycurrency);

  //@formatter:off
  /**
   * Fetches complete quote records for a security and its related links within a given date range,
   * but only on dates where all expected linked entities have quotes.
   * - Unions historyquote entries from security_derived_link and security.id_link_securitycurrency sources
   *   filtered by id_securitycurrency = ?1 and date BETWEEN ?2 AND ?3.
   * - Identifies dates with exactly ?4 linked quotes via a grouped subquery and HAVING clause.
   * - Joins back to include only those dates in the final result set.
   * - Returns all historyquote columns ordered by date and id_securitycurrency.
   */
  //@formatter:on
  @Query(nativeQuery = true)
  List<Historyquote> getHistoryquoteFromDerivedLinksByIdSecurityAndDate(Integer idSecurity, Date fromDate, Date toDate,
      int requiredDayCount);

  /**
   * For a given list of security or currencypair identifiers, this method retrieves the earliest (minimum) and latest
   * (maximum) recorded dates from their historical quote data.
   * <p>
   * This can be useful for understanding the available date range of historical data for multiple instruments at once.
   *
   * @param idsSecuritycurrency A list of integer identifiers for securities or currencypairs.
   * @return A list of IMinMaxDateHistoryquote objects, ordered by security/currencypair identifier. Each object in the
   *         list represents an instrument from the input list for which historical data exists, and contains the
   *         instrument's identifier (idSecuritycurrency), its earliest historical quote date (minDate), and its latest
   *         historical quote date (maxDate). If an identifier from the input list has no historical quotes, it will not
   *         be present in the returned list. Returns an empty list if the input list is empty or no data is found for
   *         any of the provided IDs.
   */
  @Query(nativeQuery = true)
  List<IMinMaxDateHistoryquote> getMinMaxDateByIdSecuritycurrencyIds(List<Integer> idsSecuritycurrency);

  /**
   * Return of historical prices based on the trading calendar of the security. This means that a closed price can be
   * zero if it does not exist. Dates up to the active date or the current date minus 1 day are taken into account.
   * Prices are taken into account up to the trading calendar tracking date at the latest.
   *
   * @param idSecuritycurrency The identifier of the security
   * @return Date with close price
   */
  @Query(nativeQuery = true)
  List<IDateAndClose> getClosedAndMissingHistoryquoteByIdSecurity(Integer idSecuritycurrency);

  /**
   * For each security or currencypair in a specified watchlist, retrieves the most recent available closing price on or
   * before a given target date.
   * <p>
   * The method first attempts to find a closing price from daily historical quotes (historyquote table) that is on or
   * older than the target date. If the target date falls within a defined period in a separate price period table
   * (historyquote_period), that price is also considered, using the target date as its effective date. Results from
   * both sources are combined to provide the most relevant price.
   *
   * @param idWatchlist The identifier of the watchlist containing the instruments.
   * @param date        The target date for which to find the closing prices.
   * @return A list of ISecuritycurrencyIdDateClose objects. Each object contains the instrument's identifier, the date
   *         of the closing price (this will be the target date if the price is sourced from the period table, or the
   *         actual latest available date if sourced from daily historical quotes), and the closing price itself. An
   *         empty list is returned if the watchlist is empty or no relevant price data can be found for any of the
   *         instruments.
   */
  @Query(nativeQuery = true)
  List<ISecuritycurrencyIdDateClose> getCertainOrOlderDayInHistorquoteForSecuritycurrencyByWatchlist(
      Integer idWatchlist, Date date);

  //@formatter:off
  /**
   * Retrieves a list of security or currency pair closing prices (either daily or period-based) for a specified list of
   * security currency IDs and a given date.
   *
   * This query combines two data sources:
   * - From the "historyquote" table:
   * selects the latest available quote for each security currency ID that is less than or equal to the specified date.
   * - From the "historyquote_period" table:
   * selects the period-based price if the given date falls within the defined date range (from_date to to_date).
   *
   * The results from both sources are combined using a UNION.
   *
   * @param idSecuritycurrencies the list of security currency IDs for which to fetch the data
   * @param date                 the target date for retrieving the closing prices
   *
   * @return a list of results containing the security or currency ID, the applicable date, and the corresponding closing
   *         price
   */
  //@formatter:on
  @Query(nativeQuery = true)
  List<ISecuritycurrencyIdDateClose> getIdDateCloseByIdsAndDate(@Param("ids") List<Integer> idSecuritycurrencies,
      @Param("date") Date date);

  /**
   * Retrieves all historical year-end closing prices for securities and currency pairs that are relevant to a specified
   * tenant.
   * <p>
   * Relevance is determined by analyzing the tenant's portfolios, including traded securities, currencies of cash
   * accounts, and currency pairs used for various conversions (such as between portfolio currencies and the tenant's
   * main currency, or between security currencies and the tenant's currency). For each relevant instrument (security or
   * currencypair) and for each year it possesses historical data, the method fetches the historical quote corresponding
   * to the last available trading day of that year.
   *
   * @param idTenant The identifier of the tenant for whom year-end historical quotes are required.
   * @return A list of Historyquote objects. Each object encapsulates the complete year-end historical data (including
   *         closing price and date) for a security or currencypair pertinent to the tenant. The list will contain one
   *         entry per instrument for each year that historical data is available. Returns an empty list if no relevant
   *         instruments or their historical year-end data are found.
   */
  @Query(nativeQuery = true)
  List<Historyquote> getSecuritycurrencyHistoryEndOfYearsByIdTenant(Integer idTenant);

  /**
   * Retrieves distinct historical closing prices for currency pairs actively used by a specific tenant, for a given
   * date. These are typically pairs needed to convert amounts to the tenant's main currency or to the currencies of its
   * portfolios.
   * <p>
   * The method determines "used" currency pairs by analyzing transactions, security currencies, and cash account
   * currencies associated with the tenant's portfolios. It then fetches the EOD (End-of-Day) exchange rates for these
   * pairs for the specified date, ensuring the conversion target is either the tenant's primary currency or a
   * portfolio's currency.
   *
   * @param idTenant The identifier of the tenant for whom relevant currency exchange rates are required.
   * @param date     The specific date for which the historical exchange rates are to be fetched.
   * @return A list of object arrays. Each array contains three elements in order: first, a Date object representing the
   *         date of the historical quote (this will be the same as the input 'date'); second, a String indicating the
   *         'from' currency code of the identified currency pair; and third, a Double value for the closing EOD
   *         exchange rate of this pair on the specified date. Returns an empty list if no relevant currency pair data
   *         is found for the tenant and date.
   */
  @Query(nativeQuery = true)
  List<Object[]> getUsedCurrencyHistoryquotesByIdTenantAndDate(Integer idTenant, Date date);

  /**
   * Return exchange rate for dividend transactions depending on tenant and main currency. This include all exchange
   * rates from history quotes with transactions on foreign cash account.
   */
  @Query(nativeQuery = true)
  List<Object[]> getHistoryquoteCurrenciesForDividendsByIdTenantAndMainCurrency(Integer idTenant, String mainCurrency);

  /**
   * For a given tenant and a specified main currency, this method retrieves historical end-of-day (EOD) exchange rates.
   * These rates are specifically for ACCUMULATE (buy) and REDUCE (sell) transactions (as defined in the TransactionType
   * enum) that occurred in cash accounts denominated in a currency different from the specified main currency.
   * <p>
   * A cash account's currency is considered "foreign" if it does not match the provided main currency. The fetched
   * exchange rates represent the conversion from the foreign cash account's currency to the specified main currency,
   * effective as of the date of each relevant ACCUMULATE or REDUCE transaction. This is useful for valuing such
   * transactions in the context of the main currency.
   *
   * @param idTenant     The identifier of the tenant whose transactions are being analyzed.
   * @param mainCurrency The target currency (e.g., tenant's main reporting currency) against which foreign cash account
   *                     currencies are compared and to which they are converted.
   * @return A list of object arrays. Each array corresponds to an ACCUMULATE or REDUCE transaction in a foreign
   *         currency cash account and includes three elements in order: first, a Date object representing the
   *         transaction date (which is also the EOD exchange rate date); second, a String indicating the currency code
   *         of the foreign cash account involved in the transaction; and third, a Double value for the EOD exchange
   *         rate from that cash account's currency to the specified main currency. Returns an empty list if no such
   *         transactions or corresponding exchange rates are found.
   */
  @Query(nativeQuery = true)
  List<Object[]> getHistoryquoteCurrenciesForBuyAndSellByIdTenantAndMainCurrency(Integer idTenant, String mainCurrency);

  /**
   * For a given tenant and a specified main currency, this method retrieves historical end-of-day (EOD) exchange rates.
   * These rates are associated with specific transaction types including INTEREST_CASHACCOUNT, FEE, ACCUMULATE (buy),
   * REDUCE (sell), and DIVIDEND (corresponding to TransactionType enum values 2 through 6), which occurred in cash
   * accounts denominated in a currency different from the specified main currency.
   * <p>
   * A cash account's currency is considered "foreign" if it does not match the provided main currency. The fetched
   * exchange rates represent the conversion from the foreign cash account's currency to the specified main currency,
   * effective as of the date of each relevant transaction. This is useful for valuing these various transaction types
   * in the context of the main currency.
   *
   * @param idTenant     The identifier of the tenant whose transactions are being analyzed.
   * @param mainCurrency The target currency (e.g., tenant's main reporting currency) against which foreign cash account
   *                     currencies are compared and to which they are converted.
   * @return A list of object arrays. Each array corresponds to one of the specified transaction types
   *         (INTEREST_CASHACCOUNT, FEE, ACCUMULATE, REDUCE, DIVIDEND) in a foreign currency cash account and includes
   *         three elements in order: first, a Date object representing the transaction date (which is also the EOD
   *         exchange rate date); second, a String indicating the currency code of the foreign cash account involved in
   *         the transaction; and third, a Double value for the EOD exchange rate from that cash account's currency to
   *         the specified main currency. Returns an empty list if no such transactions or corresponding exchange rates
   *         are found.
   */
  @Query(nativeQuery = true)
  List<Object[]> getHistoryquoteCurrenciesForIntrFeeBuySellDivByIdTenantAndMainCurrency(Integer idTenant,
      String mainCurrency);

  /**
   * For every transaction occurring in a cash account whose currency differs from the tenant's main currency, this
   * method retrieves the corresponding historical end-of-day (EOD) exchange rate. The fetched rate facilitates the
   * conversion of amounts from the cash account's currency to the tenant's main currency, reflecting values as of each
   * transaction's date.
   * <p>
   * This is primarily used for valuing all transactions from foreign currency cash accounts in the tenant's primary
   * reference currency. It applies to any transaction type within such accounts.
   *
   * @param idTenant The identifier of the tenant for whom these transaction-related exchange rates are required.
   * @return A list of object arrays, where each array corresponds to a transaction in a foreign currency cash account.
   *         Each array contains three elements in order: first, a Date object representing the transaction date (which
   *         is also the EOD exchange rate date); second, a String indicating the currency code of the cash account (the
   *         'from' currency in the conversion); and third, a Double value for the EOD exchange rate from the cash
   *         account's currency to the tenant's main currency. Returns an empty list if no such transactions or
   *         corresponding exchange rates are found.
   */
  @Query(nativeQuery = true)
  List<Object[]> getHistoryquotesForAllForeignTransactionsByIdTenant(Integer idTenant);

  //@formatter:off
  /**
   * For a specified portfolio, this method retrieves historical end-of-day (EOD) exchange rates
   * for all transactions conducted in its associated cash accounts, but only when the cash account's
   * currency differs from the portfolio's main currency.
   * <p>
   * The exchange rates provided are for converting the cash account's currency (the 'from' currency)
   * to the portfolio's main currency (the 'to' currency), effective on the date of each transaction.
   * This helps in valuing transactions from foreign currency cash accounts in the portfolio's reference currency.
   * Retrieves end-of-day exchange rates for cross-currency transactions in the specified portfolio.
   * - Scopes to the portfolio’s security cash accounts and their linked cash accounts.
   * - Filters to only those accounts where the cash account currency differs from the portfolio currency.
   * - Determines the currency pair from the cash account currency to the portfolio currency.
   * - Joins historyquote on the currency pair and transaction date to fetch the closing rate.
   * - Returns one row per transaction containing:
   *     • date (h.date)
   *     • source currency (cp.from_currency)
   *     • exchange rate (h.close)
   * @param idPortfolio The identifier of the portfolio for which to fetch these exchange rates.
   * @return see above
   */
  //@formatter:on
  @Query(nativeQuery = true)
  List<Object[]> getHistoryquotesForAllForeignTransactionsByIdPortfolio(Integer idPortfolio);

  //@formatter:off
  /**
   * For every transaction within a specified cash account, this method retrieves the historical
   * end-of-day (EOD) exchange rate. This rate is used for converting the currency of the
   * security involved in the transaction to the main currency of the portfolio to which the
   * cash account belongs. The exchange rate corresponds to the date of each transaction.
   * <p>
   * This method is typically used to value transactions involving foreign securities in the
   * portfolio's reference currency at the time they occurred.
   * Retrieves the end-of-day exchange rate for every transaction in the specified cash account (?1).
   * - Joins portfolio → cash account → transaction → securitycurrency → security → currencypair
   *   to determine each security’s currency pair relative to the portfolio.
   * - Joins historyquote on transaction date (t.tt_date = h.date) to fetch the closing rate.
   * - Returns one row per transaction with:
   *     • date (h.date)
   *     • source currency code (cp.from_currency)
   *     • exchange rate (h.close)
   * @param idSecurityaccount The identifier of the cash account for which transaction-related exchange rates are being fetched.
   * @return see above
   */
  //@formatter:on
  @Query(nativeQuery = true)
  List<Object[]> getHistoryquotesForAllForeignTransactionsByIdSecuritycashAccount(Integer idSecurityaccount);

  /**
   * For all transactions associated with a specific security across all portfolios of a given tenant, this method
   * retrieves the historical end-of-day (EOD) exchange rate. The rate is for converting the security's currency to the
   * tenant's main currency, as of each transaction's date.
   * <p>
   * This is useful for analyzing transaction values in the tenant's primary reference currency.
   *
   * @param idTenant           The identifier of the tenant whose transactions are being considered.
   * @param idSecuritycurrency The identifier of the security involved in the transactions.
   * @return A list of object arrays, where each array represents a transaction and its corresponding exchange rate.
   *         Each array contains three elements in order: first, a Date object for the transaction date (also the EOD
   *         exchange rate date); second, a String for the security's currency code (the source currency for
   *         conversion); and third, a Double value for the EOD exchange rate from the security's currency to the
   *         tenant's main currency (representing the 'close' price from historical quotes for the relevant currency
   *         pair). An empty list is returned if no matching transactions or exchange rates are found.
   */
  @Query(nativeQuery = true)
  List<Object[]> findByIdTenantAndIdSecurityFoCuHistoryquotes(Integer idTenant, Integer idSecuritycurrency);

  /**
   * For each transaction involving a specific security within a given portfolio, this method retrieves the relevant
   * historical end-of-day (EOD) exchange rate. The rate provided is for converting the security's currency to the main
   * currency of the portfolio, as of the transaction's date.
   * <p>
   * This is typically used to determine the value of security transactions in the portfolio's reference currency at the
   * time they occurred.
   *
   * @param idPortfolio        The identifier of the portfolio containing the transactions.
   * @param idSecuritycurrency The identifier of the security for which the conversion rates for its transactions are
   *                           being sought.
   * @return A list of object arrays. Each array corresponds to a transaction of the specified security within the
   *         portfolio and contains three elements in order: first, a Date object representing the transaction date
   *         (which is also the EOD exchange rate date); second, a String for the security's currency code (the 'from'
   *         currency in the conversion); and third, a Double value for the EOD exchange rate from the security's
   *         currency to the portfolio's main currency (representing the 'close' price from historical quotes for the
   *         relevant currency pair). Returns an empty list if no such transactions or corresponding exchange rates are
   *         found.
   */

  @Query(nativeQuery = true)
  List<Object[]> findByIdPortfolioAndIdSecurityFoCuHistoryquotes(Integer idPortfolio, Integer idSecuritycurrency);

 //@formatter:off
 /**
  * For each transaction of the specified security (?2) in the given cash account (?1),
  * retrieves the end-of-day exchange rate from the security’s currency to the portfolio’s currency.
  * - Joins portfolio → cash account → transaction → securitycurrency → security → currencypair
  *   to identify the correct currency pair.
  * - Joins historyquote on the transaction date (t.tt_date = h.date) to get the close price.
  * - Returns one row per transaction containing:
  *     • date (h.date)
  *     • source currency code (cp.from_currency)
  *     • conversion rate (h.close)
  * @param idSecuritycashAccount The identifier of the cash account where the transactions occurred.
  * @param idSecuritycurrency The identifier of the security involved in the transactions.
  * @return see above
  */
  //@formatter:on
  @Query(nativeQuery = true)
  List<Object[]> findByIdSecurityaccountAndIdSecurityFoCuHistoryquotes(Integer idSecuritycashAccount,
      Integer idSecuritycurrency);

  /**
   * Calculates and returns a list of dates representing End-of-Day (EOD) records that are missing for a given security.
   * These missing dates are identified by comparing the EOD records of the specified security against those of a
   * reference calendar (typically a market index), considering only dates on or after the security's activation date.
   *
   * @param idSecuritycurrencyIndex The unique identifier of the security or currency pair used as the reference
   *                                calendar. EOD dates present for this reference are used to find gaps in the target
   *                                security's data.
   * @param idSecuritycurrency      The unique identifier of the security for which missing EOD dates are being sought.
   * @return A chronologically ordered list of Date objects. Each date in the list signifies an EOD record that exists
   *         for the reference calendar but is missing for the target security, on or after its activation date. Returns
   *         an empty list if no such discrepancies are found.
   */
  @Query(nativeQuery = true)
  List<Date> getMissingEODForSecurityByUpdCalendarIndex(Integer idSecuritycurrencyIndex, Integer idSecuritycurrency);

  public interface SecurityCurrencyIdAndDate {
    Integer getIdSecuritycurrency();

    Date getDate();
  }

}
