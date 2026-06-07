package grafioschtrader.connector.calendar;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import grafioschtrader.test.start.GTforTest;

@SpringBootTest(classes = GTforTest.class)
@ActiveProfiles("prod")
public class SplitCalendarAppenderTest {

  @Autowired
  private SplitCalendarAppender splitCalendarAppender;

  @Test
  void appendSecuritySplitsUntilTodayTest() {
    splitCalendarAppender.appendSecuritySplitsUntilToday();
  }

}
