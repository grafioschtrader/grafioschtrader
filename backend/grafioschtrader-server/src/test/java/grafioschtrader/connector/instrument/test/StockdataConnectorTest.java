package grafioschtrader.connector.instrument.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import grafioschtrader.connector.instrument.stockdata.StockdataConnector;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.test.start.GTforTest;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = GTforTest.class)
public class StockdataConnectorTest {
  
  @Autowired
  private StockdataConnector stockdataConnector;
  
  final DateTimeFormatter germanFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
      .withLocale(Locale.GERMAN);
  
  @Test
  void getEodSecurityHistoryTest() {
  
    final List<Security> securities = new ArrayList<>();
    
    final LocalDate from = LocalDate.parse("03.01.2010", germanFormatter);
    final LocalDate to = LocalDate.parse("26.01.2022", germanFormatter);

    final Date fromDate = Date.from(from.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
    final Date toDate = Date.from(to.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
  
    // securities.add(ConnectorTestHelper.createHistoricalSecurity("Cisco Systems", "csco"));
    securities.add(ConnectorTestHelper.createHistoricalSecurity("iShares European Property Yield UCITS ETF (Dist) (EUR) ", "IQQP.DE"));
    
    
      securities.parallelStream().forEach(security -> {
      List<Historyquote> historyquote = new ArrayList<>();
      try {
        historyquote = stockdataConnector.getEodSecurityHistory(security, fromDate, toDate);
      } catch (final Exception e) {
        e.printStackTrace();
      }
      System.out.println("Ticker=" + security.getUrlHistoryExtend() + " Historyquote-Size=" + historyquote.size());
      assertThat(historyquote.size()).isGreaterThan(300);
    });
  }
  
}
