package grafioschtrader.connector.instrument.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import grafioschtrader.connector.instrument.investing.InvestingConnector;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Security;

class InvestingConnectorTest {

  private InvestingConnector investingConnector = new InvestingConnector();

  @Test
  void updateSecurityLastPriceTest() {
    final List<Security> securities = new ArrayList<>();

    securities.add(ConnectorTestHelper.createIntraSecurity("CAC 40", "indices/france-40-chart"));
    securities.add(ConnectorTestHelper.createIntraSecurity("MOEX Russia (IMOEX)", "indices/mcx"));
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
      System.out
          .println(currencyPair.getFromCurrency() + "/" + currencyPair.getToCurrency() + ":" + currencyPair.getSLast());
      assertThat(currencyPair.getSLast()).isNotNull().isGreaterThan(0.0);
    });
  }

  

}
