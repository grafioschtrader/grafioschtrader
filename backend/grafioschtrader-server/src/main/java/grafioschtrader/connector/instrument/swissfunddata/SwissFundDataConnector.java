package grafioschtrader.connector.instrument.swissfunddata;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.text.ParseException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import grafioschtrader.GlobalConstants;
import grafioschtrader.connector.instrument.BaseFeedConnector;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;

/*-
 * Stock, Bond, ETF:
 * Migros Bank (CH) Fonds 45 V (CH0023406561)
 * https://www.swissfunddata.ch/sfdpub/de/funds/excelData/79260
 *
 * Dividend: Not Supported
 * Splits: Not Supported
 *
 */
@Component
public class SwissFundDataConnector extends BaseFeedConnector {

  private static Map<FeedSupport, FeedIdentifier[]> supportedFeed;
  private final Logger log = LoggerFactory.getLogger(this.getClass());
  private static final String URL_EXTENDED_REGEX = "^\\d+$";

  static {
    supportedFeed = new HashMap<>();
    supportedFeed.put(FeedSupport.HISTORY, new FeedIdentifier[] { FeedIdentifier.SECURITY_URL });
  }

  public SwissFundDataConnector() {
    super(supportedFeed, "swissfunddata", "Swiss Fund Data", URL_EXTENDED_REGEX);
  }

  @Override
  public String getSecurityHistoricalDownloadLink(final Security security) {
    return "https://www.swissfunddata.ch/sfdpub/de/funds/excelData/" + security.getUrlHistoryExtend();
  }

  @Override
  public List<Historyquote> getEodSecurityHistory(final Security security, final Date from, final Date to)
      throws Exception {
    final List<Historyquote> historyquotes = new ArrayList<>();
    LocalDate fromDate = LocalDate.ofInstant(from.toInstant(), ZoneId.systemDefault());
    LocalDate toDate = LocalDate.ofInstant(to.toInstant(), ZoneId.systemDefault());

    final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern(GlobalConstants.STANDARD_DATE_FORMAT);
    String urlAsString = getSecurityHistoricalDownloadLink(security);
    log.info("In {} for security {} is URL for download csv file {}", getID(), security.getName(), urlAsString);
    try (BufferedReader br = new BufferedReader(new InputStreamReader(new URL(urlAsString).openStream()))) {
      parseLines(historyquotes, br, dateFormatter, fromDate, toDate);
    }
    return historyquotes;
  }

  private void parseLines(final List<Historyquote> historyquotes, final BufferedReader br,
      final DateTimeFormatter dateFormatter, final LocalDate fromDate, final LocalDate toDate)
      throws IOException, ParseException {
    int skipLines = 2;
    String inputLine = br.readLine();

    while ((inputLine = br.readLine()) != null) {
      skipLines--;
      if (skipLines < 0) {
        final String[] item = inputLine.split(";");
        LocalDate localDate = LocalDate.parse(item[0], dateFormatter);
        if (localDate.isBefore(fromDate) || localDate.isAfter(toDate)) {
          continue;
        }
        final Historyquote historyquote = new Historyquote();
        historyquote.setDateLD(localDate);
        historyquote.setClose(Double.parseDouble(item[4].trim()));
        historyquotes.add(historyquote);
      }
    }
  }

}
