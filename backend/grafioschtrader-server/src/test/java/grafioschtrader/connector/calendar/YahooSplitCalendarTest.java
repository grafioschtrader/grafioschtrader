package grafioschtrader.connector.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.Test;

import grafioschtrader.connector.calendar.ISplitCalendarFeedConnector.TickerSecuritysplit;
import grafioschtrader.connector.calendar.yahoo.YahooSplitCalendar;

class YahooSplitCalendarTest {

  @Test
  void calendarSplitTest() {
    YahooSplitCalendar yahooCalendar = new YahooSplitCalendar();
    Map<String, TickerSecuritysplit> securitySplitMap = null;
    try {
      securitySplitMap = yahooCalendar.getCalendarSplitForSingleDay(LocalDate.parse("2025-07-21"), new String[] {});
    } catch (Exception e) {
      e.printStackTrace();
    }
    securitySplitMap.entrySet().forEach(System.out::println);
    assertThat(securitySplitMap).hasSize(16);
  }

}
