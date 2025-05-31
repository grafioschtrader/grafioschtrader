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
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.GlobalConstants;
import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.connector.instrument.stockdata.StockDataFeedConnector;
import grafioschtrader.connector.instrument.test.ConnectorTestHelper.CurrencyPairHistoricalDate;
import grafioschtrader.connector.instrument.test.ConnectorTestHelper.SecurityHistoricalDate;
import grafioschtrader.test.start.GTforTest;
import grafioschtrader.types.SpecialInvestmentInstruments;
import grafioschtrader.types.SubscriptionType;

@SpringBootTest(classes = GTforTest.class)
@Transactional
public class StockDataFeedConnectorTest extends BaseFeedConnectorCheck {

  private final StockDataFeedConnector stockdataConnector;

  @Autowired
  public StockDataFeedConnectorTest(StockDataFeedConnector stockdataConnector) {
    this.stockdataConnector = stockdataConnector;
    assumeTrue(stockdataConnector.isActivated());
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
    getEodSecurityHistory(true);

  }

  @Override
  protected List<SecurityHistoricalDate> getHistoricalSecurities() {
    List<SecurityHistoricalDate> hisoricalDate = new ArrayList<>();
    try {
      hisoricalDate.add(new SecurityHistoricalDate("Cisco", SpecialInvestmentInstruments.DIRECT_INVESTMENT, "csco",
          GlobalConstants.STOCK_EX_MIC_NASDAQ, GlobalConstants.MC_USD, 86, "2025-01-03", "2025-05-08"));
      if (stockdataConnector.getSubscriptionType() == SubscriptionType.STOCK_DATA_ORG_STANDARD_OR_PRO) {
        hisoricalDate.add(new SecurityHistoricalDate("Cisco", SpecialInvestmentInstruments.DIRECT_INVESTMENT, "csco",
            GlobalConstants.STOCK_EX_MIC_NASDAQ, GlobalConstants.MC_USD, 5000, "2000-01-03", "2025-05-09"));
      }
    } catch (ParseException pe) {
      pe.printStackTrace();
    }
    return hisoricalDate;
  }

  // Currency pair price tests
  // =======================================

  @Test
  void getEodCurrencyHistoryTest() {
    getEodCurrencyHistory(true);
  }

  @Test
  void updateCurrencyPairLastPriceTest() {
    updateCurrencyPairLastPrice();
  }

  @Override
  protected List<CurrencyPairHistoricalDate> getHistoricalCurrencies() {

    final List<CurrencyPairHistoricalDate> currencies = new ArrayList<>();
    try {
      currencies.add(new CurrencyPairHistoricalDate(GlobalConstants.CC_BTC, GlobalConstants.MC_USD, 3653, "2015-05-10",
          "2025-05-09"));
      currencies.add(new CurrencyPairHistoricalDate("ZAR", "NOK", 2609, "2015-05-11", "2025-05-07"));
    } catch (ParseException pe) {
      pe.printStackTrace();
    }
    return currencies;
  }

  @Override
  protected IFeedConnector getIFeedConnector() {
    return stockdataConnector;
  }

}
