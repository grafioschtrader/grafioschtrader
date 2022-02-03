package grafioschtrader.connector.instrument.test;

import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Security;

public class ConnectorTestHelper {

  public static Currencypair createCurrencyPair(final String fromCurrency, final String toCurrency) {
    final Currencypair currencyPair = new Currencypair();
    currencyPair.setFromCurrency(fromCurrency);
    currencyPair.setToCurrency(toCurrency);
    return currencyPair;
  }

  public static Currencypair createIntraCurrencyPair(final String fromCurrency, final String toCurrency,
      final String urlIntraExtend) {
    return createIntraHistoricalCurrencyPair(fromCurrency, toCurrency, urlIntraExtend, null);
  }
  
  public static Currencypair createHistoricalCurrencyPair(final String fromCurrency, final String toCurrency,
      final String urlUrlHistoryExtend) {
    return createIntraHistoricalCurrencyPair(fromCurrency, toCurrency, null, urlUrlHistoryExtend);
  }

  private static Currencypair createIntraHistoricalCurrencyPair(final String fromCurrency, final String toCurrency,
      String urlIntraExtend, String urlHistoryExtend) {
    final Currencypair currencyPair = new Currencypair();
    currencyPair.setFromCurrency(fromCurrency);
    currencyPair.setToCurrency(toCurrency);
    currencyPair.setUrlIntraExtend(urlIntraExtend);
    currencyPair.setUrlHistoryExtend(urlHistoryExtend);
    return currencyPair;
  }

  public static Security createIntraSecurity(final String name, final String urlIntraExtend) {
    return createIntraHistoricalSecurity(name, urlIntraExtend, ExtendKind.INTRA);
  }
  
  
  public static Security createHistoricalSecurity(final String name, final String urlHistoryExtend) {
    return createIntraHistoricalSecurity(name, urlHistoryExtend, ExtendKind.EOD);
  }
  
  public static Security createDividendSecurity(final String name, final String urlDividendExtend) {
    return createIntraHistoricalSecurity(name, urlDividendExtend, ExtendKind.DIVIDEND);
  }
  
  public static Security createSplitSecurity(final String name, final String urlSplitExtend) {
    return createIntraHistoricalSecurity(name, urlSplitExtend, ExtendKind.SPLIT);
  }
  
  
  private static Security createIntraHistoricalSecurity(final String name, String urlExtend, ExtendKind extendKind) {
    final Security security = new Security();
    security.setName(name);
    switch(extendKind) {
    case EOD:
      security.setUrlHistoryExtend(urlExtend);
      break;
      
    case INTRA:
      security.setUrlIntraExtend(urlExtend);
      break;
      
    case DIVIDEND:
      security.setUrlDividendExtend(urlExtend);
      break;
      
    case SPLIT:
      security.setUrlSplitExtend(urlExtend);
      break; 
    }

  
    return security;
  }
  
  private static enum ExtendKind {
     EOD,
     INTRA,
     DIVIDEND,
     SPLIT;
  }


}
