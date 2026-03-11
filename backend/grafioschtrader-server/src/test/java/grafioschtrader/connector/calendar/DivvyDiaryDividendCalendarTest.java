package grafioschtrader.connector.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import grafioschtrader.connector.calendar.IDividendCalendarFeedConnector.CalendarDividends;
import grafioschtrader.connector.calendar.divvydiary.DivvyDiaryDividendCalendar;

public class DivvyDiaryDividendCalendarTest {

  @Test
  void calendarDividendTest() {
    var divvyDiaryDividendCalendar = new DivvyDiaryDividendCalendar();

    try {
      List<CalendarDividends> cd = divvyDiaryDividendCalendar.getExDateDividend(LocalDate.parse("2026-03-06"));
      assertThat(cd).hasSize(161);
    } catch (Exception e) {
      e.printStackTrace();
    }

  }
}
