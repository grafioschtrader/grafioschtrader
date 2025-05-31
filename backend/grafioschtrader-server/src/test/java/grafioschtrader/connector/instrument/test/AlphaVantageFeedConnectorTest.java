package grafioschtrader.connector.instrument.test;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.connector.instrument.alphavantage.AlphaVantageFeedConnector;
import grafioschtrader.connector.instrument.test.ConnectorTestHelper.SecurityHistoricalDate;
import grafioschtrader.test.start.GTforTest;
import grafioschtrader.types.SubscriptionType;

@SpringBootTest(classes = GTforTest.class)
class AlphaVantageFeedConnectorTest extends BaseFeedConnectorCheck {

  private final AlphaVantageFeedConnector alphaVantageConnector;

  @Autowired
  public AlphaVantageFeedConnectorTest(AlphaVantageFeedConnector alphaVantageConnector) {
    this.alphaVantageConnector = alphaVantageConnector;
    assumeTrue(alphaVantageConnector.isActivated());
  }

  @Test
  void getEodSecurityHistoryTest() {
    if (alphaVantageConnector.getSubscriptionType() != SubscriptionType.ALPHA_VANTAGE_FREE) {
      getEodSecurityHistory(false);
    }
  }

  @Test
  void updateSecurityLastPriceTest() {
    if (alphaVantageConnector.getSubscriptionType() != SubscriptionType.ALPHA_VANTAGE_FREE) {
      updateSecurityLastPriceByHistoricalData();
    }
  }

  @Override
  protected List<SecurityHistoricalDate> getHistoricalSecurities() {
    List<SecurityHistoricalDate> hisoricalDate = new ArrayList<>();
    try {
      hisoricalDate.add(new SecurityHistoricalDate("Apple", "AAPL", 1000, "2010-01-03", "2025-05-16"));
      hisoricalDate.add(new SecurityHistoricalDate("Microsoft", "MSFT", 1000, "2010-01-03", "2025-05-16"));
      hisoricalDate
          .add(new SecurityHistoricalDate("Dow Jones Industrial Average", "^DJI", 1000, "2010-01-03", "2025-05-16"));
    } catch (ParseException pe) {
      pe.printStackTrace();
    }
    return hisoricalDate;
  }

  @Override
  protected IFeedConnector getIFeedConnector() {
    return alphaVantageConnector;
  }

}
