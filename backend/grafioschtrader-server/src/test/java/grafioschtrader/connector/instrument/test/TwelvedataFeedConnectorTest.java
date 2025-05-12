package grafioschtrader.connector.instrument.test;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import grafioschtrader.GlobalConstants;
import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.connector.instrument.test.ConnectorTestHelper.CurrencyPairHistoricalDate;
import grafioschtrader.connector.instrument.test.ConnectorTestHelper.SecurityHistoricalDate;
import grafioschtrader.connector.instrument.twelvedata.TwelvedataFeedConnector;
import grafioschtrader.test.start.GTforTest;
import grafioschtrader.types.SpecialInvestmentInstruments;

@SpringBootTest(classes = GTforTest.class)
public class TwelvedataFeedConnectorTest extends BaseFeedConnectorCheck {

  private static TwelvedataFeedConnector twelvedataFeedConnector;

  @BeforeAll
  static void before(@Autowired TwelvedataFeedConnector tfc) {
    twelvedataFeedConnector = tfc;
  }

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
          GlobalConstants.STOCK_EX_MIC_NASDAQ, GlobalConstants.MC_USD, 6023, "2000-01-03", "2023-12-08"));
      hisoricalDate.add(new SecurityHistoricalDate("SPDR S&P 500 ETF Trust", SpecialInvestmentInstruments.ETF, "SPY",
          GlobalConstants.STOCK_EX_MIC_NASDAQ, GlobalConstants.MC_USD, 6023, "2000-01-03", "2023-12-08"));
      hisoricalDate.add(new SecurityHistoricalDate("Tesla", SpecialInvestmentInstruments.DIRECT_INVESTMENT, "TSLA",
          GlobalConstants.STOCK_EX_MIC_NASDAQ, GlobalConstants.MC_USD, 3386, "2010-06-29", "2023-12-08"));
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
    String oldestDate = "2000-01-03";
    final List<CurrencyPairHistoricalDate> currencies = new ArrayList<>();
    try {
      currencies.add(new CurrencyPairHistoricalDate("ZAR", "NOK", 6651, oldestDate, "2025-05-09"));
      currencies.add(new CurrencyPairHistoricalDate(GlobalConstants.MC_EUR, GlobalConstants.MC_CHF, 6651, oldestDate,
          "2025-05-09"));
      currencies.add(new CurrencyPairHistoricalDate(GlobalConstants.MC_USD, GlobalConstants.MC_CHF, 6648, oldestDate,
          "2025-05-09"));
      currencies.add(new CurrencyPairHistoricalDate(GlobalConstants.CC_BTC, GlobalConstants.MC_USD, 3888, "2014-09-17",
          "2025-05-09"));
    } catch (ParseException pe) {
      pe.printStackTrace();
    }
    return currencies;
  }

  @Override
  protected IFeedConnector getIFeedConnector() {
    return twelvedataFeedConnector;
  }
}
