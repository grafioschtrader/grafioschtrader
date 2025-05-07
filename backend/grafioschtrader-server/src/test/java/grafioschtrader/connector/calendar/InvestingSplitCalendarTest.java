package grafioschtrader.connector.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.Test;

import grafioschtrader.connector.calendar.ISplitCalendarFeedConnector.TickerSecuritysplit;
import grafioschtrader.connector.calendar.investing.InvestingSplitCalendar;

class InvestingSplitCalendarTest {

  @Test
  void calendarSplitTest() {
    InvestingSplitCalendar investingCalendar = new InvestingSplitCalendar();
    // String [] countryCodes = {"CH", "US", "DE", "FR", "ES", "IT", "GB", "AT", "JP", "CN", "SE", "NO", "DK", "NL", "BR", "CA"};
    String [] countryCodes = {"CH", "US", "CA"};
    Map<String, TickerSecuritysplit> securitySplitMap = null;
    try {
      securitySplitMap = investingCalendar.getCalendarSplitForSingleDay(LocalDate.parse("2025-05-07"), countryCodes);
    } catch (Exception e) {
      e.printStackTrace();
    }
    securitySplitMap.entrySet().forEach(System.out::println);
    assertThat(securitySplitMap).hasSize(3);
  }
}
