package grafioschtrader.connector.calendar.yahoo;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.NumberFormat;
import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.math3.fraction.Fraction;
import org.apache.commons.math3.fraction.FractionFormat;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import grafiosch.common.DateHelper;
import grafioschtrader.GlobalConstants;
import grafioschtrader.connector.calendar.ISplitCalendarFeedConnector;
import grafioschtrader.connector.yahoo.YahooHelper;
import grafioschtrader.entities.Securitysplit;
import grafioschtrader.types.CreateType;

/**
 * Table: stock suffix https://help.yahoo.com/kb/SLN2310.html
 */
@Component
public class YahooSplitCalendar implements ISplitCalendarFeedConnector {

  @Override
  public Map<String, TickerSecuritysplit> getCalendarSplitForSingleDay(LocalDate forDate, String[] countryCodes)
      throws Exception {
    LocalDate fromDate = forDate.minusDays(1);

    HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(
            YahooHelper.YAHOO_CALENDAR + "splits?from=" + fromDate + "&to=" + forDate + "&day=" + forDate))
        .headers("Content-Type", "application/x-www-form-urlencoded", "X-Requested-With", "XMLHttpRequest",
            "User-Agent", GlobalConstants.USER_AGENT_HTTPCLIENT)
        .GET().build();
    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    return parseSplitCalendar(Jsoup.parse(response.body()), forDate);
  }

  @Override
  public int getPriority() {
    return 20;
  }

  private Map<String, TickerSecuritysplit> parseSplitCalendar(Document doc, LocalDate forDate) {
    Map<String, TickerSecuritysplit> splitTickerMap = new HashMap<>();
    Elements rows = doc.select("#cal-res-table table tbody tr");
    FractionFormat fractionFormat = new FractionFormat(NumberFormat.getInstance(Locale.US));
    for (Element row : rows) {
      Elements cols = row.select("td");
      if (cols.get(4).text().trim().length() >= 3) {
        String yahooTicker = cols.get(0).text();
        int pos = yahooTicker.indexOf(".");
        if (pos > 0) {
          yahooTicker = yahooTicker.substring(0, pos);
        }
        splitTickerMap.putIfAbsent(yahooTicker,
            new TickerSecuritysplit(cols.get(1).text(), getSecuritySplit(cols.get(4).text(), forDate, fractionFormat)));
      }
    }

    return splitTickerMap;
  }

  private Securitysplit getSecuritySplit(String splitText, LocalDate forDate, FractionFormat fractionFormat) {
    String fractionStr = splitText.replace("-", "/").replaceAll("\\s+", "");
    Fraction fraction = fractionFormat.parse(fractionStr);
    return new Securitysplit(null, DateHelper.getDateFromLocalDate(forDate), fraction.getNumerator(),
        fraction.getDenominator(), CreateType.CONNECTOR_CREATED);
  }

}
