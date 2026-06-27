package grafioschtrader.connector.calendar;

import java.time.LocalDate;
import java.util.List;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import grafioschtrader.connector.calendar.IDividendCalendarFeedConnector.CalendarDividends;
import grafioschtrader.connector.calendar.divvydiary.DivvyDiaryDividendCalendar;

public class DivvyDiaryDividendCalendarTest {

  @Test
  void calendarDividendTest() {
    var divvyDiaryDividendCalendar = new DivvyDiaryDividendCalendar();

    try {
      List<CalendarDividends> cd = divvyDiaryDividendCalendar.getExDateDividend(LocalDate.parse("2026-03-06"));
      Assertions.assertThat(cd).hasSize(172);
    } catch (Exception e) {
      e.printStackTrace();
    }

  }
}
