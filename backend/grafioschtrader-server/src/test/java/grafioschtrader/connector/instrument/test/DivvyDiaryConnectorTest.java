package grafioschtrader.connector.instrument.test;

import java.text.ParseException;
import java.util.Map;

import org.junit.jupiter.api.Test;

import grafioschtrader.connector.instrument.divvydiary.DivvyDiaryConnector;

class DivvyDiaryConnectorTest {

  @Test
  void getDividendHistoryTest() throws ParseException {
    ConnectorTestHelper.standardDividendTest(new DivvyDiaryConnector(), null, Map.of(ConnectorTestHelper.ISIN_TLT, 18));
  }

}
