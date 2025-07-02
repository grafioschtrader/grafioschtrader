package grafioschtrader.search;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Currencypair_;
import grafioschtrader.entities.Securitycurrency_;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

/**
 * JPA Criteria API Specification implementation for searching currency pairs.
 * <p>
 * This class builds dynamic queries to search for currency pairs based on various criteria
 * while optionally excluding those already present in watchlists or correlation sets.
 * It implements the Spring Data JPA Specification pattern to provide type-safe and
 * composable query building for {@link Currencypair} entities.
 * </p>
 * <p>
 * The search supports filtering by:
 * <ul>
 *   <li>Exact currency code matching (either from or to currency)</li>
 *   <li>Partial name matching in currency codes</li>
 *   <li>Data connector IDs for historical and intraday price feeds</li>
 *   <li>Exclusion of items already in specific watchlists or correlation sets</li>
 * </ul>
 * </p>
 */ 
public class CurrencyPairSearchBuilder extends SecuritycurrencySearchBuilder implements Specification<Currencypair> {

  private static final long serialVersionUID = 1L;

  /**
   * Constructs a new CurrencySearchBuilder with the specified search parameters.
   * 
   * @param idWatchlist the ID of a watchlist to exclude currency pairs from, or null if not filtering by watchlist
   * @param idCorrelationSet the ID of a correlation set to exclude currency pairs from, or null if not filtering by correlation set
   * @param securitycurrencySearch the search criteria containing various filter options (must not be null)
   */
  public CurrencyPairSearchBuilder(Integer idWatchlist, Integer idCorrelationSet,
      SecuritycurrencySearch securitycurrencySearch) {
    super(idWatchlist, idCorrelationSet, securitycurrencySearch);
  }

  /**
   * Builds the JPA Criteria API predicate for searching currency pairs.
   * <p>
   * This method constructs a compound predicate based on the provided search criteria:
   * </p>
   * <ol>
   *   <li><strong>Exclusion filters:</strong> Excludes currency pairs already present in the specified watchlist or correlation set</li>
   *   <li><strong>Currency matching:</strong> Searches by exact currency code or partial name match in either from or to currency</li>
   *   <li><strong>Connector filtering:</strong> Filters by historical or intraday data connector IDs</li>
   * </ol>
   * <p>
   * The query uses DISTINCT to prevent duplicate results when joining with collection relationships.
   * </p>
   */ 
  @Override
  public Predicate toPredicate(final Root<Currencypair> currencypair, final CriteriaQuery<?> query,
      final CriteriaBuilder builder) {
    final List<Predicate> mainPredicates = new ArrayList<>();
    if (idWatchlist != null) {
      mainPredicates
          .add(builder.not(builder.exists(subQueryForAddingWatchlist(idWatchlist, currencypair, query, builder))));
    }
    if (idCorrelationSet != null) {
      mainPredicates.add(
          builder.not(builder.exists(subQueryForAddingCorrelationSet(idCorrelationSet, currencypair, query, builder))));
    }
    query.distinct(true);
    if (securitycurrencySearch.getCurrency() != null) {
      final Predicate pFrom = builder.equal(currencypair.get(Currencypair_.fromCurrency),
          securitycurrencySearch.getCurrency());
      final Predicate pTo = builder.equal(currencypair.get(Currencypair_.toCurrency),
          securitycurrencySearch.getCurrency());
      mainPredicates.add(builder.or(pFrom, pTo));
    } else if (securitycurrencySearch.getName() != null) {
      final Predicate pFrom = builder.like(currencypair.get(Currencypair_.fromCurrency),
          "%" + securitycurrencySearch.getName().toUpperCase() + "%");
      final Predicate pTo = builder.like(currencypair.get(Currencypair_.toCurrency),
          "%" + securitycurrencySearch.getName().toUpperCase() + "%");
      mainPredicates.add(builder.or(pFrom, pTo));
    }
    if (securitycurrencySearch.getIdConnectorHistory() != null) {
      mainPredicates.add(builder.and(builder.like(currencypair.get(Securitycurrency_.idConnectorHistory),
          securitycurrencySearch.getIdConnectorHistory())));
    }

    if (securitycurrencySearch.getIdConnectorIntra() != null) {
      mainPredicates.add(builder.and(builder.like(currencypair.get(Securitycurrency_.idConnectorIntra),
          securitycurrencySearch.getIdConnectorIntra())));
    }
    final Predicate[] predicatesArray = new Predicate[mainPredicates.size()];
    return builder.and(mainPredicates.toArray(predicatesArray));
  }
}
