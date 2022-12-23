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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import grafioschtrader.connector.instrument.twelvedata.TwelvedataFeedConnector;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.test.start.GTforTest;
import grafioschtrader.types.SpecialInvestmentInstruments;

@SpringBootTest(classes = GTforTest.class)
public class TwelvedataFeedConnectorTest {

  @Autowired
  private TwelvedataFeedConnector twelvedataFeedConnector;

  final DateTimeFormatter germanFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
      .withLocale(Locale.GERMAN);

  @Test
  void getEodSecurityHistoryTest() {

    final List<Security> securities = new ArrayList<>();

    final LocalDate from = LocalDate.parse("03.01.2000", germanFormatter);
    final LocalDate to = LocalDate.parse("26.01.2022", germanFormatter);

    final Date fromDate = Date.from(from.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
    final Date toDate = Date.from(to.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
    securities.add(ConnectorTestHelper.createHistoricalSecurity("Cisco Systems", "csco",
        SpecialInvestmentInstruments.DIRECT_INVESTMENT, "NAS"));
    securities.add(ConnectorTestHelper.createHistoricalSecurity("SPDR S&P 500 ETF Trust", "SPY",
        SpecialInvestmentInstruments.ETF, "NYSE"));
    securities.add(ConnectorTestHelper.createHistoricalSecurity("General Electric Company", "GE",
        SpecialInvestmentInstruments.ETF, "NYSE"));
    securities.parallelStream().forEach(security -> {
      List<Historyquote> historyquote = new ArrayList<>();
      try {
        historyquote = twelvedataFeedConnector.getEodSecurityHistory(security, fromDate, toDate);
      } catch (final Exception e) {
        e.printStackTrace();
      }
      System.out.println("Ticker=" + security.getUrlHistoryExtend() + " Historyquote-Size=" + historyquote.size());
      assertThat(historyquote.size()).isGreaterThan(5550);
    });
  }
  
  @Test
  void getEodCurrencyHistoryTest() {
    final LocalDate from = LocalDate.parse("2000-01-03");
    final LocalDate to = LocalDate.parse("2022-01-28");
    final Date fromDate = Date.from(from.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
    final Date toDate = Date.from(to.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
  
    
    final List<Currencypair> currencies = new ArrayList<>();
    currencies.add(ConnectorTestHelper.createCurrencyPair("ZAR", "NOK"));
    currencies.add(ConnectorTestHelper.createCurrencyPair("USD", "CHF"));
    currencies.add(ConnectorTestHelper.createCurrencyPair("JPY", "SEK"));
    // currencies.add(ConnectorTestHelper.createCurrencyPair("BTC", "USD"));
    currencies.parallelStream().forEach(currencyPair -> {
      List<Historyquote> historyquote = new ArrayList<>();
      try {
        historyquote = twelvedataFeedConnector.getEodCurrencyHistory(currencyPair, fromDate, toDate);
      } catch (Exception e) {
        e.printStackTrace();
      }
      System.out.println(historyquote.size());
      assertThat(historyquote.size()).isGreaterThan(1000);
    });
  }
  
  @Test
  void updateSecurityLastPriceTest() {
    final List<Security> securities = new ArrayList<>();
    securities.add(ConnectorTestHelper.createIntraSecurity("Cisco Systems", "csco", SpecialInvestmentInstruments.DIRECT_INVESTMENT, "NAS"));
    securities.parallelStream().forEach(security -> {
      try {
        twelvedataFeedConnector.updateSecurityLastPrice(security);
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
    currencies.add(ConnectorTestHelper.createCurrencyPair("USD", "CHF"));
    currencies.add(ConnectorTestHelper.createCurrencyPair("ZAR", "CHF"));
    currencies.add(ConnectorTestHelper.createCurrencyPair("USD", "GBP"));
    currencies.parallelStream().forEach(currencyPair -> {
      try {
        twelvedataFeedConnector.updateCurrencyPairLastPrice(currencyPair);
        System.out.println(currencyPair);
      } catch (final Exception e) {
        e.printStackTrace();
      }
      assertThat(currencyPair.getSLast()).isNotNull().isGreaterThan(0.0);
    });
  }
  
}
