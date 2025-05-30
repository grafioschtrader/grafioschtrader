package grafioschtrader.connector.instrument.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.Test;

import grafioschtrader.GlobalConstants;
import grafioschtrader.connector.instrument.fxubc.FxUbcFeedConnector;
import grafioschtrader.connector.instrument.test.ConnectorTestHelper.CurrencyPairHistoricalDate;
import grafioschtrader.entities.Historyquote;

/**
 * This test can often fail, because the provider may be to busy.
 */
class FxUbcFeedConnectorTest {

  private FxUbcFeedConnector fxUbcFeedConnector = new FxUbcFeedConnector();

  @Test
  void getEodCurrencyHistoryTest() throws ParseException {
    String oldestDate = "2000-01-04";
    String youngFromDate = "2023-09-18";
    String toDate = "2023-09-29";

    final List<CurrencyPairHistoricalDate> currencies = new ArrayList<>();
    currencies.add(new CurrencyPairHistoricalDate("ZAR", "NOK", 10, youngFromDate, toDate));
    currencies
        .add(new CurrencyPairHistoricalDate(GlobalConstants.MC_USD, GlobalConstants.MC_JPY, 10, youngFromDate, toDate));
    currencies.add(new CurrencyPairHistoricalDate("ZAR", "NOK", 5945, oldestDate, "2023-09-29"));
    currencies.add(
        new CurrencyPairHistoricalDate(GlobalConstants.MC_EUR, GlobalConstants.MC_CHF, 5946, oldestDate, "2023-09-29"));
    currencies.add(
        new CurrencyPairHistoricalDate(GlobalConstants.MC_USD, GlobalConstants.MC_CHF, 5946, oldestDate, "2023-09-29"));
    currencies.add(
        new CurrencyPairHistoricalDate(GlobalConstants.MC_USD, GlobalConstants.MC_JPY, 5946, oldestDate, "2023-09-29"));

    currencies.parallelStream().forEach(cphd -> {
      List<Historyquote> historyquotes = new ArrayList<>();
      try {
        historyquotes = fxUbcFeedConnector.getEodCurrencyHistory(cphd.currencypair, cphd.from, cphd.to);
        Collections.sort(historyquotes, Comparator.comparing(Historyquote::getDate));
      } catch (Exception e) {
        e.printStackTrace();
      }
      assertThat(historyquotes.size()).isEqualTo(cphd.expectedRows);
      assertThat(historyquotes.get(0).getDate()).isEqualTo(cphd.from);
      assertThat(historyquotes.get(historyquotes.size() - 1).getDate()).isEqualTo(cphd.to);
      ConnectorTestHelper.checkHistoryquoteUniqueDate(cphd.currencypair.getName(), historyquotes);
    });
  }

}
