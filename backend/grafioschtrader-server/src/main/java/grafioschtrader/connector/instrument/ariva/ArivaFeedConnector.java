package grafioschtrader.connector.instrument.ariva;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import grafioschtrader.GlobalConstants;
import grafioschtrader.common.DateHelper;
import grafioschtrader.connector.instrument.BaseFeedConnector;
import grafioschtrader.connector.instrument.FeedConnectorHelper;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;

@Component
public class ArivaFeedConnector extends BaseFeedConnector {

  /*-
   * Stocks, Bond, ETF:
   * Look https://forum.portfolio-performance.info/t/daten-kurs-fundamental-von-ariva-de-importieren/444
   * https://www.ariva.de/quote/historic/historic.csv?secu=102519136&boerse_id=6&clean_split=1&clean_payout=0&clean_bezug=1&min_time=14.1.2000&max_time=14.1.2017&trenner=%3B&go=Download
   * https://www.ariva.de/quote/historic/historic.csv?secu=102519136&boerse_id=12&clean_split=1&clean_payout=0&clean_bezug=1&min_time=14.1.2000&max_time=09.03.2018&trenner=%3B&go=Download
   * https://www.ariva.de/quote/historic/historic.csv?secu=144454172&boerse_id=144&clean_split=1&clean_payout=&clean_bezug=1&currency=CHF&min_time=18.5.2020&max_time=18.5.2021&trenner=%3B&go=Download
   *
   * Dividend:
   * Dividend data not supported because they are not exactly, and prices may be in EUR instead of the stock currency.
   *
   * Splits:
   * Splits are mixed with dividends, please refere to https://www.ariva.de/apple-aktie/historische_ereignisse
   */
  private static final String DATE_FORMAT_GE = "dd.MM.yyyy";
  private static final int TIMEOUT = 15000;
  private static final String URL_EXTENDED_REGEX = "^\\d+&boerse_id=\\d+(&currency=[A-Za-z]{3})?$";
  private static Map<FeedSupport, FeedIdentifier[]> supportedFeed;

  static {
    supportedFeed = new HashMap<>();
    supportedFeed.put(FeedSupport.HISTORY, new FeedIdentifier[] { FeedIdentifier.SECURITY_URL });
  }

  public ArivaFeedConnector() {
    super(supportedFeed, "ariva", "Ariva", URL_EXTENDED_REGEX);
  }

  @Override
  public String getSecurityHistoricalDownloadLink(final Security security) {
    final SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT_GE);
    Date toDate = new Date();
    LocalDate fromLocalDate = DateHelper.getLocalDate(toDate).minusDays(7);
    return getSecurityHistoricalDownloadLink(security, DateHelper.getDateFromLocalDate(fromLocalDate), toDate,
        dateFormat);
  }

  private String getSecurityHistoricalDownloadLink(final Security security, Date from, Date to, SimpleDateFormat sdf) {
    return "https://www.ariva.de/quote/historic/historic.csv?secu=" + security.getUrlHistoryExtend()
        + "&clean_split=1&clean_payout=0&clean_bezug=1&trenner=%3B&go=Download&min_time=" + sdf.format(from)
        + "&max_time=" + sdf.format(to);
  }

  @Override
  public List<Historyquote> getEodSecurityHistory(final Security security, final Date from, final Date to)
      throws Exception {
    final SimpleDateFormat dateFormatGE = new SimpleDateFormat(DATE_FORMAT_GE);
    final SimpleDateFormat dateFormatUS = new SimpleDateFormat(GlobalConstants.STANDARD_DATE_FORMAT);
    final List<Historyquote> historyquotes = new ArrayList<>();

    URL request = new URL(getSecurityHistoricalDownloadLink(security, from, to, dateFormatGE));
    URLConnection connection = request.openConnection();
    connection.setConnectTimeout(TIMEOUT);
    connection.setReadTimeout(TIMEOUT);
    try (InputStreamReader inputStream = new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8);
        BufferedReader bufferedReader = new BufferedReader(inputStream)) {

      String inputLine;
      while ((inputLine = bufferedReader.readLine()) != null) {
        if (inputLine.trim().length() == 0 || !Character.isDigit(inputLine.charAt(0))) {
          // First line throw away
          continue;
        }
        final Historyquote historyquote = FeedConnectorHelper.parseResponseLineGE(inputLine, from, to, dateFormatUS);
        historyquotes.add(historyquote);
      }
    }
    return historyquotes;
  }

}
