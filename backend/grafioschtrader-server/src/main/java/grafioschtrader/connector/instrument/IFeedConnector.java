package grafioschtrader.connector.instrument;

import java.io.IOException;
import java.text.ParseException;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.http.HttpException;

import com.fasterxml.jackson.annotation.JsonIgnore;

// import org.apache.http.HttpException;

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
 * @author Hugo Graf
 */
public interface IFeedConnector {

  /**
   * Return which Feeds are supported
   */
  public enum FeedSupport {
    HISTORY, INTRA, DIVIDEND, SPLIT
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
  public boolean needHistoricalGapFiller(final Security security);

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
   * Get split data for a security from a specified day until now.
   *
   * @param security
   * @param fromDate
   * @return
   * @throws Exception
   */
  List<Securitysplit> getSplitHistory(Security security, LocalDate fromDate) throws Exception;

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
