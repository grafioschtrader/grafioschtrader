package grafioschtrader.repository;

import java.util.List;

import grafiosch.dto.ValueKeyHtmlSelectOptions;
import grafiosch.repository.BaseRepositoryCustom;
import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.connector.instrument.IFeedConnector.FeedSupport;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Securitycurrency;
import grafioschtrader.reportviews.securitycurrency.SecuritycurrencyPosition;

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

}
