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

public class CurrencySearchBuilder extends SecuritycurrencySearchBuilder implements Specification<Currencypair> {

  private static final long serialVersionUID = 1L;
  final Integer idWatchlist;
  final Integer idCorrelationSet;
  final SecuritycurrencySearch securitycurrencySearch;

  public CurrencySearchBuilder(Integer idWatchlist, Integer idCorrelationSet,
      SecuritycurrencySearch securitycurrencySearch) {
    super();
    this.idWatchlist = idWatchlist;
    this.idCorrelationSet = idCorrelationSet;
    this.securitycurrencySearch = securitycurrencySearch;
  }

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
