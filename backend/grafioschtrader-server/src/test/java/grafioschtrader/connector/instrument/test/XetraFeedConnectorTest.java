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

import grafioschtrader.connector.xetra.XetraFeedConnector;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;


public class XetraFeedConnectorTest {

  private XetraFeedConnector xetraFeedConnector = new XetraFeedConnector();
  
  @Test
  void getEodSecurityHistoryTest() {
  
    final List<Security> securities = new ArrayList<>();
    final DateTimeFormatter germanFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(Locale.GERMAN);
    
    
    final LocalDate from = LocalDate.parse("03.01.2000", germanFormatter);
    final LocalDate to = LocalDate.parse("26.01.2022", germanFormatter);

    final Date fromDate = Date.from(from.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
    final Date toDate = Date.from(to.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
    securities.add(ConnectorTestHelper.createHistoricalSecurity("Deutsche Lufthansa AG 4,382% 15/75", "XS1271836600"));
    securities.add(ConnectorTestHelper.createHistoricalSecurity("Beiersdorf Aktiengesellschaft", "DE0005200000"));
    securities.add(ConnectorTestHelper.createHistoricalSecurity("iShares Core MSCI World UCITS ETF", "IE00B4L5Y983"));
    securities.add(ConnectorTestHelper.createHistoricalSecurity("Delivery Hero SE", "DE000A2E4K43"));
    
    
      securities.parallelStream().forEach(security -> {
      List<Historyquote> historyquote = new ArrayList<>();
      try {
        historyquote = xetraFeedConnector.getEodSecurityHistory(security, fromDate, toDate);
      } catch (final Exception e) {
        e.printStackTrace();
      }
      System.out.println("Ticker=" + security.getUrlHistoryExtend() + " Historyquote-Size=" + historyquote.size());
      assertThat(historyquote.size()).isGreaterThan(1155);
    });
  }
}
