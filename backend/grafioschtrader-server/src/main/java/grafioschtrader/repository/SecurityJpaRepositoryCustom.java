package grafioschtrader.repository;

import java.lang.reflect.InvocationTargetException;
import java.text.ParseException;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Map;

import grafiosch.entities.User;
import grafioschtrader.dto.InstrumentStatisticsResult;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitysplit;
import grafioschtrader.reportviews.historyquotequality.HistoryquoteQualityGrouped;
import grafioschtrader.reportviews.historyquotequality.HistoryquoteQualityHead;
import grafioschtrader.reportviews.securityaccount.SecurityPositionSummary;
import grafioschtrader.reportviews.securitycurrency.SecuritycurrencyPosition;
import grafioschtrader.repository.SecurityJpaRepository.SplitAdjustedHistoryquotes;
import grafioschtrader.repository.SecurityJpaRepository.SplitAdjustedHistoryquotesResult;
import grafioschtrader.search.SecuritycurrencySearch;

public interface SecurityJpaRepositoryCustom extends ISecuritycurrencyService<Security> {

  /**
   * Complete the history for all security or currency pairs until yesterday's
   * date.
   *
   * @return
   */
  List<Security> catchAllUpSecurityHistoryquote(List<Integer> idsStockexchange);

  void updateAllLastPrices();

  List<Security> updateLastPriceByList(List<Security> securyties);

  List<Security> findByActiveToDateGreaterThanEqualOrderByName(String dateString) throws ParseException;

  List<Security> getTradableSecuritiesByTenantAndIdWatschlist(Integer idWatchlist) throws ParseException;

  List<Security> tryUpToIntradayDataWhenRetryIntraLoadGreaterThan0(Integer idTenant, Integer idWatchlist);

  List<Security> tryUpToDateHistoricalDataWhenRetryHistoryLoadGreaterThan0(Integer idTenant, Integer idWatchlist);

  void calcGainLossBasedOnDateOrNewestPrice(SecurityPositionSummary securitycurrencyPositionSummary,
      IPositionCloseOnLatestPrice<Security, SecurityPositionSummary> positionCloseOnLatestPrice, Date untilDate);

  void calcGainLossBasedOnDateOrNewestPrice(List<SecurityPositionSummary> securitycurrencyPositionSummary,
      Date untilDate);

  void calcGainLossBasedOnDateOrNewestPrice(List<SecurityPositionSummary> securitycurrencyPositionSummary,
      IPositionCloseOnLatestPrice<Security, SecurityPositionSummary> positionCloseOnLatestPrice, Date untilDate);

  List<Security> searchByCriteria(final SecuritycurrencySearch securitycurrencySearch);

  List<Security> searchBuilderWithExclusion(Integer idWatchlist, Integer idCorrelationSet,
      SecuritycurrencySearch securitycurrencySearch, Integer idTenant);

  /**
   * Process open positions. Calculate gain/loss when this position would be
   * closed.
   *
   * @param summarySecurityMap
   */
  List<SecurityPositionSummary> processOpenPositionsWithActualPrice(Date untilDate,
      Map<Security, SecurityPositionSummary> summarySecurityMap);

  /**
   * Some cases the historical prices must be reloaded completely. For example
   * when a split is added. It is an asynchronous loading.
   *
   * @param securitycurrency
   */
  void reloadAsyncFullHistoryquote(Security securitycurrency);

  void reloadAsyncFullHistoryquoteExternal(Integer idSecuritycurrency);

  /**
   * The user interface receives a link to check the price data provider of a
   * security. If an API key is required, only the backend can evaluate this link
   * and return the corresponding content. The content of the provider may also be
   * determined in the backend for other reasons.
   *
   * @param idSecuritycurrency
   * @param isIntraday
   * @return
   */
  String getDataProviderResponseForUser(final Integer idSecuritycurrency, final boolean isIntraday);

  String getDataProviderLinkForUser(final Integer idSecuritycurrency, final boolean isIntraday);
  
  String getDivSplitProviderResponseForUser(final Integer idSecuritycurrency, final boolean isDiv);

  HistoryquoteQualityHead getHistoryquoteQualityHead(HistoryquoteQualityGrouped groupedBy);

  boolean checkUserCanChangeDerivedFields(User user, Security security, Security existingSecurity);

  void setDividendDownloadLink(SecuritycurrencyPosition<Security> securitycurrencyPosition);
  
  void setSplitDownloadLink(SecuritycurrencyPosition<Security> securitycurrencyPosition);

  InstrumentStatisticsResult getSecurityStatisticsReturnResult(Integer idSecuritycurrency, LocalDate dateFrom,
      LocalDate dateTo) throws IllegalAccessException, InvocationTargetException, NoSuchMethodException;

  /**
   * Some cases the historical prices must be reloaded completely. For example
   * when a split is added.
   *
   * @param security
   * @return
   */
  Security rebuildSecurityCurrencypairHisotry(Security security);

  /**
   * It tries to detect if the historical price data after the latest split has
   * been adjusted.
   *
   * @param security
   * @param securitysplits
   * @return
   * @throws Exception
   */
  @Deprecated
  SplitAdjustedHistoryquotes isYoungestSplitHistoryquotePossibleAdjusted(Security security,
      List<Securitysplit> securitysplits, boolean useConnector) throws Exception;

  /**
   * Tries to determine if the supplier's historical price data already reflects
   * the split.
   *
   * @param security
   * @param securitysplits
   * @return
   * @throws Exception
   */
  SplitAdjustedHistoryquotesResult isLatestSplitHistoryquotePossibleAdjusted(Security security,
      List<Securitysplit> securitysplits) throws Exception;

  /**
   * Returns the name or text of the currency pair for the ID of the entity for securities and currency pairs.   
   * @return Map with the ID of the entity and the name or text as value.
   */
  Map<Integer, String> getSecurityCurrencyPairInfo();
  
  // TODO remove it
  void checkAndClearSecuritycurrencyConnectors(final Security security);

}
