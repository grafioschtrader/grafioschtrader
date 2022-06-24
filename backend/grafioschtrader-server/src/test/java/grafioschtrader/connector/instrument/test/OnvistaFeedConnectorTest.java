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

import grafioschtrader.connector.instrument.onvista.OnvistaFeedConnector;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;

class OnvistaFeedConnectorTest {

  private OnvistaFeedConnector onvistaConnector = new OnvistaFeedConnector();
  
  @Test
  void getEodCurrencyHistoryTest() {
    final DateTimeFormatter germanFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(Locale.GERMAN);
   
    final List<Currencypair> currencies = new ArrayList<>();
    currencies.add(ConnectorTestHelper.createHistoricalCurrencyPair("EUR", "USD", "8381868"));
    final LocalDate from = LocalDate.parse("01.12.2017", germanFormatter);
    final Date fromDate = Date.from(from.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
    final LocalDate to = LocalDate.parse("21.05.2021", germanFormatter);
    Date toDate = Date.from(to.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
    currencies.parallelStream().forEach(currencyPair -> {
      List<Historyquote> historyquote = new ArrayList<>();
      try {
        historyquote = onvistaConnector.getEodCurrencyHistory(currencyPair, fromDate, toDate);
      } catch (Exception e) {
        e.printStackTrace();
      }
     
      assertThat(historyquote.size()).isEqualByComparingTo(1268);
    });
  }

  @Test
  void getEodSecurityHistoryTest() {

    final List<Security> securities = new ArrayList<>();

    final DateTimeFormatter germanFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(Locale.GERMAN);
    final LocalDate from = LocalDate.parse("03.01.2000", germanFormatter);
    final LocalDate to = LocalDate.parse("10.06.2021", germanFormatter);

    final Date fromDate = Date.from(from.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
    final Date toDate = Date.from(to.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());

    securities.add(createSecurity("Siemens", "1929749", 5441));
    securities.add(createSecurity("iShares Core DAX", "2027396", 5186));
    securities.add(createSecurity("green benefit Global Impact Fund I", "125961454", 1578));
    securities.add(createSecurity("Amazon", "9386187", 4246));
    
    
    securities.parallelStream().forEach(security -> {

      List<Historyquote> historyquote = new ArrayList<>();
      try {
        historyquote = onvistaConnector.getEodSecurityHistory(security, fromDate, toDate);
      } catch (final Exception e) {
        e.printStackTrace();
      }
      System.out.println(security.getName() +  " Size: " + historyquote.size());
      assertThat(historyquote.size()).isEqualTo(security.getDenomination());
    });
  }

  private Security createSecurity(final String name, final String url, final int expectedRows) {
    final Security security = new Security();
    security.setName(name);
    security.setUrlIntraExtend(url);
    security.setUrlHistoryExtend(url);
    security.setDenomination(expectedRows);
    return security;
  }

}
