package grafioschtrader.repository;

import java.util.List;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import grafioschtrader.entities.Securitysplit;

public interface SecuritysplitJpaRepository
    extends JpaRepository<Securitysplit, Integer>, SecuritysplitJpaRepositoryCustom {

  long countByIdSecuritycurrency(Integer idSecuritycurrency);

  Long deleteByIdSecuritycurrency(Integer idSecuritycurrency);

  Long deleteByIdSecuritycurrencyAndCreateType(Integer idSecuritycurrency, byte createType);

  List<Securitysplit> findByIdSecuritycurrencyOrderBySplitDateAsc(Integer idSecuritycurrency);

  List<Securitysplit> findByIdSecuritycurrencyInOrderByIdSecuritycurrencyAscSplitDateAsc(Set<Integer> idSecurity);

  /**
   * Retrieves all split events for securities in the specified watchlist.
   *
   * @param idWatchlist the ID of the watchlist whose securities’ split events are fetched
   * @return a {@link List} of {@link Securitysplit} entities for the given watchlist
   */
  @Query(nativeQuery = true)
  List<Securitysplit> getByIdWatchlist(Integer idWatchlist);

  //@formatter:off
  /**
   * Retrieves all distinct split events for securities held in any portfolio of the specified tenant.
   * <p>
   * Combines splits via:
   * <ul>
   *   <li>portfolio → securitycashaccount → transaction → security → securitysplit</li>
   * </ul>
   * Filters by tenant ID and orders by security‐currency ID and split date ascending.
   *
   * @param idTenant the ID of the tenant whose portfolios’ security splits are fetched
   * @return a {@link List} of distinct {@link Securitysplit} entities for the given tenant
   */
  //@formatter:on
  @Query(nativeQuery = true)
  List<Securitysplit> getByIdTenant(Integer idTenant);

//@formatter:off
  /**
   * Retrieves all distinct split events for securities that have transactions on the specified security account.
   * <p>
   * Joins:
   * <ul>
   *   <li>securityaccount (alias sa)</li>
   *   <li>transaction (alias t)</li>
   *   <li>securitysplit (alias sp)</li>
   * </ul>
   * Filters by security account ID and orders by security‐currency ID and split date ascending.
   *
   * @param idSecuritycashaccount the ID of the security account whose related splits are fetched
   * @return a {@link List} of distinct {@link Securitysplit} entities for the given security account
   */
  //@formatter:on
  @Query(nativeQuery = true)
  List<Securitysplit> getByIdSecuritycashaccount(Integer idSecuritycashaccount);
}
