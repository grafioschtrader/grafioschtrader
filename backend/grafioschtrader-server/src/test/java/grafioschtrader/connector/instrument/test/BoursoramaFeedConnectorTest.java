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

import grafioschtrader.connector.boursorama.BoursoramaFeedConnector;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.test.start.GTforTest;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = GTforTest.class)
public class BoursoramaFeedConnectorTest {

  @Autowired
  private BoursoramaFeedConnector boursoramaFeedConnector;

  @Test
  void getEodSecurityHistoryTest() {

    final List<Security> securities = new ArrayList<>();
    final DateTimeFormatter germanFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(Locale.GERMAN);

    final LocalDate from = LocalDate.parse("03.01.2003", germanFormatter);
    final LocalDate to = LocalDate.parse("26.01.2022", germanFormatter);

    final Date fromDate = Date.from(from.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
    final Date toDate = Date.from(to.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
    securities.add(ConnectorTestHelper.createHistoricalSecurity("Cisco Systems", "CSCO"));
    securities.add(ConnectorTestHelper.createHistoricalSecurity("iShares SMIM ETF (CH)", "2aCSSMIM"));
    securities.add(ConnectorTestHelper.createHistoricalSecurity("ZKB Gold ETF (CHF)", "2aZGLD"));
    securities.parallelStream().forEach(security -> {
      List<Historyquote> historyquote = new ArrayList<>();
      try {
        historyquote = boursoramaFeedConnector.getEodSecurityHistory(security, fromDate, toDate);
      } catch (final Exception e) {
        e.printStackTrace();
      }
      System.out.println("Ticker=" + security.getUrlHistoryExtend() + " Historyquote-Size=" + historyquote.size());
      assertThat(historyquote.size()).isGreaterThan(300);
    });
  }

  @Test
  void getEodCurrencyHistoryTest() {
    final DateTimeFormatter germanFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(Locale.GERMAN);

    final LocalDate from = LocalDate.parse("03.01.2003", germanFormatter);
    final LocalDate to = LocalDate.parse("26.01.2022", germanFormatter);

    final Date fromDate = Date.from(from.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
    final Date toDate = Date.from(to.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());

    final List<Currencypair> currencies = new ArrayList<>();
    currencies.add(ConnectorTestHelper.createCurrencyPair("JPY", "USD", "3fJPY_USD"));
    currencies.add(ConnectorTestHelper.createCurrencyPair("CAD", "EUR", "3fCAD_EUR"));
    currencies.add(ConnectorTestHelper.createCurrencyPair("CHF", "GBP", "3fCHF_GBP"));
    currencies.parallelStream().forEach(currencyPair -> {
      List<Historyquote> historyquote = new ArrayList<>();
      try {
        historyquote = boursoramaFeedConnector.getEodCurrencyHistory(currencyPair, fromDate, toDate);
      } catch (Exception e) {
        e.printStackTrace();
      }
      System.out.println(historyquote.size());
      assertThat(historyquote.size()).isGreaterThan(2680);
    });
  }

  @Test
  void updateSecurityLastPriceTest() {
    final List<Security> securities = new ArrayList<>();
    securities.add(ConnectorTestHelper.createIntraSecurity("iShares Core CHF Corporate Bond ETF (CH)", "2aCHCORP"));
    securities.add(ConnectorTestHelper.createIntraSecurity("iShares SMIM ETF (CH)", "2aCSSMIM"));
    securities.add(ConnectorTestHelper.createIntraSecurity("ZKB Gold ETF (CHF)", "2aZGLD"));

    securities.parallelStream().forEach(security -> {
      try {
        boursoramaFeedConnector.updateSecurityLastPrice(security);
        System.out.println(security);
      } catch (final Exception e) {
        e.printStackTrace();
      }
      assertThat(security.getSLast()).isNotNull().isGreaterThan(0.0);
    });
  }

  @Test
  void updateCurrencyPairLastPriceTest() {
    final List<Currencypair> currencies = new ArrayList<>();
    currencies.add(ConnectorTestHelper.createCurrencyPair("JPY", "USD", "3fJPY_USD"));
    currencies.add(ConnectorTestHelper.createCurrencyPair("CAD", "EUR", "3fCAD_EUR"));
    currencies.add(ConnectorTestHelper.createCurrencyPair("CHF", "GBP", "3fCHF_GBP"));
    currencies.parallelStream().forEach(currencyPair -> {
      try {
        boursoramaFeedConnector.updateCurrencyPairLastPrice(currencyPair);
        System.out.println(currencyPair);
      } catch (final Exception e) {
        e.printStackTrace();
      }
      assertThat(currencyPair.getSLast()).isNotNull().isGreaterThan(0.0);
    });
  }

}
