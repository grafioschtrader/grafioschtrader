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

import grafioschtrader.connector.instrument.boursorama.BoursoramaFeedConnector;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.types.SpecialInvestmentInstruments;


public class BoursoramaFeedConnectorTest {


  private BoursoramaFeedConnector boursoramaFeedConnector = new BoursoramaFeedConnector();

  @Test
  void getEodSecurityHistoryTest() {

    final List<Security> securities = new ArrayList<>();
    final DateTimeFormatter germanFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(Locale.GERMAN);

    final LocalDate from = LocalDate.parse("03.01.2003", germanFormatter);
    final LocalDate to = LocalDate.parse("26.01.2022", germanFormatter);

    final Date fromDate = Date.from(from.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
    final Date toDate = Date.from(to.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
    securities.add(ConnectorTestHelper.createHistoricalSecurity("Cisco Systems", "CSCO", SpecialInvestmentInstruments.DIRECT_INVESTMENT, "NAS"));
    securities.add(ConnectorTestHelper.createHistoricalSecurity("iShares SMIM ETF (CH)", "2aCSSMIM", SpecialInvestmentInstruments.ETF, "SIX"));
    securities.add(ConnectorTestHelper.createHistoricalSecurity("ZKB Gold ETF (CHF)", "2aZGLD", SpecialInvestmentInstruments.ETF, "SIX"));
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
    currencies.add(ConnectorTestHelper.createCurrencyPair("BTC", "USD", "9xXBTUSDSPOT"));
    
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
    securities.add(ConnectorTestHelper.createIntraSecurity("2 AEVIS 22 BDS", "2aAEV16",  SpecialInvestmentInstruments.DIRECT_INVESTMENT, "SIX"));
    securities.add(ConnectorTestHelper.createIntraSecurity("Xtrackers II Eurozone Government Bond UCITS ETF 1C", "1zDBXN",  SpecialInvestmentInstruments.ETF, "FSX"));
    securities.add(ConnectorTestHelper.createIntraSecurity("iShares Core CHF Corporate Bond ETF (CH)", "2aCHCORP",  SpecialInvestmentInstruments.ETF, "SIX"));
    securities.add(ConnectorTestHelper.createIntraSecurity("iShares SMIM ETF (CH)", "2aCSSMIM",  SpecialInvestmentInstruments.ETF, "SIX"));
    securities.add(ConnectorTestHelper.createIntraSecurity("ZKB Gold ETF (CHF)", "2aZGLD",  SpecialInvestmentInstruments.ETF, "SIX"));

    securities.parallelStream().forEach(security -> {
      try {
        boursoramaFeedConnector.updateSecurityLastPrice(security);
        System.out.println(security);
      } catch (final Exception e) {
        e.printStackTrace();
      }
      // A price is not always set
      assertThat(security.getSTimestamp()).isNotNull();
    });
  }

  @Test
  void updateCurrencyPairLastPriceTest() {
    final List<Currencypair> currencies = new ArrayList<>();
    currencies.add(ConnectorTestHelper.createIntraCurrencyPair("JPY", "USD", "3fJPY_USD"));
    currencies.add(ConnectorTestHelper.createIntraCurrencyPair("CAD", "EUR", "3fCAD_EUR"));
    currencies.add(ConnectorTestHelper.createIntraCurrencyPair("CHF", "GBP", "3fCHF_GBP"));
    currencies.add(ConnectorTestHelper.createIntraCurrencyPair("BTC", "USD", "9xXBTUSDSPOT"));
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
