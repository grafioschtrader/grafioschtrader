package grafioschtrader.connector.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.Test;

import grafioschtrader.connector.calendar.ICalendarFeedConnector.TickerSecuritysplit;
import grafioschtrader.connector.calendar.investing.InvestingCalendar;

class InvestingCalendarTest {
  
  @Test
  void calendarSplitTest() {
    InvestingCalendar investingCalendar = new InvestingCalendar();
    // String [] countryCodes = {"CH", "US", "DE", "FR", "ES", "IT", "GB", "AT", "JP", "CN", "SE", "NO", "DK", "NL", "BR", "CA"};
    String [] countryCodes = {"CH", "US", "CA"};
    Map<String, TickerSecuritysplit> tickersMap = null;
    try {
      tickersMap = investingCalendar.getCalendarSplitForSingleDay(LocalDate.parse("2021-02-16"), countryCodes);
    } catch (Exception e) {
      e.printStackTrace();
    }
    assertThat(tickersMap).hasSize(4);
  }
}
