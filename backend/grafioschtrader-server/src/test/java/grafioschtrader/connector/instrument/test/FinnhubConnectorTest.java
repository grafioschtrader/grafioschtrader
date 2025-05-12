package grafioschtrader.connector.instrument.test;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.text.ParseException;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import grafioschtrader.GlobalConstants;
import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.connector.instrument.finnhub.FinnhubConnector;
import grafioschtrader.connector.instrument.test.ConnectorTestHelper.SecurityHistoricalDate;
import grafioschtrader.test.start.GTforTest;
import grafioschtrader.types.SpecialInvestmentInstruments;
import grafioschtrader.types.SubscriptionType;

@SpringBootTest(classes = GTforTest.class)
class FinnhubConnectorTest extends BaseFeedConnectorCheck {

  private final FinnhubConnector finnhubConnector;

  @Autowired
  public FinnhubConnectorTest(FinnhubConnector finnhubConnector) {
    this.finnhubConnector = finnhubConnector;
    assumeTrue(finnhubConnector.isActivated());
  }

  final DateTimeFormatter germanFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
      .withLocale(Locale.GERMAN);

  // Security price tests
  // =======================================
  @Test
  void updateSecurityLastPriceTest() {
    updateSecurityLastPriceByHistoricalData();
  }

  @Test
  void getEodSecurityHistoryTest() {
    if (finnhubConnector.getSubscriptionType() != SubscriptionType.FINNHUB_FREE) {
      getEodSecurityHistory(false);
    }
  }

  @Override
  protected List<SecurityHistoricalDate> getHistoricalSecurities() {
    List<SecurityHistoricalDate> hisoricalDate = new ArrayList<>();
    try {
      hisoricalDate.add(new SecurityHistoricalDate("Cisco", SpecialInvestmentInstruments.DIRECT_INVESTMENT, "csco",
          GlobalConstants.STOCK_EX_MIC_NASDAQ, GlobalConstants.MC_USD, 5926, "2000-01-03", "2025-05-09"));
    } catch (ParseException pe) {
      pe.printStackTrace();
    }
    return hisoricalDate;
  }

  /**
   * Finnhub.io only for Premium
   */
  @Test
  void getSplitsTest() throws ParseException {
    if (finnhubConnector.getSubscriptionType() != SubscriptionType.FINNHUB_FREE) {
      ConnectorTestHelper.standardSplitTest(finnhubConnector, null);
    }
  }

  

  @Override
  protected IFeedConnector getIFeedConnector() {
    return finnhubConnector;
  }
}
