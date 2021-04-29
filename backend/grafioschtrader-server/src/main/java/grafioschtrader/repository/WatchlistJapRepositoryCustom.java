package grafioschtrader.repository;

import java.util.List;

import grafioschtrader.dto.TenantLimit;
import grafioschtrader.entities.Watchlist;
import grafioschtrader.reportviews.securitycurrency.SecuritycurrencyLists;
import grafioschtrader.search.SecuritycurrencySearch;

public interface WatchlistJapRepositoryCustom extends BaseRepositoryCustom<Watchlist> {

  TenantLimit[] getSecuritiesCurrenciesWachlistLimits(Integer idWatchlist);

  Watchlist addSecuritycurrenciesToWatchlist(Integer idWatchlist, SecuritycurrencyLists securitycurrencyLists);

  int removeMultipleFromWatchlist(Integer idWatchlist, final List<Integer> idsSecuritycurrency);

  Watchlist removeSecurityFromWatchlistAndDelete(Integer idWatchlist, Integer idSecuritycurrency);

  Watchlist removeCurrencypairFromWatchlistAndDelete(Integer idWatchlist, Integer idSecuritycurrency);

  Watchlist removeSecurityFromWatchlist(Integer idWatchlist, Integer idSecuritycurrency);

  Watchlist removeCurrencypairFromWatchlist(Integer idWatchlist, Integer idSecuritycurrency);

  Watchlist removeAllSecurityCurrency(Integer idWatchlist);

  int delEntityWithTenant(Integer id, Integer idTenant);

  SecuritycurrencyLists searchByCriteria(Integer idWatchlist, SecuritycurrencySearch securitycurrencySearch);

  SecuritycurrencyLists tryUpToIntradayDataWhenRetryIntraLoadGreaterThan0(Integer idWatchlist);

  SecuritycurrencyLists tryUpToDateHistoricalDataWhenRetryHistoryLoadGreaterThan0(Integer idWatchlist);

  Boolean moveSecuritycurrency(Integer idWatchlistSource, Integer idWatchlistTarget, Integer idSecuritycurrency);

}
