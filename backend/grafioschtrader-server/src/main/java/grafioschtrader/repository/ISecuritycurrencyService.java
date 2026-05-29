package grafioschtrader.repository;

import java.util.List;

import grafiosch.dto.ValueKeyHtmlSelectOptions;
import grafiosch.repository.BaseRepositoryCustom;
import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.connector.instrument.IFeedConnector.FeedSupport;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Securitycurrency;
import grafioschtrader.priceupdate.historyquote.SecurityCurrencyMaxHistoryquoteData;
import grafioschtrader.reportviews.securitycurrency.ISecurityDataProviderUrls;
import grafioschtrader.reportviews.securitycurrency.SecuritycurrencyPosition;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.SpecialInvestmentInstruments;

/**
 * Interface for services handling entities that are either a Security or a Currencypair. It provides common
 * functionalities for managing and retrieving data related to these financial instruments.
 *
 * @param <S> A type that extends {@link Securitycurrency}, representing either a Security or a Currencypair.
 */
public interface ISecuritycurrencyService<S extends Securitycurrency<S>> extends BaseRepositoryCustom<S> {

  /**
   * Sets the intraday data download link for the given security/currency position. This link can be used by the user to
   * view the intraday prices in the web browser. Similar link is used by the backend to download this data.
   *
   * @param securitycurrencyPosition The security or currency position for which to set the intraday download link.
   */
  void setSecuritycurrencyIntradayDownloadLink(SecuritycurrencyPosition<S> securitycurrencyPosition);

  /**
   * Sets the historical data download link for the given security/currency position. This link can be used by the user
   * to view the historical price data in the web browser. Similar link is used by the backend to download this data.
   *
   * @param securitycurrencyPosition The security or currency position for which to set the historical download link.
   */
  void setSecuritycurrencyHistoricalDownloadLink(SecuritycurrencyPosition<S> securitycurrencyPosition);

  /**
   * Sets the intraday data download link on the provided URL holder object.
   *
   * @param securitycurrency The security or currency pair for which to generate the intraday download link.
   * @param urlHolder        The object implementing ISecurityDataProviderUrls to receive the URL.
   */
  void setSecuritycurrencyIntradayDownloadLink(S securitycurrency, ISecurityDataProviderUrls urlHolder);

  /**
   * Sets the historical data download link on the provided URL holder object.
   *
   * @param securitycurrency The security or currency pair for which to generate the historical download link.
   * @param urlHolder        The object implementing ISecurityDataProviderUrls to receive the URL.
   */
  void setSecuritycurrencyHistoricalDownloadLink(S securitycurrency, ISecurityDataProviderUrls urlHolder);

  /**
   * Retrieves a list of all available feed connectors for financial data.
   *
   * @return A list of IFeedConnector instances.
   */
  List<IFeedConnector> getFeedConnectors();

  /**
   * Returns a list of IFeedConnector instances that support either currency pairs or securities.
   *
   * @param isCurrency If true, returns connectors supporting currency pairs; otherwise, returns connectors supporting
   *                   securities.
   * @return A list of relevant IFeedConnector instances.
   */
  List<IFeedConnector> getFeedConnectors(boolean isCurrency);

  /**
   * Like {@link #getFeedConnectors(boolean)} but additionally filtered by the supplied entity context using
   * {@link IFeedConnector#supports(String, String, AssetclassType, SpecialInvestmentInstruments)}. The extra filter is
   * only applied when {@code gt.force.connector.match} is set to mode 2 (frontend pre-filter) AND both
   * {@code assetclassType} and {@code specInvInstrument} are provided. In all other situations the unfiltered list is
   * returned, preserving the legacy behaviour.
   *
   * @param isCurrency        true → currency-pair connectors, false → security connectors
   * @param idStockexchange   stock-exchange id used to resolve MIC + country code for the geo check; may be null
   * @param assetclassType    the security's asset class type, or {@code CURRENCY_PAIR} for currency pairs; may be null
   * @param specInvInstrument the special investment instrument, or {@code FOREX}/{@code CFD} for currency pairs; may be
   *                          null
   * @return filtered connector list
   */
  List<IFeedConnector> getFeedConnectors(boolean isCurrency, Integer idStockexchange,
      AssetclassType assetclassType, SpecialInvestmentInstruments specInvInstrument);

  /**
   * Retrieves all feed connectors that support a specific type of data feed (e.g., historical, intraday) as a list of
   * key-value pairs suitable for HTML select options.
   *
   * @param feedSupport The type of feed support required from the connectors.
   * @return A list of {@link ValueKeyHtmlSelectOptions} representing the feed connectors.
   */
  List<ValueKeyHtmlSelectOptions> getAllFeedConnectorsAsKeyValue(FeedSupport feedSupport);

  /**
   * Gets the JPA repository associated with the specific Securitycurrency type.
   *
   * @return The {@link SecurityCurrencypairJpaRepository} instance for the type {@code S}.
   */
  SecurityCurrencypairJpaRepository<S> getJpaRepository();

  /**
   * Fills any gaps in the historical price data for the given security or currency pair. This typically involves
   * fetching missing data points from a data provider. The implementation for currency pair or security will differ. In
   * the case of securities, the trading calendar must also be taken into account.
   *
   * @param securitycurrency The security or currency pair for which to fill historical data gaps.
   * @return A list of Historyquote objects that were added to fill the gaps.
   */
  List<Historyquote> fillGap(S securitycurrency);

  /**
   * Finds GTNet-opted-in instruments currently in the GTNet fallback band — connector retry counter at or above its
   * configured cap (gt.history.retry) but below the absolute exhaustion cap (gt.history.retry + gt.gtnet.quote.retry).
   * Each projection includes the instrument and its most recent historyquote date (null if there is no history yet),
   * which lets the GTNet service compute the request range without a follow-up query.
   *
   * @param connectorCap the connector retry cap (gt.history.retry); inclusive lower bound for the GTNet fallback band
   * @param absoluteCap  the absolute exhaustion cap; exclusive upper bound; instruments at or above this value are
   *                     no longer retried automatically
   * @return projections pairing each eligible instrument with its latest historyquote date (or null if no history yet)
   */
  List<SecurityCurrencyMaxHistoryquoteData<S>> findGTNetFallbackBandInstruments(short connectorCap, short absoluteCap);

}
