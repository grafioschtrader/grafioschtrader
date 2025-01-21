package grafioschtrader.connector.instrument.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import grafioschtrader.GlobalConstants;
import grafioschtrader.connector.instrument.ecb.EcbCrossRateConnector;
import grafioschtrader.connector.instrument.test.ConnectorTestHelper.CurrencyPairHistoricalDate;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.test.start.GTforTest;

@SpringBootTest(classes = GTforTest.class)
public class EcbCrossRateConnectorTest {

  @Autowired
  private EcbCrossRateConnector ecbCrossRateConnector;

  @Test
  void getEodCurrencyHistoryTest() throws ParseException {
    String oldestDate = "2000-01-04";
    String youngFromDate = "2025-01-03";
    String toDate = "2025-01-13";

    final List<CurrencyPairHistoricalDate> currencies = new ArrayList<>();

    currencies.add(new CurrencyPairHistoricalDate("ZAR", "NOK", 7, youngFromDate, toDate));
    currencies
        .add(new CurrencyPairHistoricalDate(GlobalConstants.MC_USD, GlobalConstants.MC_JPY, 7, youngFromDate, toDate));
    currencies.add(new CurrencyPairHistoricalDate("ZAR", "NOK", 6406, oldestDate, toDate));
    currencies.add(
        new CurrencyPairHistoricalDate(GlobalConstants.MC_EUR, GlobalConstants.MC_CHF, 6400, oldestDate, youngFromDate));
    currencies.add(
        new CurrencyPairHistoricalDate(GlobalConstants.MC_CHF, GlobalConstants.MC_USD, 6400, oldestDate, youngFromDate));
    currencies.add(
        new CurrencyPairHistoricalDate(GlobalConstants.MC_USD, GlobalConstants.MC_CHF, 6400, oldestDate, youngFromDate));
    currencies.add(
        new CurrencyPairHistoricalDate(GlobalConstants.MC_USD, GlobalConstants.MC_JPY, 6400, oldestDate, youngFromDate));

    currencies.parallelStream().forEach(cphd -> {
      List<Historyquote> historyquotes = new ArrayList<>();
      try {
        historyquotes = ecbCrossRateConnector.getEodCurrencyHistory(cphd.currencypair, cphd.from, cphd.to);
        Collections.sort(historyquotes, Comparator.comparing(Historyquote::getDate));
      } catch (Exception e) {
        e.printStackTrace();
      }
      System.out.println(cphd.currencypair + " Size: " + historyquotes.size());
      assertThat(historyquotes.size()).isEqualTo(cphd.expectedRows);
      assertThat(historyquotes.getFirst().getDate()).isEqualTo(cphd.from);
      assertThat(historyquotes.getLast().getDate()).isEqualTo(cphd.to);
      ConnectorTestHelper.checkHistoryquoteUniqueDate(cphd.currencypair.getName(), historyquotes);
    });
  }

}
