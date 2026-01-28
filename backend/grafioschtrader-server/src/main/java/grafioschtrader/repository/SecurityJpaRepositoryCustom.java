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
import grafioschtrader.reportviews.securitycurrency.ISecurityDataProviderUrls;
import grafioschtrader.reportviews.securitycurrency.SecuritycurrencyPosition;
import grafioschtrader.reportviews.securitycurrency.SecurityDataProviderUrls;
import grafioschtrader.repository.SecurityJpaRepository.SplitAdjustedHistoryquotesResult;
import grafioschtrader.search.SecuritycurrencySearch;

public interface SecurityJpaRepositoryCustom extends ISecuritycurrencyService<Security> {

  /**
   * Batch query to find securities by ISIN+currency tuples in a single database query.
   * Used by GTNet lastprice exchange to efficiently query multiple securities.
   *
   * @param isinCurrencyPairs list of [isin, currency] pairs to query
   * @return list of matching Security entities
   */
  List<Security> findByIsinCurrencyTuples(List<String[]> isinCurrencyPairs);

  /**
   * Completes the history for all securities or currency pairs associated with the given stock exchanges until
   * yesterday's date.
   *
   * @param idsStockexchange A list of stock exchange IDs for which to update the security history.
   * @return A list of securities for which the history was updated.
   */
  List<Security> catchAllUpSecurityHistoryquote(List<Integer> idsStockexchange);

  /**
   * Updates the last intraday price for all securities.
   */
  void updateAllLastPrices();

  /**
   * Updates the last price for a given list of securities.
   *
   * @param securities A list of securities for which to update the last price.
   * @return The list of securities with updated last prices.
   */
  List<Security> updateLastPriceByList(List<Security> securyties);

  /**
   * Finds securities that are active on or after the specified date, also includes the private instruments. They are
   * sorted by name.
   *
   * @param dateString The date string (YYYY-MM-DD) to filter active securities.
   * @return A list of active securities.
   * @throws ParseException If the dateString cannot be parsed.
   */
  List<Security> findByActiveToDateGreaterThanEqualOrderByName(String dateString) throws ParseException;

  /**
   * Retrieves tradable securities for a given tenant and watchlist ID.
   *
   * @param idWatchlist The ID of the watchlist.
   * @return A list of tradable securities.
   * @throws ParseException If there's an issue parsing date values during the process.
   */
  List<Security> getTradableSecuritiesByTenantAndIdWatschlist(Integer idWatchlist) throws ParseException;

  /**
   * Attempts to update the intraday data for securities in a specific watchlist, whereby the number of failed
   * intraday loads must be greater than 0. The user could trigger this action to force an intraday update of a
   * previously unsuccessful update.
   *
   * @param idTenant    The tenant's ID, for security reasons.
   * @param idWatchlist The ID of the watchlist.
   * @return A list of securities for which intraday data update was attempted.
   */
  List<Security> tryUpToIntradayDataWhenRetryIntraLoadGreaterThan0(Integer idTenant, Integer idWatchlist);

  /**
   * Attempts to update the historical price data for securities in a specific watchlist, where the number of failed
   * historical loads must be greater than 0. The user could trigger this action to force a historical price data update
   * of a previously unsuccessful update.
   *
   * @param idTenant    The tenant's ID, for security reasons.
   * @param idWatchlist The ID of the watchlist.
   * @return A list of securities for which historical data update was attempted.
   */
  List<Security> tryUpToDateHistoricalDataWhenRetryHistoryLoadGreaterThan0(Integer idTenant, Integer idWatchlist);

  /**
   * Calculates gain/loss for a security position based on a specified date or the newest price.
   *
   * @param securitycurrencyPositionSummary The summary of the security position.
   * @param positionCloseOnLatestPrice      Interface for closing position on latest price.
   * @param untilDate                       The date until which to calculate the gain/loss. If null, the newest price
   *                                        is used.
   */
  void calcGainLossBasedOnDateOrNewestPrice(SecurityPositionSummary securitycurrencyPositionSummary,
      IPositionCloseOnLatestPrice<Security, SecurityPositionSummary> positionCloseOnLatestPrice, Date untilDate);

  /**
   * Calculates gain/loss for a list of security positions based on a specified date or the newest price.
   *
   * @param securitycurrencyPositionSummary A list of security position summaries.
   * @param untilDate                       The date until which to calculate the gain/loss. If null, the newest price
   *                                        is used.
   */
  void calcGainLossBasedOnDateOrNewestPrice(List<SecurityPositionSummary> securitycurrencyPositionSummary,
      Date untilDate);

