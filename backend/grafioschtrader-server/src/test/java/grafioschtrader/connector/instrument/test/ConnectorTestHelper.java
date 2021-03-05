package grafioschtrader.connector.instrument.test;

import grafioschtrader.entities.Currencypair;

public class ConnectorTestHelper {

  public static Currencypair createCurrencyPair(final String fromCurrency, final String toCurrency) {
    final Currencypair currencyPair = new Currencypair();
    currencyPair.setFromCurrency(fromCurrency);
    currencyPair.setToCurrency(toCurrency);
    return currencyPair;
  }

  public static Currencypair createHistoricalCurrencyPair(final String fromCurrency, final String toCurrency,
      final String urlUrlHistoryExtend) {
    final Currencypair currencyPair = new Currencypair();
    currencyPair.setFromCurrency(fromCurrency);
    currencyPair.setToCurrency(toCurrency);
    currencyPair.setUrlHistoryExtend(urlUrlHistoryExtend);
    return currencyPair;
  }

}
