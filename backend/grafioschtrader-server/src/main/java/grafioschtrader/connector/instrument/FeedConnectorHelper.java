package grafioschtrader.connector.instrument;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.apache.commons.lang3.math.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import grafiosch.common.DateHelper;
import grafioschtrader.GlobalConstants;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitycurrency;
import grafioschtrader.types.SpecialInvestmentInstruments;

/**
 * Utility class providing helper methods for feed connectors to process financial data
 * from various data providers. This class contains static methods for parsing numbers
 * in different locale formats, making HTTP requests, and processing financial data responses.
 */
public class FeedConnectorHelper {

  private static final Logger log = LoggerFactory.getLogger(FeedConnectorHelper.class);

  /**
   * Parses a German-formatted numeric string to Double. German format uses period (.) as thousands separator and comma
   * (,) as decimal separator. Example: "1.234,56" becomes 1234.56
   * 
   * @param item the German-formatted numeric string to parse
   * @return the parsed Double value, or null if the trimmed string is empty
   * @throws NumberFormatException if the string cannot be parsed as a valid number
   */
  public static Double parseDoubleGE(String item) {
    final String text = item.replace(".", "").replace(",", ".");
    return text.trim().length() > 0 ? Double.parseDouble(text) : null;
  }

  /**
   * Checks whether a German-formatted numeric string can be successfully parsed. German format uses period (.) as
   * thousands separator and comma (,) as decimal separator.
   * 
   * @param item the German-formatted numeric string to validate
   * @return true if the string can be parsed as a valid number, false otherwise
   */
  public static boolean isCreatableGE(String item) {
    final String text = item.replace(".", "").replace(",", ".");
    return NumberUtils.isCreatable(text);
  }

  /**
   * Parses a US-formatted numeric string to Double. US format uses comma (,) as thousands separator and period (.) as
   * decimal separator. Example: "1,234.56" becomes 1234.56
   * 
   * @param item the US-formatted numeric string to parse
   * @return the parsed Double value, or null if the trimmed string is empty
   * @throws NumberFormatException if the string cannot be parsed as a valid number
   */
  public static Double parseDoubleUS(String item) {
    final String text = item.replace(",", "");
    return text.trim().length() > 0 ? Double.parseDouble(text) : null;
  }

  /**
   * Parses a Swiss-formatted numeric string to Double. Swiss format uses apostrophe (') as thousands separator and
   * period (.) as decimal separator. Example: "1'234.56" becomes 1234.56
   * 
   * @param item the Swiss-formatted numeric string to parse
   * @return the parsed Double value, or null if the trimmed string is empty
   * @throws NumberFormatException if the string cannot be parsed as a valid number
   */
  public static Double parseDoubleCH(String item) {
    final String text = item.replace("’", "");
    return text.trim().length() > 0 ? Double.parseDouble(text) : null;
  }

  /**
   * Parses a Swiss-formatted numeric string to Long. Swiss format uses apostrophe (') as thousands separator. Example:
   * "1'234'567" becomes 1234567
   * 
   * @param item the Swiss-formatted numeric string to parse
   * @return the parsed Long value, or null if the trimmed string is empty
   * @throws NumberFormatException if the string cannot be parsed as a valid number
   */
  public static Long parseLongCH(String item) {
    final String text = item.replace("’", "");
    return text.trim().length() > 0 ? Long.parseLong(text) : null;
  }

  /**
   * Parses a German-formatted numeric string to Long. German format uses period (.) as thousands separator. Example:
   * "1.234.567" becomes 1234567
   * 
   * @param item the German-formatted numeric string to parse
   * @return the parsed Long value, or null if the trimmed string is empty
   * @throws NumberFormatException if the string cannot be parsed as a valid number
   */
  public static Long parseLongGE(String item) {
    final String text = item.replace(".", "");
    return text.trim().length() > 0 ? Long.parseLong(text) : null;
  }

  /**
   * Performs an HTTP GET request using the default HTTP client configuration. Uses standard user agent and no
   * connection timeout.
   * 
   * @param urlStr the URL to request
   * @return the HTTP response containing the response body as a string
   * @throws IOException          if an I/O error occurs during the request
   * @throws InterruptedException if the operation is interrupted
   */
  public static HttpResponse<String> getByHttpClient(String urlStr) throws IOException, InterruptedException {
    return getByHttpClient(urlStr, null, false);
  }

  /**
   * Performs an HTTP GET request with optional random user agent. Uses no connection timeout but allows customization
   * of the user agent string.
   * 
   * @param urlStr         the URL to request
   * @param useRandomAgent true to use a randomized user agent, false for standard user agent
   * @return the HTTP response containing the response body as a string
   * @throws IOException          if an I/O error occurs during the request
   * @throws InterruptedException if the operation is interrupted
   */
  public static HttpResponse<String> getByHttpClient(String urlStr, boolean useRandomAgent)
      throws IOException, InterruptedException {
    return getByHttpClient(urlStr, null, useRandomAgent);
  }

  /**
   * Performs an HTTP GET request with configurable connection timeout. Uses standard user agent with the specified
   * timeout value.
   * 
   * @param urlStr  the URL to request
   * @param seconds the connection timeout in seconds, or null for no timeout
   * @return the HTTP response containing the response body as a string
   * @throws IOException          if an I/O error occurs during the request
   * @throws InterruptedException if the operation is interrupted
   */
  public static HttpResponse<String> getByHttpClient(String urlStr, Integer seconds)
      throws IOException, InterruptedException {
    return getByHttpClient(urlStr, seconds, false);
  }

