package grafioschtrader.connector.instrument.test;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import grafioschtrader.GlobalConstants;
import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.connector.instrument.cryptocompare.CryptoCompareFeedConnector;
import grafioschtrader.connector.instrument.test.ConnectorTestHelper.CurrencyPairHistoricalDate;
import grafioschtrader.test.start.GTforTest;

@SpringBootTest(classes = GTforTest.class)
class CryptoCompareFeedConnectorTest extends BaseFeedConnectorCheck {

  @Autowired
  private CryptoCompareFeedConnector cryptoCompareFeedConnector;

  @Test
  void getEodCurrencyHistoryTest() throws ParseException {
    getEodCurrencyHistory();
  }

  @Test
  void updateCurrencyPairLastPriceTest() {
    updateCurrencyPairLastPrice();
  }

  @Override
  protected List<CurrencyPairHistoricalDate> getHistoricalCurrencies() {
    final List<CurrencyPairHistoricalDate> currencies = new ArrayList<>();
    try {
      currencies.add(new CurrencyPairHistoricalDate(GlobalConstants.MC_CHF, GlobalConstants.CC_BTC, 5005, "2011-09-03",
          "2025-05-16"));
      currencies.add(new CurrencyPairHistoricalDate(GlobalConstants.CC_BTC, GlobalConstants.MC_USD, 5005, "2011-09-03",
          "2025-05-16"));
    } catch (ParseException pe) {
      pe.printStackTrace();
    }
    return currencies;
  }



  @Override
  protected IFeedConnector getIFeedConnector() {
    return cryptoCompareFeedConnector;
  }

}
