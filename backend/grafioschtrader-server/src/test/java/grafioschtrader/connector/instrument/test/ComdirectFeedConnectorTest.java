package grafioschtrader.connector.instrument.test;

import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import grafioschtrader.GlobalConstants;
import grafioschtrader.connector.instrument.comdirect.ComdirectFeedConnector;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Security;

public class ComdirectFeedConnectorTest {

  private ComdirectFeedConnector comdirectConnector = new ComdirectFeedConnector();

  @Test
  void updateSecurityLastPriceTest() {
    final List<Security> securities = new ArrayList<>();

    securities.add(ConnectorTestHelper.createIntraSecurity("Xtrackers MSCI Japan UCITS ETF - 7C CHF ACC H ETF",
        "etfs/LU1215827756?ID_NOTATION=169541045"));
    securities.add(ConnectorTestHelper.createIntraSecurity("iShares SLI ETF (CH)",
        "fonds/detail/uebersicht.html?ID_NOTATION=18354672&SEARCH_REDIRECT=true&REFERER=search.general&REDIRECT_TYPE=ISIN&SEARCH_VALUE=CH0031768937"));
    securities.add(ConnectorTestHelper.createIntraSecurity("HOCHDORF Holding AG",
        "aktien/detail/uebersicht.html?ID_NOTATION=46383779&SEARCH_REDIRECT=true&REFERER=search.general&REDIRECT_TYPE=ISIN&SEARCH_VALUE=CH0024666528"));
    securities.add(ConnectorTestHelper.createIntraSecurity("adidas",
        "aktien/DE000A1EWWW0?ID_NOTATION=39471840"));
    securities.parallelStream().forEach(security -> {
      try {
        comdirectConnector.updateSecurityLastPrice(security);
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
    currencies.add(ConnectorTestHelper.createIntraCurrencyPair(GlobalConstants.MC_EUR, GlobalConstants.MC_CHF,
        "waehrungen/euro-schweizer_franken-kurs"));

    currencies.parallelStream().forEach(currencyPair -> {
      try {
        comdirectConnector.updateCurrencyPairLastPrice(currencyPair);
        System.out.println(currencyPair);
        assertTrue(currencyPair.getSLast() > 0.0);
      } catch (final Exception e) {
        e.printStackTrace();
      }
    });
  }

}
