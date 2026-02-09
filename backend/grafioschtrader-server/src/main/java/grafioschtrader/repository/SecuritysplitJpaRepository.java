package grafioschtrader.repository;

import java.util.Date;
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
   * Calculates the cumulative split factor for a security after the specified date.
   * <p>
   * Uses logarithmic summation to multiply individual split ratios:
   * <ul>
   *   <li>to_factor / from_factor for each split</li>
   *   <li>only includes splits with split_date &gt; the given date</li>
   * </ul>
   * Rounds the result to one decimal place.
   *
   * @param idSecuritycurrency the ID of the security‐currency for which the factor is computed
   * @param date               the exclusive lower bound for split_date
   * @return the cumulative split factor as a {@link Double}, or {@code null} if no splits match
   */
  //@formatter:on
  @Query(nativeQuery = true)
  Double getSplitFactorAfterThanEqualDate(Integer idSecuritycurrency, Date date);

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
