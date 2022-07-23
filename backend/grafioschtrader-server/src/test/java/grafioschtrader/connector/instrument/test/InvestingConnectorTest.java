package grafioschtrader.connector.instrument.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertTrue;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import grafioschtrader.connector.instrument.investing.InvestingConnector;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;

class InvestingConnectorTest {

  private InvestingConnector investingConnector = new InvestingConnector();

  @Test
  void updateSecurityLastPriceTest() {
    final List<Security> securities = new ArrayList<>();
    

    securities.add(ConnectorTestHelper.createIntraSecurity("MOEX Russia (IMOEX)",
        "indices/mcx"));
    securities.add(ConnectorTestHelper.createIntraSecurity("Bitcoin Tracker EUR XBT Provider (SE0007525332)",
        "etfs/bitcoin-tracker-eur-xbt-provider"));
    securities.add(ConnectorTestHelper.createIntraSecurity("Apple Inc (AAPL)", "equities/apple-computer-inc"));
    securities.add(ConnectorTestHelper.createIntraSecurity("iShares MSCI Emerging Markets ETF (EEM)",
        "etfs/ishares-msci-emg-markets"));
    securities.add(ConnectorTestHelper.createIntraSecurity("MOEX Russia (IMOEX)", "indices/mcx"));

    securities.parallelStream().forEach(security -> {
      try {
        investingConnector.updateSecurityLastPrice(security);
        System.out.println(security);
        assertTrue(security.getSLast() > 0.0);
      } catch (final Exception e) {
        e.printStackTrace();
      }
    });
  }

  @Test
  void updateCurrencyPairLastPriceTest() {

    final List<Currencypair> currencies = new ArrayList<>();
    currencies.add(ConnectorTestHelper.createCurrencyPair("EUR", "CHF"));
    currencies.add(ConnectorTestHelper.createCurrencyPair("BTC", "USD"));
    currencies.add(ConnectorTestHelper.createCurrencyPair("CHF", "AUD"));
    currencies.add(ConnectorTestHelper.createCurrencyPair("USD", "CHF"));
    currencies.add(ConnectorTestHelper.createCurrencyPair("USD", "EUR"));
    currencies.add(ConnectorTestHelper.createCurrencyPair("GBP", "EUR"));

    currencies.parallelStream().forEach(currencyPair -> {
      try {
        investingConnector.updateCurrencyPairLastPrice(currencyPair);
      } catch (final Exception e) {
        e.printStackTrace();
      }
      System.out.println(currencyPair.getFromCurrency() +  "/" + currencyPair.getToCurrency() + ":" + currencyPair.getSLast()); 
      assertThat(currencyPair.getSLast()).isNotNull().isGreaterThan(0.0);
    });
  }

  @Test
  void getEodCurrencyPairHistoryTest() {
    final List<Currencypair> currencies = new ArrayList<>();
    final DateTimeFormatter germanFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(Locale.GERMAN);

    final LocalDate from = LocalDate.parse("03.01.2000", germanFormatter);
    final LocalDate to = LocalDate.parse("10.05.2021", germanFormatter);

    final Date fromDate = Date.from(from.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
    final Date toDate = Date.from(to.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());

    currencies.add(ConnectorTestHelper.createHistoricalCurrencyPair("CNY", "CHF",
        "currencies/cny-chf-historical-data,9495,111486"));
    currencies.add(ConnectorTestHelper.createHistoricalCurrencyPair("BTC", "CHF",
        "indices/investing.com-btc-chf-historical-data,1117720,2207960"));
    currencies.add(ConnectorTestHelper.createHistoricalCurrencyPair("USD", "CHF", "currencies/usd-chf,4,106685"));

    currencies.parallelStream().forEach(currencypair -> {
      List<Historyquote> historyquote = new ArrayList<>();
      try {
        historyquote = investingConnector.getEodCurrencyHistory(currencypair, fromDate, toDate);
      } catch (final Exception e) {
        e.printStackTrace();
      }
      System.out.println(historyquote.size());
      assertTrue(historyquote.size() > 10);
    });
  }

  @Test
  void getEodSecurityHistoryTest() {
    final DateTimeFormatter germanFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(Locale.GERMAN);
    final List<Security> securities = new ArrayList<>();

    final LocalDate from = LocalDate.parse("01.01.2003", germanFormatter);
    final LocalDate to = LocalDate.parse("10.05.2021", germanFormatter);

    final Date fromDate = Date.from(from.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
    final Date toDate = Date.from(to.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());

    securities.add(ConnectorTestHelper.createHistoricalSecurity("Enel", "equities/enel-historical-data,6963,1160404"));
    securities.add(ConnectorTestHelper.createHistoricalSecurity("S&P 500 (SPX)",
        "indices/us-spx-500-historical-data,166,2030167"));
    securities.add(ConnectorTestHelper.createHistoricalSecurity("SMI Futures - Jun 19",
        "indices/switzerland-20-futures,8837,500048"));
    securities.add(ConnectorTestHelper.createHistoricalSecurity("USA 30-Year Bond Yiel",
        "rates-bonds/u.s.-30-year-bond-yield,23706,200657"));

    securities.parallelStream().forEach(security -> {
      List<Historyquote> historyquote = new ArrayList<>();
      try {
        historyquote = investingConnector.getEodSecurityHistory(security, fromDate, toDate);
      } catch (final Exception e) {
        e.printStackTrace();
      }
      System.out.println(historyquote.size());
      assertTrue(historyquote.size() > 10);
    });
  }

}
