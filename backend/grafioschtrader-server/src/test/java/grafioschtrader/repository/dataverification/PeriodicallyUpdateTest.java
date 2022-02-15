package grafioschtrader.repository.dataverification;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.repository.DividendJpaRepository;
import grafioschtrader.test.start.GTforTest;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = GTforTest.class)
@Transactional
public class PeriodicallyUpdateTest {
  
  @Autowired
  private DividendJpaRepository dividendJpaRepository;
  
  @Test
  @Rollback(false)
  void periodicallyUpdateTest() {
    List<String> errorMessages = dividendJpaRepository.periodicallyUpdate();
    assertTrue(errorMessages.isEmpty());
  }
}
