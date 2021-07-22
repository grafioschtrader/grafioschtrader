package grafioschtrader.connector.calendar;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import grafioschtrader.test.start.GTforTest;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = GTforTest.class)
public class SplitCalendarAppenderTest {

  @Autowired
  private SplitCalendarAppender splitCalendarAppender;
  
  @Test
  void appendSecuritySplitsUntilTodayTest() {
    splitCalendarAppender.appendSecuritySplitsUntilToday();
  }
  
}
