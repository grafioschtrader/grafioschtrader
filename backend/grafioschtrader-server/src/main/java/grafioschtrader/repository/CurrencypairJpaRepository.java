package grafioschtrader.repository;

import java.util.List;
import java.util.Set;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.EntityGraph.EntityGraphType;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.projection.CurrencyCount;
import grafioschtrader.priceupdate.historyquote.SecurityCurrencyMaxHistoryquoteData;
import grafioschtrader.rest.UpdateCreateJpaRepository;

public interface CurrencypairJpaRepository extends SecurityCurrencypairJpaRepository<Currencypair>,
    JpaSpecificationExecutor<Currencypair>, CurrencypairJpaRepositoryCustom, UpdateCreateJpaRepository<Currencypair> {

  List<Currencypair> findByFromCurrency(String fromCurrency);

  Currencypair findByFromCurrencyAndToCurrency(String fromCurrency, String toCurrency);

  @Query(nativeQuery = true)
  List<Currencypair> findByFromCurrencyAndToCurrencyOrToCurrencyAndFromCurrency(String c1, String c2);

  @Query(nativeQuery = true)
  List<Currencypair> getPairsByFromAndToCurrency(List<String> currencyPairConcat);

  @Query(nativeQuery = true)
  List<CurrencyCount> countCurrencyGroupByCurrency(Set<String> currencies);

  @Query(value = "SELECT c.from_currency FROM currencypair c WHERE c.to_currency = ?1", nativeQuery = true)
  Set<String> getFromCurrencyByToCurrency(String toCurrency);

  @Query(nativeQuery = true)
  Set<String> getSecurityTransactionCurrenciesForTenantExclude(Integer idTenant, String excludeCurrency);

  @Query(nativeQuery = true)
  Set<String> getSecurityTransactionCurrenciesForPortfolioExclude(Integer idPortfolio, String excludCurrency);

  @EntityGraph(value = "graph.currency.historyquote", type = EntityGraphType.FETCH)
  Currencypair findByIdSecuritycurrency(Integer idSecuritycurrency);

  @Query(nativeQuery = false)
  List<SecurityCurrencyMaxHistoryquoteData<Currencypair>> getMaxHistoryquote(short maxHistoryRetry);

  @Query(value ="""
                 SELECT c FROM Watchlist w JOIN w.securitycurrencyList c 
                 WHERE w.idTenant = ?1 AND w.idWatchlist = ?2 AND c.retryIntraLoad > 0 
                 AND c.fromCurrency IS NOT NULL AND c.idConnectorIntra IS NOT NULL""")
  List<Currencypair> findByIdTenantAndIdWatchlistWhenRetryIntraThan0(Integer idTenant, Integer idWatchlist);

  /**
   * Only currencies without a single history quote are ignored by this query.
   *
   * @param idTenant
   * @param idWatchlist
   * @return
   */
  @Query(value = """
      SELECT c as securityCurrency, MAX(h.date) AS date FROM Watchlist w JOIN w.securitycurrencyList c JOIN c.historyquoteList h 
      WHERE w.idTenant = ?1 AND w.idWatchlist = ?2 AND c.retryHistoryLoad > 0 AND c.fromCurrency IS NOT NULL 
      AND c.idConnectorHistory IS NOT NULL GROUP BY c.idSecuritycurrency""")
  List<SecurityCurrencyMaxHistoryquoteData<Currencypair>> findWithConnectorByIdTenantAndIdWatchlistWhenRetryHistoryGreaterThan0(
      Integer idTenant, Integer idWatchlist);

  /**
   * Gel all used currency pairs in transactions. That means currency pairs
   * between security cash accounts.
   *
   * @param idTenant
   * @return
   */
  @Query(nativeQuery = true)
  List<Currencypair> getCurrencypairInTransactionByTenant(Integer idTenant);

  @Query(nativeQuery = true)
  List<Currencypair> getCurrencypairInTransactionByPortfolioId(Integer idPortfolio, Integer idTenant);

  @Query(nativeQuery = true)
  List<Currencypair> getAllUsedCurrencypairs();

  @Query(nativeQuery = true)
  List<Integer> getAllIdOfEmptyHistorqute();

  /**
   * For a tenant gets all used existing currency pairs of all accounts,
   * securities and transaction.
   *
   * @param idTenant
   * @return
   */
  @Query(nativeQuery = true)
  List<Currencypair> getAllCurrencypairsForTenantByTenant(Integer idTenant);

  @Query(nativeQuery = true)
  List<Currencypair> getAllCurrencypairsForPortfolioByPortfolio(Integer idPortfolio);

  /**
   * For a tenant it gets all used currency pairs of all accounts and securities,
   * without the currency pairs used in transactions. The main currency is taken
   * from the tenant.
   *
   * TODO it does not work correctly for HoldCashaccountBalanceJpaRepositoryImpl
   *
   * @param idTenant
   * @return
   */
  @Query(nativeQuery = true)
  List<Currencypair> getAllCurrencypairsByTenantInPortfolioAndAccounts(Integer idTenant);

  @Query(nativeQuery = true)
  List<Currencypair> getAllCurrencypairsByPortfolioInPortfolioAndAccounts(Integer idPortfolio);

  @Query(nativeQuery = true)
  List<Currencypair> getHoldCashaccountOutDatetedCurrencypairs();

}
