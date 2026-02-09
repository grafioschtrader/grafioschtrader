package grafioschtrader.connector.instrument.test;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.connector.instrument.euronext.EuronextFeedConnector;
import grafioschtrader.connector.instrument.test.ConnectorTestHelper.SecurityHistoricalDate;
import grafioschtrader.types.SpecialInvestmentInstruments;

public class EuronextFeedConnectorTest extends BaseFeedConnectorCheck {

  private EuronextFeedConnector euronextFeedConnector = new EuronextFeedConnector();

  @Test
  void updateSecurityLastPriceTest() {
    updateSecurityLastPriceByHistoricalData();
  }

  // @Test
  void getEodSecurityHistoryTest() {
    getEodSecurityHistory(false);
  }

  @Override
  protected List<SecurityHistoricalDate> getHistoricalSecurities(HistoricalIntra histroricalIntra) {
    List<SecurityHistoricalDate> hisoricalDate = new ArrayList<>();
    try {
      hisoricalDate.add(new SecurityHistoricalDate("Niederlande, Königreich der 4% 05/37", "NL0000102234",
          SpecialInvestmentInstruments.DIRECT_INVESTMENT, null, EuronextFeedConnector.STOCK_EX_MIC_AMSTERDAM, 3778,
          "2005-04-20", "2023-12-08"));
      hisoricalDate.add(new SecurityHistoricalDate("Portuguese Stock Index 20", "PTING0200002",
          SpecialInvestmentInstruments.NON_INVESTABLE_INDICES, null, EuronextFeedConnector.STOCK_EX_MIC_LISBON, 6108,
          "2000-01-03", "2023-12-08"));
      hisoricalDate
          .add(new SecurityHistoricalDate("CAC40", "FR0003500008", SpecialInvestmentInstruments.NON_INVESTABLE_INDICES,
              null, EuronextFeedConnector.STOCK_EX_MIC_PARIS, 1524, "2018-01-02", "2023-12-08"));
      hisoricalDate.add(new SecurityHistoricalDate("AMUNDI MSCI EM ASIA UCITS ETF - EUR", "LU1681044480",
          SpecialInvestmentInstruments.ETF, null, EuronextFeedConnector.STOCK_EX_MIC_PARIS, 270, "2022-11-22",
          "2023-12-08"));
    } catch (ParseException pe) {
      pe.printStackTrace();
    }
    return hisoricalDate;
  }

  @Override
  protected IFeedConnector getIFeedConnector() {
    return euronextFeedConnector;
  }

}