  /**
   * Calculates gain/loss for a list of security positions based on a specified date or the newest price, using a
   * provided interface for position closing.
   *
   * @param securitycurrencyPositionSummary A list of security position summaries.
   * @param positionCloseOnLatestPrice      Interface for closing position on latest price.
   * @param untilDate                       The date until which to calculate the gain/loss. If null, the newest price
   *                                        is used.
   */
  void calcGainLossBasedOnDateOrNewestPrice(List<SecurityPositionSummary> securitycurrencyPositionSummary,
      IPositionCloseOnLatestPrice<Security, SecurityPositionSummary> positionCloseOnLatestPrice, Date untilDate);

  /**
   * Searches for securities based on the provided search criteria.
   *
   * @param securitycurrencySearch The search criteria.
   * @return A list of securities matching the criteria.
   */
  List<Security> searchByCriteria(final SecuritycurrencySearch securitycurrencySearch);

  /**
   * Searches for securities based on criteria, excluding those in a given watchlist or correlation set. It is intended,
   * for example, to expand a correlation set or watch list with additional instruments. Therefore, existing instruments
   * may no longer appear in the selection.
   *
   * @param idWatchlist            ID of the watchlist whose existing instruments are no longer offered
   * @param idCorrelationSet       ID of the correlation set whose existing instruments are no longer offered
   * @param securitycurrencySearch The search criteria.
   * @param idTenant               Securities with an active open position may be selected; this ID is required for this purpose
   * @return A list of securities matching the criteria.
   */
  List<Security> searchBuilderWithExclusion(Integer idWatchlist, Integer idCorrelationSet,
      SecuritycurrencySearch securitycurrencySearch, Integer idTenant);

  /**
   * Processes open positions and calculates gain/loss as if these positions were closed at the actual price on a given
   * date.
   *
   * @param untilDate          The date to calculate the gain/loss.
   * @param summarySecurityMap A map containing the calculations for each individual security, which will be updated.
   * @return The list of updated security position summaries.
   */
  List<SecurityPositionSummary> processOpenPositionsWithActualPrice(Date untilDate,
      Map<Security, SecurityPositionSummary> summarySecurityMap);

  /**
   * Reloads the full historical price data for a security asynchronously. This is typically used when historical data
   * needs to be completely refreshed, for example, after a stock split.
   *
   * @param securitycurrency The security for which to reload the history.
   */
  void reloadAsyncFullHistoryquote(Security securitycurrency);

  /**
   * Reloads the full historical price data for a security by its ID, using an external trigger. This means that loading
   * was triggered by a user. This is an asynchronous process.
   *
   * @param idSecuritycurrency The ID of the security/currency pair.
   */
  void reloadAsyncFullHistoryquoteExternal(Integer idSecuritycurrency);

  /**
   * The user interface receives a link to check the price data provider of a security. If an API key is required, only
   * the backend can evaluate this link and return the corresponding content. The content of the provider may also be
   * determined in the backend for other reasons.
   * 
   * @param idSecuritycurrency The ID of the security.
   * @param isIntraday         True if intraday data is requested, false for historical data.
   * @return The raw response string from the data provider.
   */
  String getDataProviderResponseForUser(final Integer idSecuritycurrency, final boolean isIntraday);

  /**
   * Gets the direct link to the data provider for a security's price data (historical or intraday) for user display.
   *
   * @param idSecuritycurrency The ID of the security.
   * @param isIntraday True if intraday data is requested, false for historical data.
   * @return The URL string for the data provider.
   */
  String getDataProviderLinkForUser(final Integer idSecuritycurrency, final boolean isIntraday);

  /**
   * Retrieves the raw response from the data provider for a security's dividend or split information.
   *
   * @param idSecuritycurrency The ID of the security.
   * @param isDiv True if dividend data is requested, false for split data.
   * @return The raw response string from the data provider.
   */
  String getDivSplitProviderResponseForUser(final Integer idSecuritycurrency, final boolean isDiv);

  HistoryquoteQualityHead getHistoryquoteQualityHead(HistoryquoteQualityGrouped groupedBy);

