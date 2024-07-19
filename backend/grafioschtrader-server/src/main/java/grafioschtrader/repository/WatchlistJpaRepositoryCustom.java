package grafioschtrader.repository;

import java.util.List;

import grafioschtrader.dto.IntraHistoricalWatchlistProblem;
import grafioschtrader.dto.TenantLimit;
import grafioschtrader.entities.Watchlist;
import grafioschtrader.reportviews.securitycurrency.SecuritycurrencyLists;
import grafioschtrader.search.SecuritycurrencySearch;

public interface WatchlistJpaRepositoryCustom extends BaseRepositoryCustom<Watchlist> {

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

  Watchlist addInstrumentsWithPriceDataProblems(Integer idWatchlist, IntraHistoricalWatchlistProblem ihwp);

  SecuritycurrencyLists tryUpToDateHistoricalDataWhenRetryHistoryLoadGreaterThan0(Integer idWatchlist);

  Boolean moveSecuritycurrency(Integer idWatchlistSource, Integer idWatchlistTarget, Integer idSecuritycurrency);

  String getDataProviderResponseForUser(Integer idSecuritycurrency, boolean isIntraday, boolean isSecurity);
  
  String getDataProviderDivSplitResponseForUser(Integer idSecuritycurrency, boolean isDiv);

  String getDataProviderLinkForUser(Integer idSecuritycurrency, boolean isIntraday, boolean isSecurity);
}
