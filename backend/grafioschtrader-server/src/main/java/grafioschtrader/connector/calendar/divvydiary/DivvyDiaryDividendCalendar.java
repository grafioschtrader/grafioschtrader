package grafioschtrader.connector.calendar.divvydiary;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import grafioschtrader.connector.calendar.IDividendCalendarFeedConnector;
import grafioschtrader.connector.instrument.FeedConnectorHelper;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@Component
public class DivvyDiaryDividendCalendar implements IDividendCalendarFeedConnector {

  private final String baseUrl;

  /** Creates the public dividend calendar connector, which does not require an API key. */
  public DivvyDiaryDividendCalendar() {
    this("https://api.divvydiary.com/dividends/calendar");
  }

  /** @param baseUrl calendar endpoint, allowing HTTP tests without provider access */
  DivvyDiaryDividendCalendar(String baseUrl) {
    this.baseUrl = baseUrl;
  }

  @Override
  public List<CalendarDividends> getExDateDividend(LocalDate exDate) throws Exception {
    List<CalendarDividends> calDividends = new ArrayList<>();
    ObjectMapper objectMapper = JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
    String urlStr = baseUrl + "?prefDate=exDate&month=" + exDate.getMonthValue() + "&year=" + exDate.getYear() + "&day="
        + exDate.getDayOfMonth();
    // DivvyDiary rejects the default Java user agent of URL.openStream() with HTTP 403.
    var response = FeedConnectorHelper.getByHttpClient(urlStr);
    if (response.statusCode() != 200) {
      throw new IOException("HTTP " + response.statusCode() + " for " + urlStr);
    }
    DivvyDiaryDividendsHeader ddDividends = objectMapper.readValue(response.body(), DivvyDiaryDividendsHeader.class);
    for (DivvyDiaryDividends ddDividend : ddDividends.dividends) {
      var cd = new CalendarDividends(ddDividend.name, ddDividend.exDate, ddDividend.payDate, ddDividend.amount);
      cd.isin = ddDividend.isin;
      cd.currency = ddDividend.currency;
      calDividends.add(cd);
    }
    return calDividends;
  }

  @Override
  public int getPriority() {
    return 10;
  }

  @Override
  public boolean supportISIN() {
    return true;
  }

  private static class DivvyDiaryDividendsHeader {
    public DivvyDiaryDividends[] dividends;
  }

  private static class DivvyDiaryDividends {
    public String name;
    public LocalDate exDate;
    public LocalDate payDate;
    public String isin;
    public double amount;
    public String currency;
  }

}
