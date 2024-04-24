package grafioschtrader.connector.calendar.divvydiary;

import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import grafioschtrader.connector.calendar.IDividendCalendarFeedConnector;

@Component
public class DivvyDiaryDividendCalendar implements IDividendCalendarFeedConnector {

  private static String BASE_URL = "https://api.divvydiary.com/dividends/calendar";

  @Override
  public List<CalendarDividends> getExDateDividend(LocalDate exDate) throws Exception {
    List<CalendarDividends> calDividends = new ArrayList<>();
    ObjectMapper objectMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    objectMapper.registerModule(new JavaTimeModule());
    String urlStr = BASE_URL + "?prefDate=exDate&month=" + exDate.getMonthValue() + "&year=" + exDate.getYear()
        + "&day=" + exDate.getDayOfMonth();
    DivvyDiaryDividendsHeader ddDividends = objectMapper.readValue(new URI(urlStr).toURL(), DivvyDiaryDividendsHeader.class);
    for(DivvyDiaryDividends ddDividend: ddDividends.dividends) {
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
