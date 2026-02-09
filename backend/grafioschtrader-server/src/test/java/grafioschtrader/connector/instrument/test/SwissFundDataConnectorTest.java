package grafioschtrader.connector.instrument.test;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.connector.instrument.swissfunddata.SwissFundDataConnector;
import grafioschtrader.connector.instrument.test.ConnectorTestHelper.SecurityHistoricalDate;
import grafioschtrader.types.SpecialInvestmentInstruments;

class SwissFundDataConnectorTest extends BaseFeedConnectorCheck {

  @Test
  void getEodSecurityHistoryTest() {
    getEodSecurityHistory(false);
  }

  @Override
  protected List<SecurityHistoricalDate> getHistoricalSecurities(HistoricalIntra histroricalIntra) {
    List<SecurityHistoricalDate> hisoricalDate = new ArrayList<>();
    try {
      hisoricalDate.add(new SecurityHistoricalDate("Migros Bank (CH) Fonds 45 V",
          SpecialInvestmentInstruments.MUTUAL_FUND, "79260", 1627, "2017-06-30", "2023-12-08"));
      hisoricalDate.add(new SecurityHistoricalDate("Focused SICAV - Global Bond (EUR hedged) F-UKdist",
          SpecialInvestmentInstruments.MUTUAL_FUND, "46234", 2359, "2010-09-28", "2023-12-08"));
    } catch (ParseException pe) {
      pe.printStackTrace();
    }
    return hisoricalDate;
  }

  @Override
  protected IFeedConnector getIFeedConnector() {
    return new SwissFundDataConnector();
  }

}
