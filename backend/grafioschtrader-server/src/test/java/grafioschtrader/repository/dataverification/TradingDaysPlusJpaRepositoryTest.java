package grafioschtrader.repository.dataverification;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import grafioschtrader.entities.TradingDaysPlus;
import grafioschtrader.repository.TradingDaysPlusJpaRepository;
import grafioschtrader.test.start.GTforTest;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = GTforTest.class)
class TradingDaysPlusJpaRepositoryTest {

  @Autowired
  private TradingDaysPlusJpaRepository tradingDaysPlusJpaRepository;

  @Test
  void findTradingDaysTest() {
    LocalDate fromDate = LocalDate.parse("2020-11-30");
    LocalDate toDate = LocalDate.parse("2020-12-04");
    List<TradingDaysPlus> tradingDays = tradingDaysPlusJpaRepository.findByTradingDateBetweenOrderByTradingDate(fromDate, toDate);
    assertEquals(tradingDays.size(), 5);
  }

}
