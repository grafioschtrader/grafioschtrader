package grafioschtrader.connector.instrument.test;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import grafioschtrader.GlobalConstants;
import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.connector.instrument.test.ConnectorTestHelper.SecurityHistoricalDate;
import grafioschtrader.connector.instrument.warsawgpw.WarsawGpwFeedConnector;
import grafioschtrader.types.SpecialInvestmentInstruments;

public class WarsawGpwFeedConnectorTest extends BaseFeedConnectorCheck {

  private WarsawGpwFeedConnector warsawGpwFeedConnector = new WarsawGpwFeedConnector();

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
      hisoricalDate
          .add(new SecurityHistoricalDate("WIG 20", "PL9999999987", SpecialInvestmentInstruments.NON_INVESTABLE_INDICES,
              GlobalConstants.STOCK_EX_MIC_WARSAW, 6346, "2000-01-03", "2025-05-09"));
      hisoricalDate
          .add(new SecurityHistoricalDate("WIG", "PL9999999995", SpecialInvestmentInstruments.NON_INVESTABLE_INDICES,
              GlobalConstants.STOCK_EX_MIC_WARSAW, 6346, "2000-01-03", "2025-05-09"));
      hisoricalDate
          .add(new SecurityHistoricalDate("PKOBP", "PLPKO0000016", SpecialInvestmentInstruments.DIRECT_INVESTMENT,
              GlobalConstants.STOCK_EX_MIC_WARSAW, 4778, "2004-11-10", "2023-12-08"));
      hisoricalDate
          .add(new SecurityHistoricalDate("GETIN", "PLGSPR000014", SpecialInvestmentInstruments.DIRECT_INVESTMENT,
              GlobalConstants.STOCK_EX_MIC_WARSAW, 5658, "2001-05-10", "2023-12-08"));
      hisoricalDate.add(new SecurityHistoricalDate("ETFBW20LV", "PLBEW2L00019", SpecialInvestmentInstruments.ETF,
          GlobalConstants.STOCK_EX_MIC_WARSAW, 954, "2020-02-25", "2023-12-08"));
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
