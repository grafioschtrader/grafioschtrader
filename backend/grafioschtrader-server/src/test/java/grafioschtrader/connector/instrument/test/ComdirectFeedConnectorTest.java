package grafioschtrader.connector.instrument.test;

import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import grafioschtrader.connector.instrument.comdirect.ComdirectFeedConnector;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Security;

public class ComdirectFeedConnectorTest {

  private ComdirectFeedConnector  comdirectConnector = new ComdirectFeedConnector();
  
  @Test
  void updateSecurityLastPriceTest() {
    final List<Security> securities = new ArrayList<>();
   
    
    securities.add(ConnectorTestHelper.createIntraSecurity("Xtrackers MSCI Japan UCITS ETF - 7C CHF ACC H ETF", "etfs/detail/uebersicht.html?ID_NOTATION=169541045&SEARCH_REDIRECT=true&REFERER=search.general&REDIRECT_TYPE=ISIN&SEARCH_VALUE=LU1215827756"));

    securities.add(ConnectorTestHelper.createIntraSecurity("CAC 40 Index", "indizes/werte/FR0003500008"));

    securities.add(ConnectorTestHelper.createIntraSecurity("Xtrackers MSCI Japan UCITS ETF", "etfs/detail/uebersicht.html?ID_NOTATION=169541045&SEARCH_REDIRECT=true&REFERER=search.general&REDIRECT_TYPE=ISIN&SEARCH_VALUE=LU1215827756"));
    securities.add(ConnectorTestHelper.createIntraSecurity("CAC 40 Index", "indizes/werte/FR0003500008"));
    securities.add(ConnectorTestHelper.createIntraSecurity("WIG INDEX (PLN)", "indizes/PL9999999995"));
    securities.add(ConnectorTestHelper.createIntraSecurity("iShares Core MSCI World UCITS ETF - USD ACC ETF",
        "etfs/IE00B4L5Y983"));
    securities.add(ConnectorTestHelper.createIntraSecurity("iShares Core MSCI World UCITS ETF - USD ACC ETF",
        "fonds/detail/uebersicht.html?ID_NOTATION=31345909&ISIN=IE00B4L5Y983"));
    securities.add(ConnectorTestHelper.createIntraSecurity("Crédit Agricole S.A. SF-Preferred MTN 2021(29)",
        "anleihen/detail/uebersicht.html?ID_NOTATION=342290402&SEARCH_REDIRECT=true&REFERER=search.general&REDIRECT_TYPE=ISIN&SEARCH_VALUE=CH1118460984"));

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
    currencies.add(ConnectorTestHelper.createIntraCurrencyPair("EUR", "CHF", "waehrungen/euro-schweizer_franken-kurs"));

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
