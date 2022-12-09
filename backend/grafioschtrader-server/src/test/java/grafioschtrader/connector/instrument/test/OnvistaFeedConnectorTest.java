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
    currencies.add(ConnectorTestHelper.createHistoricalCurrencyPair("GBP", "USD", "CURRENCY/GBPUSD/eod_history?idNotation=1305587"));
    final LocalDate from = LocalDate.parse("03.01.2000", germanFormatter);
    final Date fromDate = Date.from(from.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
    final LocalDate to = LocalDate.parse("27.07.2022", germanFormatter);
    Date toDate = Date.from(to.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
    currencies.parallelStream().forEach(currencyPair -> {
      List<Historyquote> historyquote = new ArrayList<>();
      try {
        historyquote = onvistaConnector.getEodCurrencyHistory(currencyPair, fromDate, toDate);
      } catch (Exception e) {
        e.printStackTrace();
      }
      assertThat(historyquote.size()).isEqualByComparingTo(7169);
    });
  }

  @Test
  void getEodSecurityHistoryTest() {

    final List<Security> securities = new ArrayList<>();

    final DateTimeFormatter germanFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(Locale.GERMAN);
    final LocalDate from = LocalDate.parse("03.01.2000", germanFormatter);
    final LocalDate to = LocalDate.parse("27.07.2022", germanFormatter);

    final Date fromDate = Date.from(from.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
    final Date toDate = Date.from(to.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());

    securities.add(createSecurity("Siemens", "STOCK/82902/eod_history?idNotation=1929749", 5826));
    securities.add(createSecurity("iShares Core DAX", "FUND/3567527/eod_history?idNotation=28520648", 3476));
    securities.add(createSecurity("BGF World Energy Fund I2 USD", "FUND/20982583/eod_history?idNotation=26071169", 3142));
    securities.add(createSecurity("Amazon", "STOCK/90929/eod_history?idNotation=9386187", 4631));
    securities.add(createSecurity("Autoneum Holding AG SF-Anl. 2017(25)", "BOND/130304815/eod_history?idNotation=202439144", 1079));
        
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
