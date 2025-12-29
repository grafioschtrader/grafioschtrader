package grafioschtrader.connector.instrument;

import java.time.LocalDate;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;

import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Dividend;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitycurrency;
import grafioschtrader.entities.Securitysplit;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Connector interface for data providers that supply financial market data.
 * <p>
 * This interface defines the contract for connectors that can retrieve various types of financial data including
 * historical prices, intraday prices, dividends, and stock splits for both securities and currency pairs from external
 * data sources.
 * </p>
 * 
 * <h3>Supported Data Types</h3>
 * <ul>
 * <li><strong>Historical Data (FS_HISTORY):</strong> End-of-day price data over time periods</li>
 * <li><strong>Intraday Data (FS_INTRA):</strong> Real-time or delayed price updates</li>
 * <li><strong>Dividend Data (FS_DIVIDEND):</strong> Dividend payment information for securities</li>
 * <li><strong>Split Data (FS_SPLIT):</strong> Stock split events and ratios</li>
 * </ul>
 * 
 * <h3>Data Provider Integration</h3>
 * <p>
 * Implementations should handle:
 * </p>
 * <ul>
 * <li>Authentication and API key management for premium data sources</li>
 * <li>Rate limiting and request throttling</li>
 * <li>Error handling and retry mechanisms</li>
 * <li>Data format parsing and validation</li>
 * <li>URL construction for different data types</li>
 * </ul>
 * 
 * <h3>Validation and Quality Control</h3>
 * <p>
 * Connectors provide validation capabilities including:
 * </p>
 * <ul>
 * <li>URL pattern validation using regex</li>
 * <li>HTTP connectivity checks</li>
 * <li>Data provider response validation</li>
 * <li>Instrument availability verification</li>
 * </ul>
 */
public interface IFeedConnector {

  /**
   * Enumeration of supported feed types that a connector can provide. Each feed type represents a different category of
   * financial data.
   */
  public enum FeedSupport {
    /** Historical end-of-day price data */
    FS_HISTORY,
    /** Intraday real-time or delayed price data */
    FS_INTRA,
    /** Dividend payment information */
    FS_DIVIDEND,
    /** Stock split event data */
    FS_SPLIT
  }

  /**
   * Enumeration of feed identifier types that specify how instruments are identified and whether additional URL
   * parameters are required for data access.
   */
  public enum FeedIdentifier {
    /** Supports currency pairs without requiring extended URL parameters */
    CURRENCY,
    /** Supports currency pairs only with extended URL parameters */
    CURRENCY_URL,
    /** Supports securities without requiring extended URL parameters */
    SECURITY,
    /** Supports securities only with extended URL parameters */
    SECURITY_URL,
    /** Supports dividend data without requiring extended URL parameters */
    DIVIDEND,
    /** Supports dividend data only with extended URL parameters */
    DIVIDEND_URL,
    /** Supports split data without requiring extended URL parameters */
    SPLIT,
    /** Supports split data only with extended URL parameters */
    SPLIT_URL
  }

  /**
   * Enumeration of URL validation types that can be performed to verify data provider connectivity and instrument
   * availability.
   */
  public enum UrlCheck {
    /** Validation for intraday data URLs */
    INTRADAY,
    /** Validation for historical data URLs */
    HISTORY
  }

  /**
   * Enumeration of download link creation strategies for frontend display. Some data providers require backend
   * processing due to API keys or complex authentication.
   */
  public enum DownloadLink {
    /** Historical price data download link must be requested in an additional request */
    DL_LAZY_HISTORY,
    /** Intraday price data download link must be requested in an additional request */
    DL_LAZY_INTRA,
    /** Historical price data content must be created in the backend due to API keys */
    DL_HISTORY_FORCE_BACKEND,
    /** Intraday price data content must be created in the backend due to API keys */
    DL_INTRA_FORCE_BACKEND
  }

  @Schema(description = "Id of the connector as it is used in the database")
  String getID();

  @Schema(description = "Id of the connector without prefix")
  String getShortID();

  /**
   * Returns a numeric identifier for this connector, derived from the short ID hash code. Used for task data change
   * references where an integer entity ID is needed.
   *
   * @return numeric identifier based on shortId.hashCode()
   */
  @JsonIgnore
  default int getIdNumber() {
    return getShortID().hashCode();
  }

  @Schema(description = "The display name of the connector ")
  String getReadableName();

