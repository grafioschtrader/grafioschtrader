package grafioschtrader.connector.instrument.test;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.connector.instrument.six.SixFeedConnector;
import grafioschtrader.connector.instrument.test.ConnectorTestHelper.SecurityHistoricalDate;
import grafioschtrader.types.SpecialInvestmentInstruments;

class SixFeedConnectorTest extends BaseFeedConnectorCheck {

  private SixFeedConnector sixFeedConnector = new SixFeedConnector();

  @Test
  void updateSecurityLastPriceTest() {
    updateSecurityLastPrice();
  }

  @Test
  void getEodSecurityHistoryTest() {
    getEodSecurityHistory(false);
  }

  @Override
  protected List<SecurityHistoricalDate> getHistoricalSecurities() {
    List<SecurityHistoricalDate> hisoricalDate = new ArrayList<>();
    try {
      hisoricalDate
          .add(new SecurityHistoricalDate("SMI PR", "CH0009980894", SpecialInvestmentInstruments.NON_INVESTABLE_INDICES,
              "CH0009980894CHF9", null, 6041, "2000-01-04", "2023-12-08"));
      hisoricalDate.add(new SecurityHistoricalDate("1 HOLCIM 15-25", "CH0306179125",
          SpecialInvestmentInstruments.DIRECT_INVESTMENT, "CH0306179125CHF4", null, 2017, "2015-12-07", "2023-12-08"));
      hisoricalDate.add(new SecurityHistoricalDate("ABB Ltd", "CH0012221716",
          SpecialInvestmentInstruments.DIRECT_INVESTMENT, "CH0012221716CHF4", null, 6022, "2000-01-04", "2023-12-08"));
      hisoricalDate.add(new SecurityHistoricalDate("ZKB Silver ETF - A (CHF)", "CH0183135976",
          SpecialInvestmentInstruments.ETF, "CH0183135976CHF4", null, 4172, "2007-05-10", "2023-12-08"));
    } catch (ParseException pe) {
      pe.printStackTrace();
    }
    return hisoricalDate;
  }

  @Override
  protected IFeedConnector getIFeedConnector() {
    return sixFeedConnector;
  }

}
