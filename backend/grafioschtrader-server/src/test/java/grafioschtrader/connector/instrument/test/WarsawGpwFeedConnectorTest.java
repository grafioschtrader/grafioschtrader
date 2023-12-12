package grafioschtrader.connector.instrument.test;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.connector.instrument.test.ConnectorTestHelper.SecurityHisoricalDate;
import grafioschtrader.connector.instrument.warsawgpw.WarsawGpwFeedConnector;
import grafioschtrader.types.SpecialInvestmentInstruments;

public class WarsawGpwFeedConnectorTest extends BaseFeedConnectorCheck {

  private WarsawGpwFeedConnector warsawGpwFeedConnector = new WarsawGpwFeedConnector();

  @Test
  void updateSecurityLastPriceTest() {
    updateSecurityLastPrice();
  }

  @Test
  void getEodSecurityHistoryTest() {
    getEodSecurityHistory(false);
  }
  
  protected List<SecurityHisoricalDate> getHistoricalSecurities() {
    List<SecurityHisoricalDate> hisoricalDate = new ArrayList<>();
    try {
      hisoricalDate.add(new SecurityHisoricalDate("WIG", "PL9999999995",
          SpecialInvestmentInstruments.NON_INVESTABLE_INDICES, 5954, "2000-03-01", "2023-12-08"));
      hisoricalDate.add(new SecurityHisoricalDate("PKOBP", "PLPKO0000016",
          SpecialInvestmentInstruments.DIRECT_INVESTMENT, 4778, "2004-11-10", "2023-12-08"));
      hisoricalDate.add(new SecurityHisoricalDate("GETIN", "PLGSPR000014",
          SpecialInvestmentInstruments.DIRECT_INVESTMENT, 5658, "2001-05-10", "2023-12-08"));
      hisoricalDate.add(new SecurityHisoricalDate("ETFBW20LV", "PLBEW2L00019",
          SpecialInvestmentInstruments.ETF, 954, "2020-02-25", "2023-12-08"));
    } catch (ParseException pe) {
      pe.printStackTrace();
    }
    return hisoricalDate;
  }

  @Override
  protected IFeedConnector getIFeedConnector() {
    return warsawGpwFeedConnector;
  }

}