  /**
   * Checks if a user has the permission to change derived fields of a security. These properties include the basic
   * instrument, the formula and the additional instruments.
   *
   * @param user             The user attempting the change.
   * @param security         The security being modified.
   * @param existingSecurity The existing state of the security before modification.
   * @return True if the user can change derived fields, false otherwise.
   */
  boolean checkUserCanChangeDerivedFields(User user, Security security, Security existingSecurity);

  /**
   * Sets the appropriate download link for dividend data on the {@code securitycurrencyPosition}. If a dividend data
   * connector is configured for the security and requires an API key that the current context may not provide directly
   * to the frontend, this method sets a backend-redirect URL. Otherwise, it attempts to set a direct URL to the
   * dividend data provider. The link is set on the {@code dividendUrl} property of the
   * {@code securitycurrencyPosition}.
   *
   * @param securitycurrencyPosition The security position object on which the dividend download URL should be set.
   */
  void setDividendDownloadLink(SecuritycurrencyPosition<Security> securitycurrencyPosition);

  /**
   * Sets the appropriate download link for stock split data on the {@code securitycurrencyPosition}. If a split data
   * connector is configured for the security and requires an API key that the current context may not provide directly
   * to the frontend, this method sets a backend-redirect URL. Otherwise, it attempts to set a direct URL to the split
   * data provider. The link is set on the {@code splitUrl} property of the {@code securitycurrencyPosition}.
   *
   * @param securitycurrencyPosition The security position object on which the split download URL should be set.
   */
  void setSplitDownloadLink(SecuritycurrencyPosition<Security> securitycurrencyPosition);

  /**
   * Sets the dividend download link on the provided URL holder object.
   *
   * @param security  The security for which to generate the dividend download link.
   * @param urlHolder The object implementing ISecurityDataProviderUrls to receive the URL.
   */
  void setDividendDownloadLink(Security security, ISecurityDataProviderUrls urlHolder);

  /**
   * Sets the split download link on the provided URL holder object.
   *
   * @param security  The security for which to generate the split download link.
   * @param urlHolder The object implementing ISecurityDataProviderUrls to receive the URL.
   */
  void setSplitDownloadLink(Security security, ISecurityDataProviderUrls urlHolder);

  /**
   * Retrieves all data provider URLs for a security. This includes intraday, historical, dividend, and split URLs.
   *
   * @param idSecuritycurrency The ID of the security.
   * @return A SecurityDataProviderUrls object containing all available URLs.
   */
  SecurityDataProviderUrls getDataProviderUrls(Integer idSecuritycurrency);

  /**
   * Retrieves statistical results for a security within a given date range.
   *
   * @param idSecuritycurrency The ID of the security.
   * @param dateFrom           The start date of the period.
   * @param dateTo             The end date of the period.
   * @return An InstrumentStatisticsResult object containing the statistics.
   */
  InstrumentStatisticsResult getSecurityStatisticsReturnResult(Integer idSecuritycurrency, LocalDate dateFrom,
      LocalDate dateTo) throws IllegalAccessException, InvocationTargetException, NoSuchMethodException;

  /**
   * Rebuilds the historical price data for a security or currency pair. This can be necessary in cases like data
   * corruption or after significant events like splits.
   *
   * @param security The security for which to rebuild history.
   * @return The security with its rebuilt history.
   */
  Security rebuildSecurityCurrencypairHisotry(Security security);

  /**
   * Tries to determine if the supplier's historical price data already reflects the split.
   */
  SplitAdjustedHistoryquotesResult isLatestSplitHistoryquotePossibleAdjusted(Security security,
      List<Securitysplit> securitysplits) throws Exception;

  /**
   * Returns the name or text of the currency pair for the ID of the entity for securities and currency pairs.
   *
   * @return Map with the ID of the entity and the name or text as value.
   */
  Map<Integer, String> getSecurityCurrencyPairInfo();

  // TODO remove it
  void checkAndClearSecuritycurrencyConnectors(final Security security);

  /**
   * Resets retry_history_load and retry_intra_load counters to zero for all securities that are active on the given
   * date and optionally filtered by connector. Securities with retry counters greater than zero and a configured
   * connector will have their counters reset.
   *
   * @param connectorId  the full connector ID (e.g., "gt.datafeed.yahoo") to filter, or null to reset for all
   *                     connectors
   * @param activeOnDate the date to check if security is active (activeToDate >= this date)
   */
  void resetRetryCountersByConnector(String connectorId, Date activeOnDate);

}
