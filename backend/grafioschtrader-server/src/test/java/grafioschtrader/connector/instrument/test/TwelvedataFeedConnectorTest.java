package grafioschtrader.connector.instrument.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import grafioschtrader.GlobalConstants;
import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.connector.instrument.test.ConnectorTestHelper.CurrencyPairHistoricalDate;
import grafioschtrader.connector.instrument.test.ConnectorTestHelper.SecurityHistoricalDate;
import grafioschtrader.connector.instrument.twelvedata.TwelvedataFeedConnector;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.test.start.GTforTest;
import grafioschtrader.types.SpecialInvestmentInstruments;

@SpringBootTest(classes = GTforTest.class)
public class TwelvedataFeedConnectorTest extends BaseFeedConnectorCheck {


  private static TwelvedataFeedConnector twelvedataFeedConnector;

  @BeforeAll
  static void before(@Autowired TwelvedataFeedConnector tfc) {
    twelvedataFeedConnector = tfc;
  }


  //Security price tests
  // =======================================
  @Test
  void updateSecurityLastPriceTest() {
    updateSecurityLastPrice();
  }


  @Test
  void getEodSecurityHistoryTest() {
     getEodSecurityHistory(true);
  }

  @Override
  protected List<SecurityHistoricalDate> getHistoricalSecurities() {
    List<SecurityHistoricalDate> hisoricalDate = new ArrayList<>();
    try {
      hisoricalDate.add(new SecurityHistoricalDate("Cisco", SpecialInvestmentInstruments.DIRECT_INVESTMENT, "csco",
          GlobalConstants.STOCK_EX_MIC_NASDAQ, GlobalConstants.MC_USD, 6024, "2000-01-03", "2023-12-08"));
      hisoricalDate.add(new SecurityHistoricalDate("SPDR S&P 500 ETF Trust", SpecialInvestmentInstruments.ETF,
          "SPY", GlobalConstants.STOCK_EX_MIC_NASDAQ, GlobalConstants.MC_USD, 6023, "2000-01-03", "2023-12-08"));
      hisoricalDate.add(new SecurityHistoricalDate("Tesla", SpecialInvestmentInstruments.DIRECT_INVESTMENT, "TSLA",
          GlobalConstants.STOCK_EX_MIC_NASDAQ, GlobalConstants.MC_USD, 3386, "2010-06-29", "2023-12-08"));
  } catch (ParseException pe) {
      pe.printStackTrace();
    }
    return hisoricalDate;
  }


  @Override
  protected IFeedConnector getIFeedConnector() {
    return twelvedataFeedConnector;
  }

  // Currency pair price tests
  //=======================================
  @Test
  void updateCurrencyPairLastPriceTest() {
    final List<Currencypair> currencies = new ArrayList<>();
    currencies.add(ConnectorTestHelper.createCurrencyPair(GlobalConstants.MC_USD, GlobalConstants.MC_CHF));
    currencies.add(ConnectorTestHelper.createCurrencyPair("ZAR", GlobalConstants.MC_CHF));
    currencies.add(ConnectorTestHelper.createCurrencyPair(GlobalConstants.MC_USD, GlobalConstants.MC_GBP));
    currencies.parallelStream().forEach(currencyPair -> {
      try {
        twelvedataFeedConnector.updateCurrencyPairLastPrice(currencyPair);
        System.out.println(currencyPair);
      } catch (final Exception e) {
        e.printStackTrace();
      }
      assertThat(currencyPair.getSLast()).isNotNull().isGreaterThan(0.0);
    });
  }

  @Test
  void getEodCurrencyHistoryTest() throws ParseException {
    final List<CurrencyPairHistoricalDate> currencies = new ArrayList<>();

    currencies.add(new CurrencyPairHistoricalDate(GlobalConstants.MC_EUR, GlobalConstants.MC_CHF, 6192, "2000-01-03",
        "2023-09-29"));
    currencies.add(new CurrencyPairHistoricalDate(GlobalConstants.MC_USD, GlobalConstants.MC_CHF, 6193, "2000-01-03",
        "2023-09-29"));
    currencies.add(new CurrencyPairHistoricalDate(GlobalConstants.MC_JPY, "SEK", 6426, "2000-01-03", "2023-09-29"));
    currencies.add(new CurrencyPairHistoricalDate(GlobalConstants.CC_BTC, GlobalConstants.MC_USD, 3300, "2014-09-17",
        "2023-09-29"));
    currencies.parallelStream().forEach(cphd -> {
      List<Historyquote> historyquotes = new ArrayList<>();
      try {
        historyquotes = twelvedataFeedConnector.getEodCurrencyHistory(cphd.currencypair, cphd.from, cphd.to);
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
