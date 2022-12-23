package grafioschtrader.connector.instrument.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import grafioschtrader.connector.instrument.euronext.EuronextFeedConnector;
import grafioschtrader.connector.instrument.test.ConnectorTestHelper.HisoricalDate;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.types.SpecialInvestmentInstruments;

public class EuronextFeedConnectorTest {

  private EuronextFeedConnector euronextFeedConnector = new EuronextFeedConnector();

  @Test
  void getEodSecurityHistoryTest() {
    final List<HisoricalDate> hisoricalDate = new ArrayList<>();
    try {

      hisoricalDate.add(new HisoricalDate("Portuguese Stock Index 20", "PTING0200002",
          SpecialInvestmentInstruments.NON_INVESTABLE_INDICES, EuronextFeedConnector.STOCK_EX_MIC_LISBON, 5860,
          "2000-01-03", "2022-12-21"));

      hisoricalDate.add(new HisoricalDate("CAC40", "FR0003500008", SpecialInvestmentInstruments.NON_INVESTABLE_INDICES,
          EuronextFeedConnector.STOCK_EX_MIC_PARIS, 1276, "2018-01-02", "2022-12-21"));

      hisoricalDate.add(new HisoricalDate("AMUNDI MSCI EM ASIA UCITS ETF - EUR", "LU1681044480",
          SpecialInvestmentInstruments.ETF, EuronextFeedConnector.STOCK_EX_MIC_PARIS, 22, "2022-11-22", "2022-12-21"));

      hisoricalDate.parallelStream().forEach(hd -> {
        List<Historyquote> historyquote = new ArrayList<>();
        try {
          historyquote = euronextFeedConnector.getEodSecurityHistory(hd.security, hd.from, hd.to);
        } catch (final Exception e) {
          e.printStackTrace();
        }

        assertThat(historyquote.size()).isEqualTo(hd.expectedRows);
        assertThat(historyquote.get(0).getDate()).isEqualTo(hd.from);
        assertThat(historyquote.get(historyquote.size() - 1).getDate()).isEqualTo(hd.to);

      });
    } catch (ParseException pe) {
      pe.printStackTrace();
    }
  }

}
