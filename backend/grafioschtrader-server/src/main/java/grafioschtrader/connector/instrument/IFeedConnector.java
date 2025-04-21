package grafioschtrader.connector.instrument;

import java.io.IOException;
import java.text.ParseException;
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
 * Connector interface for data provider.
 *
 */
public interface IFeedConnector {

  /**
   * Return which Feeds are supported
   */
  public enum FeedSupport {
    FS_HISTORY, FS_INTRA, FS_DIVIDEND, FS_SPLIT
  }

  public enum FeedIdentifier {
    // Supports currency without using extended url
    CURRENCY,
    // Supports currency only with using extended url
    CURRENCY_URL,
    // Supports currency without using extended url
    SECURITY,
    // Supports currency only with using extended url
    SECURITY_URL,
    // Supports dividend without using extended url
    DIVIDEND,
    // Supports dividend only with using extended url
    DIVIDEND_URL,
    // Supports split without using extended url
    SPLIT,
    // Supports split only with using extended url
    SPLIT_URL
  }

  public enum UrlCheck {
    INTRADAY, HISTORY
  }

  /**
   * The user can view the content that is downloaded via the data source.
   * Normally a URL is returned, which is opened in the browser. However, the
   * creation of this URL can take a long time, so lazy creation should be
   * possible. On the other hand, the return of a URL may not be sufficient
   * because it has to be expanded dynamically.
   */
  public enum DownloadLink {
    // The download link for historical price data must be requested in an
    // additional request.
    DL_LAZY_HISTORY,
    // The download link for intraday price data must be requested in an additional
    // request.
    DL_LAZY_INTRA,
    // As with data sources with API keys, the content of a data source for intraday
    // price data must be created in the backend.
    DL_HISTORY_FORCE_BACKEND,
    // As with data sources with API keys, the content of a data source for
    // historical price data must be created in the backend.
    DL_INTRA_FORCE_BACKEND
  }

  @Schema(description = "Id of the connector as it is used in the database")
  String getID();

  @Schema(description = "Id of the connector without prefix")
  String getShortID();

  @Schema(description = "The display name of the connector ")
  String getReadableName();

  @JsonIgnore
  boolean isActivated();

  Map<FeedSupport, FeedIdentifier[]> getSecuritycurrencyFeedSupport();

  @Schema(description = "Shows a help text to the connector which can be shown in the user interface")
  Description getDescription();

  /**
   * Returns true if this connector supports currency data regardless of
   * historical or last price.
   *
   * @return
   */
  boolean supportsCurrency();

  /**
   * Returns true if this connector supports security data regardless of
   * historical or last price.
   *
   * @return
   */
  boolean supportsSecurity();

  /**
   * Get the historical download link for a security
   *
   * @param security
   * @return
   */
  String getSecurityHistoricalDownloadLink(Security security);

  boolean hasFeedIndentifier(FeedIdentifier feedIdentifier);

  /**
   * A URL can be checked with a regex pattern. A valid regex pattern is no
   * guarantee that the data provider will offer the corresponding instrument.
   * Therefore, the check can be extended with a connection to the data provider
   * with the corresponding instrument. The return code is checked for HTTP_OK.
   * HTTP_OK does not guarantee that the data provider will also deliver the
   * corresponding data. The content of the return body must also be evaluated for
   * this. This must be specially implemented for each supplier. An unsuccessful
   * check must throw an error, which appears on the user interface.
   *
   * @param <S>
   * @param securitycurrency
   * @param feedSupport
   */
  <S extends Securitycurrency<S>> void checkAndClearSecuritycurrencyUrlExtend(Securitycurrency<S> securitycurrency,
      FeedSupport feedSupport);

  /**
   * Get the intraday download link for a security.
   *
   * @param security
   * @return
   */
  String getSecurityIntradayDownloadLink(Security security);

  /**
   * Return the url as string for access the historical currency price data. It
   * may also be used in the front end to check the settings.
   *
   * @param currencypair
   * @return
   */
  String getCurrencypairHistoricalDownloadLink(Currencypair currencypair);

