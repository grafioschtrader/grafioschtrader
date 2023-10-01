package grafioschtrader.connector.instrument.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.text.ParseException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import grafioschtrader.GlobalConstants;
import grafioschtrader.connector.instrument.test.ConnectorTestHelper.CurrencyPairHisoricalDate;
import grafioschtrader.connector.instrument.twelvedata.TwelvedataFeedConnector;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.test.start.GTforTest;
import grafioschtrader.types.SpecialInvestmentInstruments;

@SpringBootTest(classes = GTforTest.class)
public class TwelvedataFeedConnectorTest {

  @Autowired
  private TwelvedataFeedConnector twelvedataFeedConnector;

  final DateTimeFormatter germanFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
      .withLocale(Locale.GERMAN);

  @Test
  void getEodSecurityHistoryTest() {

    final List<Security> securities = new ArrayList<>();

    final LocalDate from = LocalDate.parse("03.01.2000", germanFormatter);
    final LocalDate to = LocalDate.parse("26.01.2022", germanFormatter);

    final Date fromDate = Date.from(from.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
    final Date toDate = Date.from(to.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
    securities.add(ConnectorTestHelper.createHistoricalSecurity("Cisco Systems", "csco",
        SpecialInvestmentInstruments.DIRECT_INVESTMENT, GlobalConstants.STOCK_EX_MIC_NASDAQ));
    securities.add(ConnectorTestHelper.createHistoricalSecurity("SPDR S&P 500 ETF Trust", "SPY",
        SpecialInvestmentInstruments.ETF, GlobalConstants.STOCK_EX_MIC_NYSE));
    securities.add(ConnectorTestHelper.createHistoricalSecurity("General Electric Company", "GE",
        SpecialInvestmentInstruments.ETF, GlobalConstants.STOCK_EX_MIC_NYSE));
    securities.parallelStream().forEach(security -> {
      List<Historyquote> historyquote = new ArrayList<>();
      try {
        historyquote = twelvedataFeedConnector.getEodSecurityHistory(security, fromDate, toDate);
      } catch (final Exception e) {
        e.printStackTrace();
      }
      System.out.println("Ticker=" + security.getUrlHistoryExtend() + " Historyquote-Size=" + historyquote.size());
      assertThat(historyquote.size()).isGreaterThan(5550);
    });
  }
  
  @Test
  void getEodCurrencyHistoryTest() throws ParseException {
      final List<CurrencyPairHisoricalDate> currencies = new ArrayList<>();

      currencies.add(new CurrencyPairHisoricalDate(GlobalConstants.MC_EUR, GlobalConstants.MC_CHF, 6192, "2000-01-03",
          "2023-09-29"));
      currencies.add(new CurrencyPairHisoricalDate(GlobalConstants.MC_USD, GlobalConstants.MC_CHF, 6193, "2000-01-03",
          "2023-09-29"));
      currencies.add(new CurrencyPairHisoricalDate(GlobalConstants.MC_JPY, "SEK", 6426, "2000-01-03",
          "2023-09-29"));
      currencies.add(new CurrencyPairHisoricalDate(GlobalConstants.CC_BTC, GlobalConstants.MC_USD, 3118, "2014-09-17",
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
  
  
  @Test
  void updateSecurityLastPriceTest() {
    final List<Security> securities = new ArrayList<>();
    securities.add(ConnectorTestHelper.createIntraSecurity("Cisco Systems", "csco", SpecialInvestmentInstruments.DIRECT_INVESTMENT, "NAS"));
    securities.parallelStream().forEach(security -> {
      try {
        twelvedataFeedConnector.updateSecurityLastPrice(security);
        System.out.println(security);
      } catch (final Exception e) {
        e.printStackTrace();
      }
      assertThat(security.getSLast()).isNotNull().isGreaterThan(0.0);
    });
  }
  
  
  
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
  
}
