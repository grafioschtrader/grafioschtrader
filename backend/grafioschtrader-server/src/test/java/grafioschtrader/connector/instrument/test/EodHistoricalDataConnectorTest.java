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

import grafioschtrader.connector.instrument.eodhistoricaldata.EodHistoricalDataConnector;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Dividend;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitysplit;
import grafioschtrader.test.start.GTforTest;
import grafioschtrader.types.SpecialInvestmentInstruments;

@SpringBootTest(classes = GTforTest.class)
public class EodHistoricalDataConnectorTest {

  @Autowired
  private EodHistoricalDataConnector eodHistoricalDataConnector;
  
  final DateTimeFormatter germanFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
      .withLocale(Locale.GERMAN);
  
  @Test
  void getEodSecurityHistoryTest() {
  
    final List<Security> securities = new ArrayList<>();
    
    final LocalDate from = LocalDate.parse("03.01.2000", germanFormatter);
    final LocalDate to = LocalDate.parse("26.01.2022", germanFormatter);

    final Date fromDate = Date.from(from.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
    final Date toDate = Date.from(to.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
    securities.add(ConnectorTestHelper.createHistoricalSecurity("Cisco Systems", "csco", SpecialInvestmentInstruments.DIRECT_INVESTMENT, "NAS"));
    securities.add(ConnectorTestHelper.createHistoricalSecurity("iShares SMIM ETF (CH)", "CSSMIM.SW", SpecialInvestmentInstruments.ETF, "SIX"));
    securities.add(ConnectorTestHelper.createHistoricalSecurity("ZKB Gold ETF (CHF)", "ZGLD.SW", SpecialInvestmentInstruments.ETF, "SIX"));
      securities.parallelStream().forEach(security -> {
      List<Historyquote> historyquote = new ArrayList<>();
      try {
        historyquote = eodHistoricalDataConnector.getEodSecurityHistory(security, fromDate, toDate);
      } catch (final Exception e) {
        e.printStackTrace();
      }
      System.out.println("Ticker=" + security.getUrlHistoryExtend() + " Historyquote-Size=" + historyquote.size());
      assertThat(historyquote.size()).isGreaterThan(300);
    });
  }
  
  
  @Test
  void getEodCurrencyHistoryTest() {
    final LocalDate from = LocalDate.parse("2000-01-01");
    final LocalDate to = LocalDate.parse("2022-01-28");
    final Date fromDate = Date.from(from.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
    final Date toDate = Date.from(to.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
  
    
    final List<Currencypair> currencies = new ArrayList<>();
    currencies.add(ConnectorTestHelper.createCurrencyPair("ZAR", "NOK"));
    currencies.add(ConnectorTestHelper.createCurrencyPair("USD", "CHF"));
    currencies.add(ConnectorTestHelper.createCurrencyPair("JPY", "SEK"));
    currencies.add(ConnectorTestHelper.createCurrencyPair("BTC", "USD"));
    currencies.parallelStream().forEach(currencyPair -> {
      List<Historyquote> historyquote = new ArrayList<>();
      try {
        historyquote = eodHistoricalDataConnector.getEodCurrencyHistory(currencyPair, fromDate, toDate);
      } catch (Exception e) {
        e.printStackTrace();
      }
      System.out.println(historyquote.size());
      assertThat(historyquote.size()).isGreaterThan(3400);
    });
  }
  
  @Test
  void updateSecurityLastPriceTest() {
    final List<Security> securities = new ArrayList<>();
    securities.add(ConnectorTestHelper.createIntraSecurity("Cisco Systems", "csco", SpecialInvestmentInstruments.DIRECT_INVESTMENT, "NAS"));
    securities.parallelStream().forEach(security -> {
      try {
        eodHistoricalDataConnector.updateSecurityLastPrice(security);
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
        eodHistoricalDataConnector.updateCurrencyPairLastPrice(currencyPair);
        System.out.println(currencyPair);
      } catch (final Exception e) {
        e.printStackTrace();
      }
      assertThat(currencyPair.getSLast()).isNotNull().isGreaterThan(0.0);
    });
  }
  
  @Test
  void getDividendHistoryTest() {
    final DateTimeFormatter germanFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(Locale.GERMAN);
    final LocalDate from = LocalDate.parse("03.01.2000", germanFormatter);

    final List<Security> securities = new ArrayList<>();
    securities.add(ConnectorTestHelper.createDividendSecurity("Nestlé", "NESN.SW"));
    securities.add(ConnectorTestHelper.createDividendSecurity("Apple", "AAPL"));
    securities.add(ConnectorTestHelper.createDividendSecurity("iShares 20+ Year Treasury Bond", "TLT"));

    securities.parallelStream().forEach(security -> {
      List<Dividend> dividens = new ArrayList<>();
      try {
        dividens = eodHistoricalDataConnector.getDividendHistory(security, from);
      } catch (final Exception e) {
        e.printStackTrace();
      }
      assertThat(dividens.size()).isGreaterThanOrEqualTo(1);
    });
  }
  
  @Test
  void getSplitsTest() {
    final DateTimeFormatter germanFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(Locale.GERMAN);
    final LocalDate from = LocalDate.parse("03.01.2000", germanFormatter);

    final List<Security> securities = new ArrayList<>();
    securities.add(ConnectorTestHelper.createSplitSecurity("Nestlé", "NESN.SW"));
    securities.add(ConnectorTestHelper.createSplitSecurity("Apple", "AAPL"));

    securities.parallelStream().forEach(security -> {
      List<Securitysplit> seucritysplitList = new ArrayList<>();
      try {
        seucritysplitList = eodHistoricalDataConnector.getSplitHistory(security, from);
      } catch (final Exception e) {
        e.printStackTrace();
      }
      seucritysplitList.forEach(System.out::println);
      assertThat(seucritysplitList.size()).isGreaterThanOrEqualTo(1);
    });

  }
  
  
  
}
