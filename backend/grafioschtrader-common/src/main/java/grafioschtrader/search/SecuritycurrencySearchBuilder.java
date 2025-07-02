package grafioschtrader.search;

import java.util.ArrayList;
import java.util.List;

import grafioschtrader.entities.CorrelationSet;
import grafioschtrader.entities.CorrelationSet_;
import grafioschtrader.entities.Securitycurrency;
import grafioschtrader.entities.Securitycurrency_;
import grafioschtrader.entities.Watchlist;
import grafioschtrader.entities.Watchlist_;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.ListJoin;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

/**
 * Base class for building JPA Criteria API queries for securities and currency pairs.
 * <p>
 * This class provides common utility methods for creating subqueries that are used to exclude securities/currency pairs
 * that are already present in watchlists or correlation sets. This is typically used when searching for instruments to
 * add to these collections.
 * </p>
 * <p>
 * The subqueries created by this class are designed to work with NOT EXISTS clauses to filter out items that are
 * already in the target collection.
 * </p>
 */
public class SecuritycurrencySearchBuilder {

  /** ID of the watchlist to exclude currency pairs from (optional) */
  protected final Integer idWatchlist;

  /** ID of the correlation set to exclude currency pairs from (optional) */
  protected final Integer idCorrelationSet;

  /** Search criteria container with various filter options */
  protected final SecuritycurrencySearch securitycurrencySearch;

  public SecuritycurrencySearchBuilder(Integer idWatchlist, Integer idCorrelationSet,
      SecuritycurrencySearch securitycurrencySearch) {
    this.idWatchlist = idWatchlist;
    this.idCorrelationSet = idCorrelationSet;
    this.securitycurrencySearch = securitycurrencySearch;
  }

  /**
   * Creates a subquery to check if a security or currency pair exists in a specific watchlist.
   * <p>
   * This method builds a subquery that can be used with NOT EXISTS to exclude securities/currency pairs that are
   * already present in the specified watchlist. This is commonly used when searching for instruments to add to a
   * watchlist.
   * </p>
   * 
   * @param <T>         the type of security/currency extending {@link Securitycurrency}
   * @param idWatchlist the ID of the watchlist to check against
   * @param security    the root entity in the main query (Security or Currencypair)
   * @param query       the main criteria query
   * @param builder     the criteria builder for constructing predicates
   * @return a subquery that returns watchlists containing the specified security/currency
   */
  protected <T extends Securitycurrency<?>> Subquery<Watchlist> subQueryForAddingWatchlist(final Integer idWatchlist,
      final Root<T> security, final CriteriaQuery<?> query, final CriteriaBuilder builder) {
    final Subquery<Watchlist> watchlistSub = query.subquery(Watchlist.class);
    final Root<Watchlist> watchtlist = watchlistSub.from(Watchlist.class);
    watchlistSub.select(watchtlist);
    final List<Predicate> subQueryPredicates = new ArrayList<>();
    subQueryPredicates.add(builder.equal(watchtlist.get(Watchlist_.idWatchlist), idWatchlist));
    final ListJoin<Watchlist, Securitycurrency<?>> joinSecuritycurreny = watchtlist
        .join(Watchlist_.securitycurrencyList);
    subQueryPredicates.add(builder.equal(joinSecuritycurreny.get(Securitycurrency_.idSecuritycurrency),
        security.get(Securitycurrency_.idSecuritycurrency)));
    watchlistSub.where(subQueryPredicates.toArray(new Predicate[] {}));

    return watchlistSub;
  }

  /**
   * Creates a subquery to check if a security or currency pair exists in a specific correlation set.
   * <p>
   * This method builds a subquery that can be used with NOT EXISTS to exclude securities/currency pairs that are
   * already present in the specified correlation set. This is commonly used when searching for instruments to add to a
   * correlation analysis.
   * </p>
   * 
   * @param <T>              the type of security/currency extending {@link Securitycurrency}
   * @param idCorrelationSet the ID of the correlation set to check against
   * @param security         the root entity in the main query (Security or Currencypair)
   * @param query            the main criteria query
   * @param builder          the criteria builder for constructing predicates
   * @return a subquery that returns correlation sets containing the specified security/currency
   */
  protected <T extends Securitycurrency<?>> Subquery<CorrelationSet> subQueryForAddingCorrelationSet(
      final Integer idCorrelationSet, final Root<T> security, final CriteriaQuery<?> query,
      final CriteriaBuilder builder) {
    final Subquery<CorrelationSet> correlationSetSub = query.subquery(CorrelationSet.class);
    final Root<CorrelationSet> correlationSet = correlationSetSub.from(CorrelationSet.class);
    correlationSetSub.select(correlationSet);
    final List<Predicate> subQueryPredicates = new ArrayList<>();
    subQueryPredicates.add(builder.equal(correlationSet.get(CorrelationSet_.idCorrelationSet), idCorrelationSet));
    final ListJoin<CorrelationSet, Securitycurrency<?>> joinSecuritycurreny = correlationSet
        .join(CorrelationSet_.securitycurrencyList);
    subQueryPredicates.add(builder.equal(joinSecuritycurreny.get(Securitycurrency_.idSecuritycurrency),
        security.get(Securitycurrency_.idSecuritycurrency)));
    correlationSetSub.where(subQueryPredicates.toArray(new Predicate[] {}));

    return correlationSetSub;
  }

}
