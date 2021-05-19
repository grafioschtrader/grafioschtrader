package grafioschtrader.search;

import java.util.ArrayList;
import java.util.List;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import org.springframework.data.jpa.domain.Specification;

import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Currencypair_;

public class CurrencySearchBuilder extends SecuritycurrencySearchBuilder implements Specification<Currencypair> {

  private static final long serialVersionUID = 1L;
  final Integer idWatchlist;
  final SecuritycurrencySearch securitycurrencySearch;

  public CurrencySearchBuilder(Integer idWatchlist, SecuritycurrencySearch securitycurrencySearch) {
    super();
    this.idWatchlist = idWatchlist;
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

    query.distinct(true);
    if (securitycurrencySearch.currency != null) {
      final Predicate pFrom = builder.equal(currencypair.get(Currencypair_.fromCurrency),
          securitycurrencySearch.currency);
      final Predicate pTo = builder.equal(currencypair.get(Currencypair_.toCurrency), securitycurrencySearch.currency);
      mainPredicates.add(builder.or(pFrom, pTo));
    } else if (securitycurrencySearch.name != null) {
      final Predicate pFrom = builder.like(currencypair.get(Currencypair_.fromCurrency),
          "%" + securitycurrencySearch.name.toUpperCase() + "%");
      final Predicate pTo = builder.like(currencypair.get(Currencypair_.toCurrency),
          "%" + securitycurrencySearch.name.toUpperCase() + "%");
      mainPredicates.add(builder.or(pFrom, pTo));
    }
    
    if (securitycurrencySearch.idConnectorHistory != null) {
      mainPredicates.add(builder.and(
          builder.like(currencypair.get(Currencypair_.idConnectorHistory), securitycurrencySearch.idConnectorHistory)));
    }
    
    if (securitycurrencySearch.idConnectorIntra != null) {
      mainPredicates.add(builder.and(
          builder.like(currencypair.get(Currencypair_.idConnectorIntra), securitycurrencySearch.idConnectorIntra)));
    }
    

    final Predicate[] predicatesArray = new Predicate[mainPredicates.size()];
    return builder.and(mainPredicates.toArray(predicatesArray));

  }
}
