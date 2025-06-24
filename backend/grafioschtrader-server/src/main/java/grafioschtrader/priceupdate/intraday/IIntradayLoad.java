package grafioschtrader.priceupdate.intraday;

import java.util.List;

import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.entities.Securitycurrency;

/**
 * Interface for intraday price loading and real-time market data updates through feed connectors.
 * 
 * <p>
 * This interface defines the contract for updating intraday prices of securities and currency pairs from external data
 * providers using configured feed connectors. It supports both concurrent batch processing and individual security
 * updates with sophisticated retry mechanisms, timeout controls, and delayed update handling. The interface abstracts
 * feed connector complexity while providing flexible execution options and robust error handling for real-time market
 * data operations.
 * </p>
 * 
 * @param <S> the type of security currency extending Securitycurrency
 */
public interface IIntradayLoad<S extends Securitycurrency<S>> {

  /**
   * Updates intraday prices of securities or currency pairs using concurrent processing with default configuration.
   * 
   * <p>
   * This method processes multiple securities concurrently to improve performance when updating large sets of market
   * data. Uses default retry configuration and timeout settings from global parameters. Each security's retry counter
   * is managed automatically, resetting on successful updates and incrementing on failures.
   * </p>
   * 
   * @param securtycurrencies list of securities or currency pairs to update
   * @param singleThread      true to force single-threaded execution, false for concurrent processing
   * @return list of securities with updated intraday prices, excluding items that exceeded retry limits or are inactive
   */
  List<S> updateLastPriceOfSecuritycurrency(final List<S> securtycurrencies, boolean singleThread);

  /**
   * Updates intraday price of a single security or currency pair with comprehensive validation and error handling.
   * 
   * <p>
   * This method performs a complete update cycle including:
   * <ul>
   * <li>Feed connector validation based on idConnectorIntra and FS_INTRA support</li>
   * <li>Retry limit checking against the security's current retryIntraLoad counter</li>
   * <li>Active status verification using isActiveForIntradayUpdate()</li>
   * <li>Delayed update allowance based on connector's intradayDelayedSeconds and last update timestamp</li>
   * <li>Automatic retry counter management (reset on success, increment on failure)</li>
   * <li>Database persistence of updated security state</li>
   * </ul>
   * </p>
   * 
   * @param securitycurrency        the security or currency pair to update
   * @param maxIntraRetry           maximum number of retry attempts for failed price updates, -1 for unlimited retries
   * @param scIntradayUpdateTimeout timeout in seconds for determining if delayed updates are allowed
   * @return the security with updated intraday price and persisted retry counter, or original security if update was
   *         skipped
   */
  List<S> updateLastPriceOfSecuritycurrency(final List<S> securtycurrencies, final short maxIntraRetry,
      boolean singleThread);

  /**
   * Updates intraday price of a single security or currency pair with comprehensive validation and error handling.
   * 
   * <p>
   * This method performs a complete update cycle including:
   * <ul>
   * <li>Feed connector validation based on idConnectorIntra and FS_INTRA support</li>
   * <li>Retry limit checking against the security's current retryIntraLoad counter</li>
   * <li>Active status verification using isActiveForIntradayUpdate()</li>
   * <li>Delayed update allowance based on connector's intradayDelayedSeconds and last update timestamp</li>
   * <li>Automatic retry counter management (reset on success, increment on failure)</li>
   * <li>Database persistence of updated security state</li>
   * </ul>
   * </p>
   * 
   * @param securitycurrency        the security or currency pair to update
   * @param maxIntraRetry           maximum number of retry attempts for failed price updates, -1 for unlimited retries
   * @param scIntradayUpdateTimeout timeout in seconds for determining if delayed updates are allowed
   * @return the security with updated intraday price and persisted retry counter, or original security if update was
   *         skipped
   */
  S updateLastPriceSecurityCurrency(final S securitycurrency, final short maxIntraRetry,
      final int scIntradayUpdateTimeout);

  /**
   * Generates a download link URL string for intraday data with intelligent lazy loading detection.
   * 
   * <p>
   * Creates a URL for retrieving intraday price data by first locating the appropriate feed connector based on the
   * security's idConnectorIntra. The method intelligently determines the link type:
   * <ul>
   * <li>Returns "lazy" if the connector supports DL_LAZY_INTRA (lazy loading)</li>
   * <li>Otherwise delegates to createDownloadLink() for immediate link generation</li>
   * <li>Returns null if no suitable feed connector is found</li>
   * </ul>
   * </p>
   * 
   * @param securitycurrency the security or currency pair for which to generate the download link
   * @return URL string for accessing intraday data, "lazy" for lazy-loaded connectors, or null if no connector
   *         available
   */
  String getSecuritycurrencyIntraDownloadLinkAsUrlStr(S securitycurrency);

  /**
   * Creates a download link with API key security and connector-specific routing logic.
   * 
   * <p>
   * Generates download links with sophisticated security and routing logic:
   * <ul>
   * <li>For accessible API keys and non-DL_INTRA_FORCE_BACKEND connectors: creates direct provider links</li>
   * <li>For Security entities: uses feedConnector.getSecurityIntradayDownloadLink()</li>
   * <li>For Currencypair entities: uses feedConnector.getCurrencypairIntradayDownloadLink()</li>
   * <li>For protected API keys or DL_INTRA_FORCE_BACKEND: routes through secure backend using
   * getDownlinkWithApiKey()</li>
   * </ul>
   * </p>
   * 
   * @param securitycurrency the security or currency pair for which to create the download link
   * @param feedConnector    the feed connector to use for link generation and API key access determination
   * @return download link string (direct provider URL or secure backend-routed URL), or null if connector is null
   */
  String createDownloadLink(S securitycurrency, IFeedConnector feedConnector);

}
