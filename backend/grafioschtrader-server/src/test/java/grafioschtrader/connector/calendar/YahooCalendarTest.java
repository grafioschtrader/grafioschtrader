package grafioschtrader.connector.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.Test;

import grafioschtrader.connector.calendar.ICalendarFeedConnector.TickerSecuritysplit;
import grafioschtrader.connector.calendar.yahoo.YahooCalendar;

class YahooCalendarTest {

  @Test
  void calendarSplitTest() {
    YahooCalendar yahooCalendar = new YahooCalendar();
    Map<String, TickerSecuritysplit> securitySplitMap = null;
    try {
      securitySplitMap = yahooCalendar.getCalendarSplitForSingleDay(LocalDate.parse("2022-11-18"), new String[] {});
    } catch (Exception e) {
      e.printStackTrace();
    }
    assertThat(securitySplitMap).hasSize(8);
  }
  
}
