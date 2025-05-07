package grafioschtrader.repository;

import java.util.List;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.common.UpdateQuery;
import grafiosch.rest.UpdateCreateDeleteWithTenantJpaRepository;
import grafioschtrader.entities.Watchlist;
import grafioschtrader.reportviews.securitycurrency.SecuritycurrencyUDFGroup.IUDFEntityValues;

public interface WatchlistJpaRepository extends JpaRepository<Watchlist, Integer>, WatchlistJpaRepositoryCustom,
    UpdateCreateDeleteWithTenantJpaRepository<Watchlist> {

  Watchlist findByName(String name);

  List<Watchlist> findByIdTenantOrderByName(Integer idTenant);

  Watchlist findByIdWatchlistAndIdTenant(Integer idWatchlist, Integer idTenant);

  @Query(value = "SELECT count(s) FROM Watchlist w JOIN w.securitycurrencyList s WHERE w.idTenant = ?1 AND w.idWatchlist= ?2")
  Long countPostionsInWatchlist(Integer idTenant, Integer idWatchlist);

  @Query(value = "SELECT count(s) FROM Watchlist w JOIN w.securitycurrencyList s WHERE w.idTenant = ?1")
  Long countPostionsInAllWatchlistByIdTenant(Integer idTenant);

  @Query(value = "SELECT w.idWatchlist FROM Watchlist w JOIN w.securitycurrencyList s WHERE w.idTenant = ?1 AND s.idSecuritycurrency = ?2")
  List<Integer> getAllWatchlistsWithSecurityByIdSecuritycurrency(Integer idTenant, Integer idSecuritycurrency);

  @Query(value = "SELECT count(w) FROM Watchlist w WHERE w.idTenant = ?1 AND w.idWatchlist IN (?2)")
  int getWatchlistByTenantAndWatchlistIds(Integer idTenant, Integer[] watchlistsIds);

  @UpdateQuery(value = "UPDATE watchlist_sec_cur wsc SET wsc.id_watchlist = ?2 WHERE wsc.id_watchlist = ?1 AND wsc.id_securitycurrency = ?3", nativeQuery = true)
  void moveUpdateSecuritycurrency(Integer idWatchlistSource, Integer idWatchlistTarget, Integer idSecuritycurrency);

  @Transactional
  @Modifying
  int deleteByIdWatchlistAndIdTenant(Integer idWatchlist, Integer idTenant);

  //@formatter:off
  /**
   * Retrieves each watchlist of the given tenant along with a flag indicating whether it contains any securities.
   *
   * @param idTenant the tenant ID whose watchlists are checked
   * @return a list of Object[] where each array contains:
   *         <ul>
   *           <li>index 0: watchlist ID ({@code Integer})</li>
   *           <li>index 1: has_security flag ({@code Integer}, 1 if non-empty, 0 otherwise)</li>
   *         </ul>
   */
  //@formatter:on
  @Query(nativeQuery = true)
  List<Object[]> watchlistsOfTenantHasSecurity(Integer idTenant);

  /**
   * Adds to the watchlist all securities and currency pairs whose intraday data
   * has not been refreshed within the specified interval and have remaining retry
   * quota.
   *
   * @param idWatchlist       the watchlist ID to insert into
   * @param daysSinceLastWork maximum days since last successful intraday load
   * @param retryIntraCounter minimum retry count threshold for intraday loads
   */
  @Query(nativeQuery = true)
  void addInstrumentsWithIntradayPriceDataTrouble(Integer idWatchlist, Integer daysSinceLastWork,
      Short retryIntraCounter);

  /**
   * Adds to the watchlist all securities and currency pairs whose historical data
   * has not been refreshed within the specified interval and have remaining retry
   * quota.
   *
   * @param idWatchlist            the watchlist ID to insert into
   * @param daysSinceLastWork      maximum days since last historical load
   * @param retryHistoricalCounter minimum retry count threshold for historical
   *                               loads
   */
  @Query(nativeQuery = true)
  void addInstrumentsWithHistoricalPriceDataTrouble(Integer idWatchlist, Integer daysSinceLastWork,
      Short retryHistoricalCounter);

  /**
   * Retrieves user-defined data (UDF) JSON values for the given watchlist, user,
   * and entity types.
   *
   * @param idWatchlist the watchlist ID to query
   * @param idUser      the user ID who owns the UDF entries
   * @param entities    array of entity names to filter by (e.g., security)
   * @return a list of {@link IUDFEntityValues} projections containing entity ID
   *         and JSON values
   */
  @Query(nativeQuery = true)
  List<IUDFEntityValues> getUDFByIdWatchlistAndIdUserAndEntity(Integer idWatchlist, Integer idUser, String[] entities);

  /**
   * Returns the Security ids from used Securities. They can be referenced by a
   * watchlist or a transaction. The watchlist in of the parameter is excluded. It
   * checks if a Security could removed from the watchlist and aftewards delete
   * without constraints violations.
   * 
   * @param idWatchlist the watchlist ID to inspect
   * @return an array of security-currency IDs matching the criteria
   */
  @Query(nativeQuery = true)
  int[] watchlistSecuritiesHasTransactionOrOtherWatchlist(Integer idWatchlist);

  /**
   * Deletes specified securities from a watchlist belonging to a tenant.
   *
   * @param idTenant    the tenant ID who owns the watchlist
   * @param idWatchlist the watchlist ID to delete from
   * @param ids         list of security or currency IDs to remove
   * @return the number of rows deleted
   */
  @Transactional
  @Modifying
  @Query(nativeQuery = true)
  int deleteByIdTenantAndWatchlistAndIds(Integer idTenant, Integer idWatchlist, List<Integer> ids);

  /**
   * Retrieves currency-pair security IDs that are referenced by portfolios, cash
   * accounts, transactions, or other watchlists—but not by this watchlist. Such
   * instruments can no longer be permanently deleted. As they are already
   * referenced.
   *
   * @param idWatchlist the watchlist ID to exclude
   * @return an array of currency-pair security IDs
   */
  @Query(nativeQuery = true)
  int[] watchlistCurrencypairsHasReferencesButThisWatchlist(Integer idWatchlist);

  /**
   * Retrieves security IDs in the watchlist that have at least one split or dividend record.
   *
   * @param idWatchlist the watchlist ID to inspect
   * @return a set of security-currency IDs with splits or dividends
   */
  @Query(nativeQuery = true)
  Set<Integer> hasSplitOrDividendByWatchlist(Integer idWatchlist);

  /**
   * Retrieves security IDs in the watchlist that have open or closed transactions
   * under the specified tenant.
   *
   * @param idWatchlist the watchlist ID to inspect
   * @param idTenant    the tenant ID to filter transactions
   * @return an array of security-currency IDs with any transaction for this tenant
   */
  @Query(nativeQuery = true)
  int[] watchlistSecuritiesHasOpenOrClosedTransactionForThisTenant(Integer idWatchlist, Integer idTenant);

}
