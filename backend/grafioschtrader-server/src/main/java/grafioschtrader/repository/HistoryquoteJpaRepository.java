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

  /**
   * For user interface, do not show history quotes which fills day holes.
   *
   * @param idSecuritycurrency
   * @return
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

  @Query(nativeQuery = false)
  List<HistoryquoteDateClose> findDateCloseByIdSecuritycurrencyAndCreateTypeFalseOrderByDateAsc(
      Integer idSecuritycurrency);

  /**
   * Retrieves completeness and quality metrics for a security’s historical
   * quotes, including date range, missing days, weekend anomalies, and
   * creation-type breakdown.
   */
  @Query(nativeQuery = true)
  IHistoryquoteQuality getMissingsDaysCountByIdSecurity(Integer idSecuritycurrency);

  @Query(nativeQuery = true)
  IHistoryquoteQuality getMissingsDaysCountByIdCurrency(Integer idSecuritycurrency);

  @Query(nativeQuery = true)
  List<ISecuritycurrencyIdDateClose> getYoungestHistorquoteForSecuritycurrencyByWatchlist(Integer idWatchlist);

  /**
   * Retrieves the latest end-of-day quote for each security in the specified
   * watchlist, scoped to the current tenant to enforce data isolation. - Uses
   * id_tenant to prevent tenants from accessing each other’s data. - Finds the
   * maximum quote date per security via a subquery joining watchlist, its
   * entries, and historyquote. - Joins back to historyquote to fetch the complete
   * record for each security’s max date. - Returns results ordered by security ID
   * in ascending order.
   */
  @Query(nativeQuery = true)
  List<Historyquote> getYoungestFeedHistorquoteForSecuritycurrencyByWatchlist(Integer idWatchlist, Integer idTenant);

  /*-
   * Returns the total number of times a given security is referenced by the current tenant,
   * summing counts from both transactions and watchlist entries.
   * - Filters transactions by id_tenant = ?1 and id_securitycurrency = ?2.
   * - Filters watchlist entries by id_tenant = ?1 via watchlist and watchlist_sec_cur joins for the same security.
   * - Uses UNION ALL to combine both counts and SUM() to produce a single total.
   */
  @Query(nativeQuery = true)
  Integer countSecuritycurrencyForHistoryquoteAccess(Integer idTenant, Integer idSecuritycurrency);

  /*-
   * Fetches complete quote records for a security and its related links within a given date range,
   * but only on dates where all expected linked entities have quotes.
   * - Unions historyquote entries from security_derived_link and security.id_link_securitycurrency sources
   *   filtered by id_securitycurrency = ?1 and date BETWEEN ?2 AND ?3.
   * - Identifies dates with exactly ?4 linked quotes via a grouped subquery and HAVING clause.
   * - Joins back to include only those dates in the final result set.
   * - Returns all historyquote columns ordered by date and id_securitycurrency.
   */
  @Query(nativeQuery = true)
  List<Historyquote> getHistoryquoteFromDerivedLinksByIdSecurityAndDate(Integer idSecurity, Date fromDate, Date toDate,
      int requiredDayCount);

  @Query(nativeQuery = true)
  List<IMinMaxDateHistoryquote> getMinMaxDateByIdSecuritycurrencyIds(List<Integer> idsSecuritycurrency);

  /**
   * Return of historical prices based on the trading calendar of the security.
   * This means that a closed price can be zero if it does not exist. Dates up to
   * the active date or the current date minus 1 day are taken into account.
   * Prices are taken into account up to the trading calendar tracking date at the
   * latest.
   *
   * @param idSecuritycurrency
   * @return
   */
  @Query(nativeQuery = true)
  List<IDateAndClose> getClosedAndMissingHistoryquoteByIdSecurity(Integer idSecuritycurrency);

  List<IDateAndClose> getByIdSecuritycurrencyAndCreateTypeNotOrderByDate(Integer idSecuritycurrency, byte createType);

  List<Historyquote> getByIdSecuritycurrencyOrderByDate(Integer idSecuritycurrency);

  @Query(nativeQuery = true)
  List<ISecuritycurrencyIdDateClose> getCertainOrOlderDayInHistorquoteForSecuritycurrencyByWatchlist(
      Integer idWatchlist, Date date);

  @Query(nativeQuery = true)
  List<ISecuritycurrencyIdDateClose> getIdDateCloseByIdsAndDate(@Param("ids") List<Integer> idSecuritycurrencies,
      @Param("date") Date date);

  /**
   * Determines all historical year-end exchange rates for the foreign currencies
   * used by the customer. In addition, the historical year-end rates of the
   * securities held are also determined.
   *
   * @param idTenant
   * @return
   */
  @Query(nativeQuery = true)
  List<Historyquote> getSecuritycurrencyHistoryEndOfYearsByIdTenant(Integer idTenant);

  @Query(nativeQuery = true)
  List<Object[]> getUsedCurrencyHistoryquotesByIdTenantAndDate(Integer idTenant, Date date);

  /**
   * Return exchange rate for dividend transactions depending on tenant and main
   * currency. This include all exchange rates from history quotes with
   * transactions on foreign cash account.
   *
   * @param idTenant
   * @param mainCurrency
   * @return
   */
  @Query(nativeQuery = true)
  List<Object[]> getHistoryquoteCurrenciesForDividendsByIdTenantAndMainCurrency(Integer idTenant, String mainCurrency);

  /**
   * Return exchange rate for buy/sell transactions depending on tenant and main
   * currency. This include all exchange rates from history quotes with
   * transactions on foreign cash account.
   *
   * @param idTenant
   * @param mainCurrency
   * @return
   */
  @Query(nativeQuery = true)
  List<Object[]> getHistoryquoteCurrenciesForBuyAndSellByIdTenantAndMainCurrency(Integer idTenant, String mainCurrency);

  /**
   * Returns the end-of-day exchange rate to the main currency for all
   * transactions of a tenant in foreign currencies. This include all exchange
   * rates from history quotes with transactions on foreign cash account.
   *
   * @param idTenant
   * @param mainCurrency
   * @return
   */
  @Query(nativeQuery = true)
  List<Object[]> getHistoryquoteCurrenciesForIntrFeeBuySellDivByIdTenantAndMainCurrency(Integer idTenant,
      String mainCurrency);

  /**
   * For every transaction of a tenant gets the corresponding exchange rate to the
   * main currency. It includes all transactions, that means transaction with
   * security or no security involved.
   *
   * @param idTenant
   * @return
   */
  @Query(nativeQuery = true)
  List<Object[]> getHistoryquotesForAllForeignTransactionsByIdTenant(Integer idTenant);

  /*-
   * Retrieves end-of-day exchange rates for cross-currency transactions in the specified portfolio.
   * - Scopes to the portfolio’s security cash accounts and their linked cash accounts.
   * - Filters to only those accounts where the cash account currency differs from the portfolio currency.
   * - Determines the currency pair from the cash account currency to the portfolio currency.
   * - Joins historyquote on the currency pair and transaction date to fetch the closing rate.
   * - Returns one row per transaction containing:
   *     • date (h.date)
   *     • source currency (cp.from_currency)
   *     • exchange rate (h.close)
   */
  @Query(nativeQuery = true)
  List<Object[]> getHistoryquotesForAllForeignTransactionsByIdPortfolio(Integer idPortfolio);

  /*-
   * Retrieves the end-of-day exchange rate for every transaction in the specified cash account (?1).
   * - Joins portfolio → cash account → transaction → securitycurrency → security → currencypair 
   *   to determine each security’s currency pair relative to the portfolio.
   * - Joins historyquote on transaction date (t.tt_date = h.date) to fetch the closing rate.
   * - Returns one row per transaction with:
   *     • date (h.date)
   *     • source currency code (cp.from_currency)
   *     • exchange rate (h.close)
   */
  @Query(nativeQuery = true)
  List<Object[]> getHistoryquotesForAllForeignTransactionsByIdSecuritycashAccount(Integer idSecurityaccount);

  /**
   * For every transaction of a certain Tenant and Security combination gets the
   * corresponding exchange rate to the main currency.
   *
   * @param idTenant
   * @param idSecuritycurrency
   * @return
   */
  @Query(nativeQuery = true)
  List<Object[]> findByIdTenantAndIdSecurityFoCuHistoryquotes(Integer idTenant, Integer idSecuritycurrency);

  /*-
   * Retrieves end-of-day conversion rates for a specific security within a portfolio.
   * - Traverses portfolio → cash account → transaction → securitycurrency → security relationships.
   * - Determines the currency pair from the security’s currency to the portfolio’s currency.
   * - Joins historyquote on the currency pair ID and transaction date (tt_date) to fetch the close rate.
   * - Applies filters for portfolio ID (?1) and securitycurrency ID (?2).
   * - Returns the quote date, source currency code, and conversion rate (close price).
   */
  @Query(nativeQuery = true)
  List<Object[]> findByIdPortfolioAndIdSecurityFoCuHistoryquotes(Integer idPortfolio, Integer idSecuritycurrency);

 /*-
  * For each transaction of the specified security (?2) in the given cash account (?1),
  * retrieves the end-of-day exchange rate from the security’s currency to the portfolio’s currency.
  * - Joins portfolio → cash account → transaction → securitycurrency → security → currencypair 
  *   to identify the correct currency pair.
  * - Joins historyquote on the transaction date (t.tt_date = h.date) to get the close price.
  * - Returns one row per transaction containing:
  *     • date (h.date)
  *     • source currency code (cp.from_currency)
  *     • conversion rate (h.close)
  */
  @Query(nativeQuery = true)
  List<Object[]> findByIdSecurityaccountAndIdSecurityFoCuHistoryquotes(Integer idSecuritycashAccount,
      Integer idSecuritycurrency);

  /**
   * Return of all missing dates of the EOD for a security. The missing dates are
   * determined via the index referenced by the stock exchange.
   *
   * @param idSecuritycurrencyIndex
   * @param idSecuritycurrency
   * @return
   */
  @Query(nativeQuery = true)
  List<Date> getMissingEODForSecurityByUpdCalendarIndex(Integer idSecuritycurrencyIndex, Integer idSecuritycurrency);

  public interface SecurityCurrencyIdAndDate {
    Integer getIdSecuritycurrency();

    Date getDate();
  }

}
