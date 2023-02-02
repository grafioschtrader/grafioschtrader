package grafioschtrader.repository;

import java.util.List;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.entities.Watchlist;
import grafioschtrader.rest.UpdateCreateDeleteWithTenantJpaRepository;

public interface WatchlistJpaRepository extends JpaRepository<Watchlist, Integer>, WatchlistJapRepositoryCustom,
    UpdateCreateDeleteWithTenantJpaRepository<Watchlist> {

  Watchlist findByName(String name);

  List<Watchlist> findByIdTenantOrderByName(Integer idTenant);

  Watchlist findByIdWatchlistAndIdTenant(Integer idWatchlist, Integer idTenant);

  @Transactional
  @Modifying
  @Query(nativeQuery = true)
  int deleteByIdTenantAndWatchlistAndIds(Integer idTenant, Integer idWatchlist, List<Integer> ids);

  @Query(value = "SELECT count(s) FROM Watchlist w JOIN w.securitycurrencyList s WHERE w.idTenant = ?1 AND w.idWatchlist= ?2")
  Long countPostionsInWatchlist(Integer idTenant, Integer idWatchlist);

  @Query(value = "SELECT count(s) FROM Watchlist w JOIN w.securitycurrencyList s WHERE w.idTenant = ?1")
  Long countPostionsInAllWatchlistByIdTenant(Integer idTenant);

  @Query(value = "SELECT w.idWatchlist FROM Watchlist w JOIN w.securitycurrencyList s WHERE w.idTenant = ?1 AND s.idSecuritycurrency = ?2")
  List<Integer> getAllWatchlistsWithSecurityByIdSecuritycurrency(Integer idTenant, Integer idSecuritycurrency);

  @Transactional
  @Modifying
  int deleteByIdWatchlistAndIdTenant(Integer idWatchlist, Integer idTenant);

  @Query(nativeQuery = true)
  Set<Integer> hasSplitOrDividendByWatchlist(Integer idWatchlist);

  @Query(nativeQuery = true)
  List<Object[]> watchlistsOfTenantHasSecurity(Integer idTenant);

  /**
   * Returns the Security ids from used Securities. They can be referenced by a
   * watchlist or a transaction. The watchlist in of the parameter is excluded. It
   * checks if a Security could removed from the watchlist and aftewards delete
   * without constraints violations.
   *
   * @param idWatchlist
   * @return
   */
  @Query(nativeQuery = true)
  int[] watchlistSecuritiesHasTransactionOrOtherWatchlist(Integer idWatchlist);

  @Query(nativeQuery = true)
  int[] watchlistCurrencypairsHasReferencesButThisWatchlist(Integer idWatchlist);

  @Query(nativeQuery = true)
  int[] watchlistSecuritiesHasOpenOrClosedTransactionForThisTenant(Integer idWatchlist, Integer idTenant);

  @Query(value = "SELECT count(w) FROM Watchlist w WHERE w.idTenant = ?1 AND w.idWatchlist IN (?2)")
  int getWatchlistByTenantAndWatchlistIds(Integer idTenant, Integer[] watchlistsIds);

  @Query(value = "UPDATE watchlist_sec_cur wsc SET wsc.id_watchlist = ?2 WHERE wsc.id_watchlist = ?1 AND wsc.id_securitycurrency = ?3", nativeQuery = true)
  void moveUpdateSecuritycurrency(Integer idWatchlistSource, Integer idWatchlistTarget, Integer idSecuritycurrency);
}
