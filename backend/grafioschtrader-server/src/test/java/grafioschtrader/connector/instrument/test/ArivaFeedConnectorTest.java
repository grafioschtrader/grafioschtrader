package grafioschtrader.connector.instrument.test;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.connector.instrument.ariva.ArivaFeedConnector;
import grafioschtrader.connector.instrument.test.ConnectorTestHelper.SecurityHisoricalDate;
import grafioschtrader.connector.instrument.xetra.XetraFeedConnector;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.SpecialInvestmentInstruments;

class ArivaFeedConnectorTest extends BaseFeedConnectorCheck  {

  @Test
  void getEodSecurityHistoryTest() {
     getEodSecurityHistory(true);
  }

  @Override
  protected List<SecurityHisoricalDate> getHistoricalSecurities() {
    List<SecurityHisoricalDate> hisoricalDate = new ArrayList<>();
    try {
      hisoricalDate.add(new SecurityHisoricalDate("Allianz Aktie", "DE0008404005",
          SpecialInvestmentInstruments.DIRECT_INVESTMENT, AssetclassType.EQUITIES, "292&boerse_id=6",
          XetraFeedConnector.STOCK_EX_MIC_XETRA, 5922, "2000-01-03", "2023-01-18"));
      hisoricalDate.add(new SecurityHisoricalDate("Software AG", "DE000A2GS401",
          SpecialInvestmentInstruments.DIRECT_INVESTMENT, AssetclassType.EQUITIES, "121673&boerse_id=6",
          XetraFeedConnector.STOCK_EX_MIC_XETRA, 5923, "2000-01-03", "2023-01-18"));
      hisoricalDate.add(new SecurityHisoricalDate("2,1% Österreich, Republik 17/2117 auf Festzins", "AT0000A1XML2",
          SpecialInvestmentInstruments.DIRECT_INVESTMENT, AssetclassType.EQUITIES, "131667309&boerse_id=131",
          XetraFeedConnector.STOCK_EX_MIC_XETRA, 663, "2018-09-06", "2023-01-18"));
      hisoricalDate.add(new SecurityHisoricalDate(" iShares Global Clean Energy UCITS ETF", "IE00B1XNHC34",
          SpecialInvestmentInstruments.ETF, AssetclassType.EQUITIES, "100989459&boerse_id=45",
          XetraFeedConnector.STOCK_EX_MIC_XETRA, 1679, "2016-06-08", "2023-01-18"));
    } catch (ParseException pe) {
      pe.printStackTrace();
    }
    return hisoricalDate;
  }


  @Override
  protected IFeedConnector getIFeedConnector() {
    return new ArivaFeedConnector();
  }

}
