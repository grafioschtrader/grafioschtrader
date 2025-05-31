package grafioschtrader.connector.instrument.test;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import grafioschtrader.GlobalConstants;
import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.connector.instrument.finanzennet.FinanzenNETFeedConnector;
import grafioschtrader.connector.instrument.test.ConnectorTestHelper.SecurityHistoricalDate;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.SpecialInvestmentInstruments;

/**
 * Sometimes Finanzen.NET can not always satisfy every request.
 */

class FinanzenNETFeedConnectorTest extends BaseFeedConnectorCheck {

  private FinanzenNETFeedConnector finanzenNETFeedConnector = new FinanzenNETFeedConnector();

  // Security price tests
  // =======================================
  @Test
  void updateSecurityLastPriceTest() {
    updateSecurityLastPriceByHistoricalData();
  }

  @Override
  protected List<SecurityHistoricalDate> getHistoricalSecurities() {
    List<SecurityHistoricalDate> hisoricalDate = new ArrayList<>();
    try {
      hisoricalDate.add(new SecurityHistoricalDate("Xtrackers FTSE 100 Short Daily Swap UCITS ETF 1C", "LU0328473581",
          SpecialInvestmentInstruments.ETF, AssetclassType.EQUITIES,
          "etf/xtrackers-ftse-100-short-daily-swap-etf-1c-lu0328473581", GlobalConstants.STOCK_EX_MIC_XETRA, 6023,
          "2000-01-04", "2023-12-08"));
      hisoricalDate.add(
          new SecurityHistoricalDate("Lufthansa Aktie", "DE0008232125", SpecialInvestmentInstruments.DIRECT_INVESTMENT,
              AssetclassType.EQUITIES, "aktien/lufthansa-aktie@stBoerse_XETRA", GlobalConstants.STOCK_EX_MIC_XETRA,
              6023, "2000-01-04", "2023-12-08"));
      hisoricalDate.add(new SecurityHistoricalDate("Deutschland, Bundesrepublik-Anleihe: 2,900% bis 15.08.2056",
          "DE000BU2D012", SpecialInvestmentInstruments.DIRECT_INVESTMENT, AssetclassType.FIXED_INCOME,
          "anleihen/bu2d01-deutschland-bundesrepublik-anleihe", GlobalConstants.STOCK_EX_MIC_FRANKFURT, 6023,
          "2000-01-04", "2023-12-08"));
//    hisoricalDate.add(new SecurityHistoricalDate("NASDAQ 100", "US6311011026",
//    SpecialInvestmentInstruments.NON_INVESTABLE_INDICES, AssetclassType.EQUITIES,
//    "index/nasdaq_100", GlobalConstants.STOCK_EX_MIC_NASDAQ, 6023,
//    "2000-01-04", "2023-12-08"));

    } catch (ParseException pe) {
      pe.printStackTrace();
    }
    return hisoricalDate;
  }

  @Override
  protected IFeedConnector getIFeedConnector() {
    return finanzenNETFeedConnector;
  }

}
