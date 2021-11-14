package grafioschtrader.connector.instrument.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import grafioschtrader.connector.instrument.divvydiary.DivvyDiaryConnector;
import grafioschtrader.entities.Dividend;
import grafioschtrader.entities.Security;

class DivvyDiaryConnectorTest {

  @Test
  void getDividendHistoryTest() {
    DivvyDiaryConnector divvyDiaryConnector = new DivvyDiaryConnector();
    LocalDate fromDate = LocalDate.parse("2008-01-01");
    List<Security> securities = new ArrayList<>();
    securities.add(createSecurity("iShares SLI", "CH0031768937"));
  
    securities.parallelStream().forEach(security -> {
      try {
        List<Dividend> dividends = divvyDiaryConnector.getDividendHistory(security, fromDate);
        dividends.forEach(System.out::println);
        assertThat(dividends.size()).isGreaterThanOrEqualTo(6);
      } catch (Exception e) {
        e.printStackTrace();
      }
    });
  }

  private Security createSecurity(String name, String isin) {
    Security security = new Security();
    security.setName(name);
    security.setIsin(isin);
    return security;
  }
}
