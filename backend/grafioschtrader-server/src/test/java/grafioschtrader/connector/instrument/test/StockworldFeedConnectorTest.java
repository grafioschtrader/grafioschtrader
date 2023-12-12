package grafioschtrader.connector.instrument.test;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.connector.instrument.stockworld.StockworldFeedConnector;
import grafioschtrader.connector.instrument.test.ConnectorTestHelper.SecurityHisoricalDate;
import grafioschtrader.types.SpecialInvestmentInstruments;

class StockworldFeedConnectorTest extends BaseFeedConnectorCheck {

  private StockworldFeedConnector stockworldFeedConnector = new StockworldFeedConnector();
    
  @Test
  void getEodSecurityHistoryTest() {
    getEodSecurityHistory(true);
  }
  
  @Override
  protected List<SecurityHisoricalDate> getHistoricalSecurities() {
    List<SecurityHisoricalDate> hisoricalDate = new ArrayList<>();
    try {
      hisoricalDate.add(new SecurityHisoricalDate("1 HOLCIM 15-25", "CH0306179125",
          SpecialInvestmentInstruments.DIRECT_INVESTMENT, "124100141", null, 1916, "2015-12-07", "2023-12-08"));
      hisoricalDate.add(new SecurityHisoricalDate("ABB Ltd", "CH0012221716",
          SpecialInvestmentInstruments.DIRECT_INVESTMENT, "2798", null, 4571, "2002-10-02", "2023-12-08"));
      hisoricalDate.add(new SecurityHisoricalDate("ZKB Silver ETF - A (CHF)", "CH0183135976",
          SpecialInvestmentInstruments.ETF, "102758344", null, 1597, "2017-05-11", "2023-12-08"));
      hisoricalDate.add(new SecurityHisoricalDate("Bayerische Landesbank 2,5% 17/27", "DE000BLB4UP9",
          SpecialInvestmentInstruments.ETF, "128405128", null, 1656, "2017-01-26", "2023-12-08"));
    } catch (ParseException pe) {
      pe.printStackTrace();
    }
    return hisoricalDate;
  }

  @Override
  protected IFeedConnector getIFeedConnector() {
    return stockworldFeedConnector;
  }
  
 }