  /**
   * Indicates whether this connector is currently activated and available for use. Connectors may be deactivated due to
   * missing API keys or configuration issues.
   *
   * @return true if the connector is activated, false otherwise
   */
  @JsonIgnore
  boolean isActivated();

  /**
   * Returns a map of supported feed types and their corresponding feed identifiers. This defines what types of data the
   * connector can provide and how instruments are identified.
   *
   * @return map of feed support types to feed identifier arrays
   */
  Map<FeedSupport, FeedIdentifier[]> getSecuritycurrencyFeedSupport();

  @Schema(description = "Shows a help text to the connector which can be shown in the user interface")
  Description getDescription();

  /**
   * Indicates whether this connector supports currency pair data regardless of whether it's historical or intraday
   * pricing.
   *
   * @return true if currency data is supported, false otherwise
   */
  boolean supportsCurrency();

  /**
   * Indicates whether this connector supports security data regardless of whether it's historical or intraday pricing.
   *
   * @return true if security data is supported, false otherwise
   */
  boolean supportsSecurity();

  /**
   * Generates the download URL for accessing historical price data of a security. This URL may be displayed to users or
   * used for direct data retrieval.
   *
   * @param security the security for which to generate the download link
   * @return the historical data download URL, or null if not supported
   */
  String getSecurityHistoricalDownloadLink(Security security);

  /**
   * Checks whether this connector supports a specific feed identifier type.
   *
   * @param feedIdentifier the feed identifier type to check
   * @return true if the feed identifier is supported, false otherwise
   */
  boolean hasFeedIndentifier(FeedIdentifier feedIdentifier);

  /**
   * Validates and potentially clears URL extension parameters for a security or currency pair. This method performs
   * regex validation and connectivity checks to ensure the data provider can access the specified instrument. Invalid
   * configurations may result in cleared URLs or thrown exceptions.
   *
   * @param <S>              the type of securitycurrency (Security or Currencypair)
   * @param securitycurrency the security or currency pair to validate
   * @param feedSupport      the type of feed support being validated
   * @throws RuntimeException if validation fails or connectivity issues are detected
   */
  <S extends Securitycurrency<S>> void checkAndClearSecuritycurrencyUrlExtend(Securitycurrency<S> securitycurrency,
      FeedSupport feedSupport);

  /**
   * Generates the download URL for accessing intraday price data of a security.
   *
   * @param security the security for which to generate the intraday download link
   * @return the intraday data download URL, or null if not supported
   */
  String getSecurityIntradayDownloadLink(Security security);

  /**
   * Generates the download URL for accessing historical currency pair exchange rate data. This URL may be used in
   * frontend applications to verify connector configuration.
   *
   * @param currencypair the currency pair for which to generate the download link
   * @return the historical currency data download URL, or null if not supported
   */
  String getCurrencypairHistoricalDownloadLink(Currencypair currencypair);

  /**
   * Generates the download URL for accessing intraday currency pair exchange rate data.
   *
   * @param currencypair the currency pair for which to generate the download link
   * @return the intraday currency data download URL, or null if not supported
   */
  String getCurrencypairIntradayDownloadLink(Currencypair currencypair);

  /**
   * Returns the supported feed identifier types for a specific feed support category.
   *
   * @param feedSupport the feed support type to query
   * @return array of supported feed identifiers, or null if not supported
   */
  FeedIdentifier[] getSecuritycurrencyFeedSupport(final FeedSupport feedSupport);

  /**
   * Indicates whether this connector requires gap filling for historical data. Some connectors only provide end-of-day
   * prices when actual trading occurred, requiring gaps to be filled with previous closing prices.
   *
   * @param security the security to check
   * @return true if gap filling is needed, false otherwise
   */
  boolean needHistoricalGapFiller(final Security security);

  /**
   * Retrieves historical end-of-day price quotes for a security within the specified date range. The returned list
   * should be sorted in ascending order by date.
   *
   * @param security the security for which to retrieve historical data
   * @param from     the start date (inclusive) for the historical data range
   * @param to       the end date (inclusive) for the historical data range
   * @return list of historical quotes sorted by date
   * @throws Exception if data retrieval fails due to connectivity, authentication, or parsing errors
   */
  List<Historyquote> getEodSecurityHistory(Security security, Date from, Date to) throws Exception;

  /**
   * Updates the security with the latest intraday price information including
   * last price, volume, daily high/low, and percentage change.
   *
   * @param security the security to update with latest price data
   * @throws Exception if the price update fails due to connectivity or data provider issues
   */
  void updateSecurityLastPrice(Security security) throws Exception;

