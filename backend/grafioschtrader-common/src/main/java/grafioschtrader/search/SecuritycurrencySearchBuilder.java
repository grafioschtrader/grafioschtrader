package grafioschtrader.search;

import java.util.ArrayList;
import java.util.List;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.ListJoin;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.criteria.Subquery;

import grafioschtrader.entities.Securitycurrency;
import grafioschtrader.entities.Securitycurrency_;
import grafioschtrader.entities.Watchlist;
import grafioschtrader.entities.Watchlist_;

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

}