  /**
   * Return the url as string for access the historical security price data. It
   * may also be used in the frontend to check the settings.
   *
   * @param currencypair
   * @return
   */
  String getCurrencypairIntradayDownloadLink(Currencypair currencypair);

  /**
   * Returns the Ticker, ISIN or WKN when one of this support the load of the
   * data.
   *
   * @param feedSupport
   * @return
   */
  FeedIdentifier[] getSecuritycurrencyFeedSupport(final FeedSupport feedSupport);

  /**
   * Certain connectors only provide end-of-day prices for certain securities if
   * trading has also taken place on that day.
   *
   * @param security
   * @return
   */
  boolean needHistoricalGapFiller(final Security security);

  /**
   * Return the security quotes for a specified period
   *
   * @param identifier
   * @param from
   * @param to
   * @param timeSpan
   * @return
   * @throws Exception
   */
  List<Historyquote> getEodSecurityHistory(Security security, Date from, Date to) throws Exception;

  /**
   * Update the security with last price, volume and so on
   *
   * @param security
   * @throws Exception
   * @throws HttpException
   */
  void updateSecurityLastPrice(Security security) throws Exception;

  /**
   * Delays in seconds of data provider for intraday data
   *
   * @return
   */
  int getIntradayDelayedSeconds();

  /**
   * Return the currency pair quotes for a specified period
   *
   * @param currency
   * @param from
   * @param to
   * @throws IOException
   * @throws ParseException
   */
  List<Historyquote> getEodCurrencyHistory(Currencypair currencyPair, Date from, Date to) throws Exception;

  /**
   * Updates the last price of a currency pair
   *
   * @param currencyPair
   * @throws IOException
   * @throws ParseException
   */
  void updateCurrencyPairLastPrice(Currencypair currencyPair) throws Exception;

  /**
   * Returns true if dividends are split adjusted
   *
   * @return
   */
  @JsonIgnore
  boolean isDividendSplitAdjusted();

  /*
   * Return the url as string for access the historical dividend data. It may also
   * be used in the front end to check the settings.
   */
  String getDividendHistoricalDownloadLink(Security security);

  /**
   * Get dividends for a security from a specified date until now. The list must
   * be sorted in ascending order by date.
   *
   * @param security
   * @param fromDate
   * @return Dividends sorted in ascending order by date.
   * @throws Exception
   */
  List<Dividend> getDividendHistory(Security security, LocalDate fromDate) throws Exception;

  /**
   * The link to get Split data for a security
   *
   * @param security
   * @return
   */
  String getSplitHistoricalDownloadLink(Security security);

  /**
   * Sometimes it takes a few days for a split to be reflected in the historical
   * price data. This gives you the number of days to wait before the next attempt
   * to check the historical quotes should be made. If the split is too far in the
   * past, no check should take place.
   *
   * @param splitDate
   * @return
   */
  Integer getNextAttemptInDaysForSplitHistorical(Date splitDate);

  /**
   * Returns which data provider the content of the links for the frontend must be
   * created in the backend. Normally, the frontend receives a URL that can be
   * opened directly in the browser. This is not possible for data providers with
   * API keys, so the data content is prepared in the backend. There are other
   * cases where the data content must be created in the backend.
   *
   * @return
   */
  EnumSet<DownloadLink> isDownloadLinkCreatedLazy();

  /**
   * The user interface receives a link to check the price data provider of a
   * security. If an API key is required, only the backend can evaluate this link
   * and return the corresponding content. The content of the provider may also be
   * determined in the backend for other reasons.
   *
   * @param httpPageUrl
   * @return
   */
  String getContentOfPageRequest(String httpPageUrl);

  /**
   * Get split data for a security from a specified day until now.
   *
   * @param security
   * @param fromDate
   * @return
   * @throws Exception
   */
  List<Securitysplit> getSplitHistory(Security security, LocalDate fromDate, LocalDate toDate) throws Exception;

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