  /**
   * Returns the delay in seconds for intraday data provided by this connector.
   * Real-time data has 0 delay, while delayed feeds may have 15-20 minute delays.
   *
   * @return delay in seconds for intraday data
   */
  int getIntradayDelayedSeconds();

  /**
   * Retrieves historical exchange rate data for a currency pair within the specified date range.
   *
   * @param currencyPair the currency pair for which to retrieve historical exchange rates
   * @param from the start date (inclusive) for the historical data range
   * @param to the end date (inclusive) for the historical data range
   * @return list of historical exchange rate quotes sorted by date
   * @throws Exception if data retrieval fails
   */
  List<Historyquote> getEodCurrencyHistory(Currencypair currencyPair, Date from, Date to) throws Exception;

  /**
   * Updates the currency pair with the latest exchange rate information.
   *
   * @param currencyPair the currency pair to update with latest exchange rate
   * @throws Exception if the exchange rate update fails
   */
  void updateCurrencyPairLastPrice(Currencypair currencyPair) throws Exception;

  /**
   * Indicates whether dividend data from this connector is already adjusted for stock splits.
   * If true, dividend amounts reflect post-split values; if false, raw dividend amounts are provided.
   *
   * @return true if dividends are split-adjusted, false otherwise
   */
  @JsonIgnore
  boolean isDividendSplitAdjusted();

  /**
   * Generates the download URL for accessing historical dividend data of a security.
   *
   * @param security the security for which to generate the dividend download link
   * @return the dividend data download URL, or null if not supported
   */
  String getDividendHistoricalDownloadLink(Security security);

  /**
   * Retrieves dividend payment history for a security starting from the specified date.
   * The returned list must be sorted in ascending order by ex-dividend date.
   *
   * @param security the security for which to retrieve dividend history
   * @param fromDate the start date from which to retrieve dividend data
   * @return list of dividend payments sorted by ex-dividend date in ascending order
   * @throws Exception if dividend data retrieval fails
   */
  List<Dividend> getDividendHistory(Security security, LocalDate fromDate) throws Exception;

  /**
   * Generates the download URL for accessing historical stock split data of a security.
   *
   * @param security the security for which to generate the split download link
   * @return the stock split data download URL, or null if not supported
   */
  String getSplitHistoricalDownloadLink(Security security);

  /**
   * Calculates the number of days to wait before the next attempt to check if historical
   * price data reflects a stock split. Recent splits may take several days to be reflected
   * in adjusted historical prices from data providers.
   *
   * @param splitDate the date when the stock split occurred
   * @return number of days to wait before next check, or null if no further checks needed
   */
  Integer getNextAttemptInDaysForSplitHistorical(Date splitDate);

  /**
   * Returns which data provider the content of the links for the frontend must be created in the backend. Normally, the
   * frontend receives a URL that can be opened directly in the browser. This is not possible for data providers with
   * API keys, so the data content is prepared in the backend. There are other cases where the data content must be
   * created in the backend.
   *
   * @return set of download link types requiring special handling
   */
  EnumSet<DownloadLink> isDownloadLinkCreatedLazy();

  /**
   * The user interface receives a link to check the price data provider of a security. If an API key is required, only
   * the backend can evaluate this link and return the corresponding content. The content of the provider may also be
   * determined in the backend for other reasons.
  *
   * @param httpPageUrl the URL to fetch content from
   * @return the content of the requested page, typically formatted for HTML display
   */
  String getContentOfPageRequest(String httpPageUrl);

  /**
   * Retrieves stock split history for a security within the specified date range.
   * The returned data includes split ratios and effective dates.
   *
   * @param security the security for which to retrieve split history
   * @param fromDate the start date for the split data range
   * @param toDate the end date for the split data range
   * @return list of stock split events within the specified date range
   * @throws Exception if split data retrieval fails
   */
  List<Securitysplit> getSplitHistory(Security security, LocalDate fromDate, LocalDate toDate) throws Exception;

  @Schema(description = """
      Container class for connector help text and documentation that can be displayed
      in user interfaces to assist with connector configuration and usage.""")
  class Description {

    @Schema(description = "Help text for the histrical data connector")
    public String historicalDescription = "";
    @Schema(description = "Help text for the intraday data connector")
    public String intraDescription = "";

    public String getHistoricalDescription() {
      return historicalDescription.trim().length() > 0 ? historicalDescription : null;
    }

    public String getIntraDescription() {
      return intraDescription.trim().length() > 0 ? intraDescription : null;
    }

  }

}
