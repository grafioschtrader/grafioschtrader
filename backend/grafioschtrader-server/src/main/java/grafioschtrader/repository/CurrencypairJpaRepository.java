package grafioschtrader.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.EntityGraph.EntityGraphType;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.rest.UpdateCreateJpaRepository;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.projection.CurrencyCount;
import grafioschtrader.priceupdate.historyquote.SecurityCurrencyMaxHistoryquoteData;

public interface CurrencypairJpaRepository extends SecurityCurrencypairJpaRepository<Currencypair>,
    JpaSpecificationExecutor<Currencypair>, CurrencypairJpaRepositoryCustom, UpdateCreateJpaRepository<Currencypair> {

  /**
   * Returns the IDs of all currency pairs. Used by batch jobs that process one currency pair per transaction, so that
   * neither the entities nor their lazily loaded history quotes are kept in a persistence context spanning the whole
   * run.
   */
  @Query("SELECT c.idSecuritycurrency FROM Currencypair c")
  List<Integer> findAllIdSecuritycurrency();

  /**
   * Returns IDs of currency pairs configured to receive intraday prices via GTNet.
   */
  @Query("SELECT c.idSecuritycurrency FROM Currencypair c WHERE c.gtNetLastpriceRecv = true")
  Set<Integer> findIdsWithGtNetLastpriceRecv();

  /**
   * Returns IDs of currency pairs configured to send intraday prices via GTNet.
   */
  @Query("SELECT c.idSecuritycurrency FROM Currencypair c WHERE c.gtNetLastpriceSend = true")
  Set<Integer> findIdsWithGtNetLastpriceSend();

  /**
   * Returns IDs of currency pairs configured to receive historical prices via GTNet.
   */
  @Query("SELECT c.idSecuritycurrency FROM Currencypair c WHERE c.gtNetHistoricalRecv = true")
  Set<Integer> findIdsWithGtNetHistoricalRecv();

  /**
   * Returns IDs of currency pairs configured to send historical prices via GTNet.
   */
  @Query("SELECT c.idSecuritycurrency FROM Currencypair c WHERE c.gtNetHistoricalSend = true")
  Set<Integer> findIdsWithGtNetHistoricalSend();

  /**
   * Finds currency pairs modified after the given timestamp for GTNet sync.
   * Also includes currency pairs where gtNetLastModifiedTime is NULL (never synced before).
   */
  @Query("SELECT c FROM Currencypair c WHERE c.gtNetLastModifiedTime > ?1 OR c.gtNetLastModifiedTime IS NULL")
  List<Currencypair> findByGtNetLastModifiedTimeAfter(LocalDateTime timestamp);

  /**
   * Finds all currency pairs with GTNet send flags enabled (lastprice or historical).
   * Used for full recreation mode where all eligible instruments are synchronized.
   */
  @Query("SELECT c FROM Currencypair c WHERE c.gtNetLastpriceSend = true OR c.gtNetHistoricalSend = true")
  List<Currencypair> findAllWithGtNetSendEnabled();

  /**
   * Retrieves all currency pairs with supplier detail count for GTNet exchange configuration.
   */
  @Query("SELECT c, (SELECT COUNT(d) FROM GTNetSupplierDetail d WHERE d.idEntity = c.idSecuritycurrency) FROM Currencypair c ORDER BY c.fromCurrency, c.toCurrency")
  List<Object[]> findAllWithSupplierDetailCount();

  List<Currencypair> findByFromCurrency(String fromCurrency);

  Currencypair findByFromCurrencyAndToCurrency(String fromCurrency, String toCurrency);

  List<Currencypair> findByRetryIntraLoadLessThan(short retryLoad);

  @Override
  @EntityGraph(value = "graph.currency.historyquote", type = EntityGraphType.FETCH)
  Currencypair findByIdSecuritycurrency(Integer idSecuritycurrency);

  @Query(value = "SELECT c.from_currency FROM currencypair c WHERE c.to_currency = ?1", nativeQuery = true)
  Set<String> getFromCurrencyByToCurrency(String toCurrency);

  /**
   * Fetches currency pairs in the specified tenant’s watchlist that need an intraday reload (retryIntraLoad > 0) and
   * have a configured intraday connector.
   *
   * @param idTenant    the tenant ID
   * @param idWatchlist the watchlist ID
   * @return list of currency pairs pending intraday update
   */
  @Query(value = """
      SELECT c FROM Watchlist w JOIN w.securitycurrencyList c
      WHERE w.idTenant = ?1 AND w.idWatchlist = ?2 AND c.retryIntraLoad > 0
      AND c.fromCurrency IS NOT NULL AND c.idConnectorIntra IS NOT NULL""")
  List<Currencypair> findByIdTenantAndIdWatchlistWhenRetryIntraThan0(Integer idTenant, Integer idWatchlist);

  //@formatter:off
  /**
   * Retrieves the latest quote date for each currency pair in the specified watchlist
   * that is scheduled for a historical data reload (retryHistoryLoad > 0) and has an attached history connector.
   *
   * @param idTenant    the tenant ID owning the watchlist
   * @param idWatchlist the watchlist ID
   * @return a list of SecurityCurrencyMaxHistoryquoteData projections containing:
   *         <ul>
   *           <li>securityCurrency: the Currencypair entity</li>
   *           <li>date: the most recent quote date from historyquoteList</li>
   *         </ul>
   */
  //@formatter:on
  @Query(value = """
      SELECT c as securityCurrency, MAX(h.date) AS date FROM Watchlist w JOIN w.securitycurrencyList c JOIN c.historyquoteList h
      WHERE w.idTenant = ?1 AND w.idWatchlist = ?2 AND c.retryHistoryLoad > 0 AND c.fromCurrency IS NOT NULL
      AND c.idConnectorHistory IS NOT NULL GROUP BY c.idSecuritycurrency""")
  List<SecurityCurrencyMaxHistoryquoteData<Currencypair>> findWithConnectorByIdTenantAndIdWatchlistWhenRetryHistoryGreaterThan0(
      Integer idTenant, Integer idWatchlist);

  /**
   * Gel all used currency pairs in transactions. That means currency pairs between security cash accounts.
   */
  @Query(nativeQuery = true)
  List<Currencypair> getCurrencypairInTransactionByTenant(Integer idTenant);

  /**
   * Retrieves distinct security currencies that have been transacted by the specified tenant, excluding the given
   * currency.
   *
   * @param idTenant        the tenant ID whose transaction currencies are fetched
   * @param excludeCurrency the currency code to exclude from the results
   * @return a set of security currency codes transacted by the tenant, excluding excludeCurrency
   */
  @Query(nativeQuery = true)
  Set<String> getSecurityTransactionCurrenciesForTenantExclude(Integer idTenant, String excludeCurrency);

  /**
   * Retrieves the distinct currencies of all cash accounts of the specified tenant, excluding the given currency.
   * Together with {@link #getSecurityTransactionCurrenciesForTenantExclude(Integer, String)} this covers every currency
   * that has to be convertible into the tenant currency, which is what the holdings tables denormalise into their
   * currency pair columns.
   *
   * Named query: Currencypair.getCashaccountCurrenciesForTenantExclude
   *
   * @param idTenant        the tenant ID whose cash account currencies are fetched
   * @param excludeCurrency the currency code to exclude from the results, normally the tenant currency
   * @return a set of cash account currency codes of the tenant, excluding excludeCurrency
   */
  @Query(nativeQuery = true)
  Set<String> getCashaccountCurrenciesForTenantExclude(Integer idTenant, String excludeCurrency);

  /**
   * Retrieves distinct security currencies that have been transacted in the specified portfolio, excluding the given
   * currency.
   *
   * @param idPortfolio     the portfolio ID whose transaction currencies are fetched
   * @param excludeCurrency the currency code to exclude from the results
   * @return a set of security currency codes transacted in the portfolio, excluding excludeCurrency
   */
  @Query(nativeQuery = true)
  Set<String> getSecurityTransactionCurrenciesForPortfolioExclude(Integer idPortfolio, String excludCurrency);

  /**
   * Retrieves the distinct currencies of the cash accounts of the specified portfolio, excluding the given currency.
   * Together with {@link #getSecurityTransactionCurrenciesForPortfolioExclude(Integer, String)} this covers every
   * currency that has to be convertible into the portfolio currency, which is what the holdings tables denormalise into
   * their currency pair columns.
   *
   * Named query: Currencypair.getCashaccountCurrenciesForPortfolioExclude
   *
   * @param idPortfolio    the portfolio ID whose cash account currencies are fetched
   * @param excludCurrency the currency code to exclude from the results, normally the portfolio currency
   * @return a set of cash account currency codes of the portfolio, excluding excludCurrency
   */
  @Query(nativeQuery = true)
  Set<String> getCashaccountCurrenciesForPortfolioExclude(Integer idPortfolio, String excludCurrency);

  /**
   * Retrieves all currency pairs that have been used in transactions for the specified portfolio (and scoped by
   * tenant).
   *
   * @param idPortfolio the portfolio ID whose transaction currency pairs are fetched
   * @param idTenant    the tenant ID to which the portfolio belongs
   * @return a list of Currencypair entities used in the portfolio’s transactions
   */
  @Query(nativeQuery = true)
  List<Currencypair> getCurrencypairInTransactionByPortfolioId(Integer idPortfolio, Integer idTenant);

  /**
   * Retrieves the IDs of all currency pairs that have no historyquote records and have retry count below the global
   * retry threshold.
   *
   * @return a list of Currencypair IDs with empty historyquote and eligible for retry
   */
  @Query(nativeQuery = true)
  List<Integer> getAllIdOfEmptyHistorqute();

  //@formatter:off
  /**
   * Retrieves every distinct currency pair needed to convert assets into the tenant’s base currency.
   * <p>
   * This includes:
   * <ul>
   *   <li>Pairs converting each security’s native currency (from security transactions) to the tenant currency.</li>
   *   <li>Pairs converting each cash account’s currency to the tenant currency.</li>
   *   <li>Any currency pairs directly recorded in transaction entries.</li>
   * </ul>
   * Identity pairs (where from_currency == to_currency) are excluded.
   *
   * @param idTenant the tenant ID whose conversion currency pairs are fetched
   * @return a list of distinct Currencypair entities used across the tenant’s portfolios, cash accounts, and transactions
   */
   //@formatter:on
  @Query(nativeQuery = true)
  List<Currencypair> getAllCurrencypairsForTenantByTenant(Integer idTenant);

  //@formatter:off
  /**
   * Retrieves every distinct currency pair required to convert portfolio holdings into the portfolio’s base currency.
   * <p>
   * Combines:
   * <ul>
   *   <li><strong>Security context:</strong> for each security held in the portfolio (via transactions),
   *       converting security.currency to portfolio.currency.</li>
   *   <li><strong>Cash account context:</strong> for each cash account under the portfolio,
   *       converting cashaccount.currency to portfolio.currency.</li>
   * </ul>
   * Unions results from both contexts and returns matching Currencypair entities linked with their Securitycurrency metadata.
   *
   * @param idPortfolio the portfolio ID whose currency pairs are required for valuation
   * @return a list of distinct Currencypair entities used by the portfolio’s securities and cash accounts
   */
  //@formatter:on
  @Query(nativeQuery = true)
  List<Currencypair> getAllCurrencypairsForPortfolioByPortfolio(Integer idPortfolio);

  /**
   * Finds all currency pairs matching any concatenated "from+to" currency codes in the provided list.
   *
   * @param currencyPairConcat a list of concatenated currency codes (e.g., "USDCHF")
   * @return a list of matching Currencypair entities
   */
  @Query(nativeQuery = true)
  List<Currencypair> getPairsByFromAndToCurrency(List<String> currencyPairConcat);

  /**
   * Finds the currency pair entity for the given two currencies, in either direction.
   *
   * @param c1 the first currency code
   * @param c2 the second currency code
   * @return a list of matching Currencypair entities where (from=c1, to=c2) or (from=c2, to=c1)
   */
  @Query(nativeQuery = true)
  List<Currencypair> findByFromCurrencyAndToCurrencyOrToCurrencyAndFromCurrency(String c1, String c2);

  /**
   * For a tenant it gets all currency pairs its holdings need, without the currency pairs used in transactions. This is
   * the lookup map behind {@code id_currency_pair_tenant} / {@code id_currency_pair_portfolio} of
   * {@code hold_securityaccount_security} and {@code hold_cashaccount_balance}, so it returns all four conversion
   * directions those two columns can hold: security currency to tenant currency, cash account currency to tenant
   * currency, security currency to portfolio currency and cash account currency to portfolio currency. A pair whose two
   * currencies are equal is never returned, because such a holding is already held in the reporting currency.
   *
   * Named query: Currencypair.getAllCurrencypairsByTenantInPortfolioAndAccounts
   *
   * @param idTenant the tenant ID whose currency pairs are fetched
   * @return a list of Currencypair entities used within the tenant’s holdings
   */
  @Query(nativeQuery = true)
  List<Currencypair> getAllCurrencypairsByTenantInPortfolioAndAccounts(Integer idTenant);

  /**
   * Counts how many times each currency appears in the 'from' or 'to' side of currency pairs, limited to the given set
   * of currency codes.
   *
   * @param currencies a set of currency codes to count usage for
   * @return a list of CurrencyCount projections with currency and usage count
   */
  @Query(nativeQuery = true)
  List<CurrencyCount> countCurrencyGroupByCurrency(Set<String> currencies);

  //@formatter:off
  /**
  * Retrieves every currency pair needed to convert both security positions and cash balances
  * into the portfolio’s base currency for the specified portfolio.
  * <p>
  * This includes:
  * <ul>
  *   <li>Pairs from security transactions: converting each security’s native currency
  *       (security.currency) to the portfolio currency.</li>
  *   <li>Pairs from cash accounts: converting each cash account’s currency to the portfolio currency.</li>
  * </ul>
  * The method unions results from both contexts and returns distinct Currencypair entities
  * joined with their Securitycurrency metadata.
  *
  * @param idPortfolio the portfolio ID whose security and cash-account currency pairs are fetched
  * @return a list of distinct Currencypair entities used within the portfolio’s holdings
  */
  //@formatter:on
  @Query(nativeQuery = true)
  List<Currencypair> getAllCurrencypairsByPortfolioInPortfolioAndAccounts(Integer idPortfolio);

  /**
   * Retrieves the maximum historyquote date for each currency pair that has retryHistoryLoad below the given threshold
   * and a non-null connector history setting.
   *
   * @param maxHistoryRetry the maximum retryHistoryLoad value to consider
   * @return a list of SecurityCurrencyMaxHistoryquoteData projections containing the currency pair and its latest quote
   *         date
   */
  @Query(nativeQuery = false)
  List<SecurityCurrencyMaxHistoryquoteData<Currencypair>> getMaxHistoryquote(short maxHistoryRetry);

  /**
   * Finds GTNet-opted-in currency pairs currently in the GTNet fallback band — connector retry counter at or above its
   * configured cap (gt.history.retry) but below the absolute exhaustion cap (gt.history.retry + gt.gtnet.quote.retry).
   * Returns the pair and its most recent historyquote date (null if none yet), so the GTNet service knows where to
   * start the requested range.
   *
   * Named query: Currencypair.findRetryExhaustedGTNetEnabled
   *
   * @param connectorCap the connector retry cap (gt.history.retry); inclusive lower bound
   * @param absoluteCap  the absolute exhaustion cap (gt.history.retry + gt.gtnet.quote.retry); exclusive upper bound
   * @return projections pairing each eligible Currencypair with its latest historyquote date (or null if empty history)
   */
  @Query(nativeQuery = false)
  List<SecurityCurrencyMaxHistoryquoteData<Currencypair>> findRetryExhaustedGTNetEnabled(short connectorCap,
      short absoluteCap);

  //@formatter:off
  /**
   * Retrieves currency pairs whose cash‐account deposit holds are stale compared to market quotes.
   * <p>
   * Identifies pairs where a deposit or withdrawal (transaction_type ≤ DEPOSIT) occurred in a
   * cash account whose currency differs from the portfolio or tenant base currency, and where the
   * corresponding `hold_cashaccount_deposit.valid_timestamp` is earlier than the `historyquote.create_modify_time`.
   * <ul>
   *   <li><strong>Portfolio context:</strong> converts cash‐account currency to the portfolio currency.</li>
   *   <li><strong>Tenant context:</strong> converts cash‐account currency to the tenant currency.</li>
   * </ul>
   * These pairs require refreshing hold‐based cash valuations against the latest end‐of‐day rates.
   *
   * @return a list of Currencypair entities for which deposit hold records are out‐of‐date
   */
   //@formatter:on
  @Query(nativeQuery = true)
  List<Currencypair> getHoldCashaccountOutDatetedCurrencypairs();

  /**
   * Counts currency pairs where either the history or intraday connector field matches the given connector ID.
   *
   * @param connectorId the full connector ID (e.g., "gt.datafeed.myconnector")
   * @return the number of currency pairs referencing this connector
   */
  @Query("SELECT COUNT(c) FROM Currencypair c WHERE c.idConnectorHistory = ?1 OR c.idConnectorIntra = ?1")
  long countByAnyConnectorId(String connectorId);

  /**
   * Resets retry_history_load counter to zero for all currency pairs, optionally filtered by connector. Only affects
   * currency pairs where a history connector is configured and retry counter is greater than zero.
   *
   * Named query: Currencypair.resetRetryHistoryByConnector
   *
   * @param connectorId the full connector ID (e.g., "gt.datafeed.yahoo") to filter, or null to reset all connectors
   */
  @Transactional
  @Modifying
  @Query(nativeQuery = true)
  void resetRetryHistoryByConnector(String connectorId);

  /**
   * Resets retry_intra_load counter to zero for all currency pairs, optionally filtered by connector. Only affects
   * currency pairs where an intraday connector is configured and retry counter is greater than zero.
   *
   * Named query: Currencypair.resetRetryIntraByConnector
   *
   * @param connectorId the full connector ID (e.g., "gt.datafeed.yahoo") to filter, or null to reset all connectors
   */
  @Transactional
  @Modifying
  @Query(nativeQuery = true)
  void resetRetryIntraByConnector(String connectorId);

}
