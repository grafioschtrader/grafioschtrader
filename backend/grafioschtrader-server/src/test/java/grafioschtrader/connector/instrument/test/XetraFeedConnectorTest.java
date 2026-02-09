package grafioschtrader.connector.instrument.test;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.connector.instrument.test.ConnectorTestHelper.SecurityHistoricalDate;
import grafioschtrader.connector.instrument.xetra.XetraFeedConnector;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.SpecialInvestmentInstruments;

public class XetraFeedConnectorTest extends BaseFeedConnectorCheck {

  private XetraFeedConnector xetraFeedConnector = new XetraFeedConnector();

  @Test
  void updateSecurityLastPriceTest() {
    updateSecurityLastPriceByHistoricalData();
  }

  @Test
  void getEodSecurityHistoryTest() {
    getEodSecurityHistory(false);
  }

  @Override
  protected List<SecurityHistoricalDate> getHistoricalSecurities(HistoricalIntra histroricalIntra) {
    List<SecurityHistoricalDate> hisoricalDate = new ArrayList<>();
    try {
      hisoricalDate.add(new SecurityHistoricalDate("NORIS-FONDS", "DE0008492356",
          SpecialInvestmentInstruments.MUTUAL_FUND, AssetclassType.EQUITIES, "XFRA:DE0008492356",
          XetraFeedConnector.STOCK_EX_MIC_FRANKFURT, 2641, "2014-11-11", "2025-05-09"));
      hisoricalDate.add(new SecurityHistoricalDate("Deutsche Lufthansa AG 4,382", "XS1271836600",
          SpecialInvestmentInstruments.DIRECT_INVESTMENT, AssetclassType.FIXED_INCOME, "XS1271836600",
          XetraFeedConnector.STOCK_EX_MIC_FRANKFURT, 2474, "2015-08-06", "2025-05-09"));
      hisoricalDate.add(new SecurityHistoricalDate("DAX 40", "DE0008469008",
          SpecialInvestmentInstruments.NON_INVESTABLE_INDICES, AssetclassType.EQUITIES, "DE0008469008",
          XetraFeedConnector.STOCK_EX_MIC_XETRA, 6449, "2000-01-03", "2025-05-09"));
      hisoricalDate.add(new SecurityHistoricalDate("Beiersdorf Aktiengesellschaft", "DE0005200000",
          SpecialInvestmentInstruments.DIRECT_INVESTMENT, AssetclassType.EQUITIES, "DE0005200000",
          XetraFeedConnector.STOCK_EX_MIC_XETRA, 6461, "2000-01-03", "2025-05-09"));
      hisoricalDate.add(new SecurityHistoricalDate("iShares Core MSCI World UCITS ETF", "IE00B4L5Y983",
          SpecialInvestmentInstruments.ETF, AssetclassType.EQUITIES, "IE00B4L5Y983",
          XetraFeedConnector.STOCK_EX_MIC_XETRA, 3910, "2009-10-14", "2025-05-09"));
      if (histroricalIntra == HistoricalIntra.HISTORICAL) {
        hisoricalDate.add(new SecurityHistoricalDate("Nickelpreis", "XC0005705543",
            SpecialInvestmentInstruments.DIRECT_INVESTMENT, AssetclassType.COMMODITIES, "ARIVA:XC0005705543",
            XetraFeedConnector.STOCK_EX_MIC_FRANKFURT, 3091, "2013-01-02", "2025-05-09"));
        hisoricalDate.add(new SecurityHistoricalDate("EURO STOXX 50", "EU0009658145",
            SpecialInvestmentInstruments.NON_INVESTABLE_INDICES, AssetclassType.EQUITIES, "ARIVA:EU0009658145",
            XetraFeedConnector.STOCK_EX_MIC_XETRA, 3131, "2013-01-02", "2025-05-09"));
      }
    } catch (ParseException pe) {
      pe.printStackTrace();
    }
    return hisoricalDate;
  }

  @Override
  protected IFeedConnector getIFeedConnector() {
    return xetraFeedConnector;
  }
}
