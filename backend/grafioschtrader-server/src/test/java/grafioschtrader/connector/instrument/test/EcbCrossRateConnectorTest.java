package grafioschtrader.connector.instrument.test;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import grafioschtrader.GlobalConstants;
import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.connector.instrument.ecb.EcbCrossRateConnector;
import grafioschtrader.connector.instrument.test.ConnectorTestHelper.CurrencyPairHistoricalDate;
import grafioschtrader.test.start.GTforTest;

@SpringBootTest(classes = GTforTest.class)
public class EcbCrossRateConnectorTest extends BaseFeedConnectorCheck {

  @Autowired
  private EcbCrossRateConnector ecbCrossRateConnector;

  @Test
  void getEodCurrencyHistoryTest() throws ParseException {
    getEodCurrencyHistory();
  }

  @Override
  protected List<CurrencyPairHistoricalDate> getHistoricalCurrencies() {
    String oldestDate = "2000-01-04";
    String youngFromDate = "2025-01-03";
    String toDate = "2025-01-13";

    final List<CurrencyPairHistoricalDate> currencies = new ArrayList<>();
    try {
      currencies.add(new CurrencyPairHistoricalDate("ZAR", "NOK", 7, youngFromDate, toDate));
      currencies.add(
          new CurrencyPairHistoricalDate(GlobalConstants.MC_USD, GlobalConstants.MC_JPY, 7, youngFromDate, toDate));
      currencies.add(new CurrencyPairHistoricalDate("ZAR", "NOK", 6406, oldestDate, toDate));
      currencies.add(new CurrencyPairHistoricalDate(GlobalConstants.MC_EUR, GlobalConstants.MC_CHF, 6400, oldestDate,
          youngFromDate));
      currencies.add(new CurrencyPairHistoricalDate(GlobalConstants.MC_CHF, GlobalConstants.MC_USD, 6400, oldestDate,
          youngFromDate));
      currencies.add(new CurrencyPairHistoricalDate(GlobalConstants.MC_USD, GlobalConstants.MC_CHF, 6400, oldestDate,
          youngFromDate));
      currencies.add(new CurrencyPairHistoricalDate(GlobalConstants.MC_USD, GlobalConstants.MC_JPY, 6400, oldestDate,
          youngFromDate));

    } catch (ParseException pe) {
      pe.printStackTrace();
    }
    return currencies;
  }

  @Override
  protected IFeedConnector getIFeedConnector() {
    return ecbCrossRateConnector;
  }

}
