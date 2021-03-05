package grafioschtrader.connector.instrument.test;

import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import grafioschtrader.connector.instrument.swissquote.SwissquoteFeedConnector;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Security;

class SwissquoteFeedConnectorTest {

  @Test
  void updateSecurityLastPriceTest() {
    final List<Security> securities = new ArrayList<>();

    final SwissquoteFeedConnector swissquoteConnector = new SwissquoteFeedConnector();

    securities.add(createSecurity("SLI"));
    securities.add(createSecurity("CH0183135976"));
    securities.add(createSecurity("LU0322252924"));
    securities.add(createSecurity("IE00B3VWMM18"));

    securities.parallelStream().forEach(security -> {
      try {
        swissquoteConnector.updateSecurityLastPrice(security);
      } catch (final Exception e) {
        e.printStackTrace();
      }
      System.out.println(security);
      if (security.getSLow() != null && security.getSHigh() != null) {

        assertTrue(security.getSLast() > 0.0 && security.getSLast() >= security.getSLow()
            && security.getSLast() <= security.getSHigh());
      } else {
        assertTrue(security.getSLast() > 0.0);
      }
    });
  }

  @Test
  void updateCurrencyPairLastPriceTest() {

    final SwissquoteFeedConnector swissquoteConnector = new SwissquoteFeedConnector();

    final List<Currencypair> currencies = new ArrayList<>();
    currencies.add(ConnectorTestHelper.createCurrencyPair("EUR", "CHF"));
    currencies.add(ConnectorTestHelper.createCurrencyPair("USD", "CHF"));
    currencies.add(ConnectorTestHelper.createCurrencyPair("USD", "EUR"));

    currencies.parallelStream().forEach(currencyPair -> {
      try {
        swissquoteConnector.updateCurrencyPairLastPrice(currencyPair);
      } catch (final Exception e) {
        e.printStackTrace();
      }
      assertTrue(currencyPair.getSLast() > 0.0);
    });
  }

  private Security createSecurity(final String intraTicker) {
    final Security security = new Security();
    security.setUrlIntraExtend(intraTicker);
    return security;
  }

}