  /**
   * Performs an HTTP GET request with full configuration options. Allows specification of both connection timeout and
   * user agent randomization.
   * 
   * @param urlStr        the URL to request
   * @param seconds       the connection timeout in seconds, or null for no timeout
   * @param useRadomAgent true to use a randomized user agent, false for standard user agent
   * @return the HTTP response containing the response body as a string
   * @throws IOException          if an I/O error occurs during the request
   * @throws InterruptedException if the operation is interrupted
   */
  public static HttpResponse<String> getByHttpClient(String urlStr, Integer seconds, boolean useRadomAgent)
      throws IOException, InterruptedException {
    HttpClient client = (seconds != null) ? HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(seconds)).build()
        : HttpClient.newHttpClient();

    HttpRequest request = HttpRequest.newBuilder().GET().header("User-Agent", getHttpAgentAsString(useRadomAgent))
        .uri(URI.create(urlStr)).build();
    return client.send(request, HttpResponse.BodyHandlers.ofString());
  }

  /**
   * Generates a user agent string for HTTP requests. Can provide either a standard user agent or a randomized version
   * to avoid detection.
   * 
   * @param useRadomAgent true to generate a randomized user agent with random numbers, false to use the standard
   *                      application user agent
   * @return the user agent string to use in HTTP headers
   */
  public static String getHttpAgentAsString(boolean useRadomAgent) {
    return (useRadomAgent)
        ? GlobalConstants.USER_AGENT_HTTPCLIENT_SHORT + "(" + ThreadLocalRandom.current().nextInt(100000, 999999) + ")"
        : GlobalConstants.USER_AGENT_HTTPCLIENT;
  }

  /**
   * Parses a single line of German-formatted financial data response into a Historyquote object. Expected format is
   * semicolon-separated values: date;open;high;low;close;volume Only creates a Historyquote if the parsed date falls
   * within the specified date range.
   * 
   * @param inputLine  the semicolon-separated line to parse
   * @param from       the start date of the acceptable range (inclusive)
   * @param to         the end date of the acceptable range (inclusive)
   * @param dateFormat the SimpleDateFormat to use for parsing the date field
   * @return a new Historyquote object if the date is within range, null otherwise
   * @throws ParseException if the date field cannot be parsed using the provided format
   */
  public static Historyquote parseResponseLineGE(final String inputLine, final Date from, final Date to,
      final SimpleDateFormat dateFormat) throws ParseException {
    Historyquote historyquote = null;
    final String[] item = inputLine.split(";", -1);

    Date readDate = dateFormat.parse(item[0]);
    if (readDate.getTime() >= from.getTime() && readDate.getTime() <= to.getTime()) {
      historyquote = new Historyquote();
      historyquote.setDate(readDate);
      historyquote.setOpen(FeedConnectorHelper.parseDoubleGE(item[1]));
      historyquote.setHigh(FeedConnectorHelper.parseDoubleGE(item[2]));
      historyquote.setLow(FeedConnectorHelper.parseDoubleGE(item[3]));
      historyquote.setClose(FeedConnectorHelper.parseDoubleGE(item[4]));

      historyquote.setVolume(FeedConnectorHelper.parseLongGE(item[5]));
    }
    return historyquote;
  }

  /**
   * Validates and cleans a list of history quotes by removing entries that fall outside the specified date range.
   * Checks only the first and last entries for efficiency, as the list is expected to be sorted. Logs warnings when
   * quotes are removed.
   * 
   * @param fromDate       the start date of the acceptable range (inclusive)
   * @param toDate         the end date of the acceptable range (inclusive)
   * @param historyquotes  the list of history quotes to validate and clean
   * @param instrumentName the name of the financial instrument for logging purposes
   * @return the cleaned list with out-of-range quotes removed
   */
  public static List<Historyquote> checkFirstLastHistoryquoteAndRemoveWhenOutsideDateRange(Date fromDate, Date toDate,
      List<Historyquote> historyquotes, String instrumentName) {
    for (int i = 0; !historyquotes.isEmpty() && i < historyquotes.size(); i += Math.max(historyquotes.size() - 1, 1)) {
      Historyquote historyquote = historyquotes.get(i);
      var fromDateCheck = DateHelper.setTimeToZeroAndAddDay(fromDate, 0);
      if (historyquote.getDate().before(fromDateCheck) || historyquote.getDate().after(toDate)) {
        log.warn("Removed historyquote with date {} from instrument {}. Date range was {}-{}", historyquote.getDate(),
            instrumentName, fromDate, toDate);
        historyquotes.remove(i);
      }
    }
    return historyquotes;
  }

  /**
   * Calculates the price divider for London Stock Exchange securities quoted in GBX (pence). UK securities are often
   * quoted in pence rather than pounds, requiring division by 100 to convert to the correct currency unit. This method
   * determines whether such conversion is needed based on the security's exchange, currency, and instrument type.
   * 
   * @param securitycurrency the security or currency pair to check
   * @param <T>              the type of security currency (Security or Currencypair)
   * @return 100.0 if the security requires GBX to GBP conversion, 1.0 otherwise
   */
  public static <T extends Securitycurrency<T>> double getGBXLondonDivider(T securitycurrency) {
    if (securitycurrency instanceof Security security) {
      return security.getAssetClass()
          .getSpecialInvestmentInstrument() != SpecialInvestmentInstruments.NON_INVESTABLE_INDICES
          && GlobalConstants.STOCK_EX_MIC_UK.equals(security.getStockexchange().getMic())
          && security.getCurrency().equals(GlobalConstants.MC_GBP) ? 100.0 : 1.0;
    }
    return 1.0;
  }

}
