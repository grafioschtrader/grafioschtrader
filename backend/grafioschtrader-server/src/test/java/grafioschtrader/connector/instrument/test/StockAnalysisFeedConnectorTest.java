package grafioschtrader.connector.instrument.test;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import grafioschtrader.GlobalConstants;
import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.connector.instrument.stockanalysis.StockAnalysisFeedConnector;
import grafioschtrader.connector.instrument.test.ConnectorTestHelper.SecurityHistoricalDate;
import grafioschtrader.types.SpecialInvestmentInstruments;

public class StockAnalysisFeedConnectorTest extends BaseFeedConnectorCheck {

  private StockAnalysisFeedConnector stockAnalysisFeedConnector = new StockAnalysisFeedConnector();

  // Security price tests
  // =======================================
  @Test
  void getEodSecurityHistoryTest() {
    getEodSecurityHistory(false);
  }

  @Test
  void updateSecurityLastPriceTest() {
    updateSecurityLastPriceByHistoricalData();
  }

  @Override
  protected List<SecurityHistoricalDate> getHistoricalSecurities(HistoricalIntra histroricalIntra) {
    List<SecurityHistoricalDate> hisoricalDate = new ArrayList<>();
    String dateTo = "2026-10-09";
    try {
      hisoricalDate.add(new SecurityHistoricalDate("Nestlé", SpecialInvestmentInstruments.DIRECT_INVESTMENT,
          "a/SWX-NESN", GlobalConstants.STOCK_EX_MIC_SIX, GlobalConstants.MC_CHF, 6769, "2000-01-03", dateTo));
      hisoricalDate.add(new SecurityHistoricalDate("SAP", SpecialInvestmentInstruments.DIRECT_INVESTMENT, "a/ETR-SAP",
          GlobalConstants.STOCK_EX_MIC_XETRA, GlobalConstants.MC_EUR, 6812, "2000-01-03", dateTo));
      hisoricalDate.add(new SecurityHistoricalDate("Apple", SpecialInvestmentInstruments.DIRECT_INVESTMENT, "s/aapl",
          GlobalConstants.STOCK_EX_MIC_NASDAQ, GlobalConstants.MC_USD, 6733, "2000-01-03", dateTo));
      hisoricalDate.add(new SecurityHistoricalDate("iShares Core MSCI World", SpecialInvestmentInstruments.ETF,
          "a/AMS-IWDA", "XAMS", GlobalConstants.MC_USD, 3570, "2012-10-24", dateTo));
    } catch (ParseException pe) {
      pe.printStackTrace();
    }
    return hisoricalDate;
  }

  @Override
  protected IFeedConnector getIFeedConnector() {
    return stockAnalysisFeedConnector;
  }

}
