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

public class SecuritycurrencySearchBuilder {

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
