package grafioschtrader.connector.instrument.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.text.ParseException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import grafioschtrader.GlobalConstants;
import grafioschtrader.connector.instrument.eodhistoricaldata.EodHistoricalDataConnector;
import grafioschtrader.connector.instrument.test.ConnectorTestHelper.HisoricalDate;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Dividend;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitysplit;
import grafioschtrader.test.start.GTforTest;
import grafioschtrader.types.SpecialInvestmentInstruments;

@SpringBootTest(classes = GTforTest.class)
public class EodHistoricalDataConnectorTest {

  @Autowired
  private EodHistoricalDataConnector eodHistoricalDataConnector;
  
  final DateTimeFormatter germanFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
      .withLocale(Locale.GERMAN);
  
  @Test
  void getEodSecurityHistoryTest() {
    List<HisoricalDate> hisoricalDate = getHistoricalSecurities();
    hisoricalDate.parallelStream().forEach(hd -> {
      List<Historyquote> historyquotes = new ArrayList<>();
      try {
        historyquotes = eodHistoricalDataConnector.getEodSecurityHistory(hd.security, hd.from, hd.to);
      } catch (final Exception e) {
        e.printStackTrace();
      }
      System.out.println("Ticker=" + hd.security.getUrlHistoryExtend() + " Historyquote-Size=" + historyquotes.size()
          + " first date: " + historyquotes.get(0).getDate());
      assertThat(historyquotes.size()).isEqualTo(hd.expectedRows);
      assertThat(historyquotes.get(0).getDate()).isEqualTo(hd.from);
      assertThat(historyquotes.get(historyquotes.size() - 1).getDate()).isEqualTo(hd.to);
    });
  }
  
  
  @Test
  void getEodCurrencyHistoryTest() {
    final LocalDate from = LocalDate.parse("2000-01-01");
    final LocalDate to = LocalDate.parse("2022-01-28");
    final Date fromDate = Date.from(from.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
    final Date toDate = Date.from(to.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
  
    
    final List<Currencypair> currencies = new ArrayList<>();
    currencies.add(ConnectorTestHelper.createCurrencyPair("ZAR", "NOK"));
    currencies.add(ConnectorTestHelper.createCurrencyPair(GlobalConstants.MC_USD, GlobalConstants.MC_CHF));
    currencies.add(ConnectorTestHelper.createCurrencyPair(GlobalConstants.MC_JPY, "SEK"));
    currencies.add(ConnectorTestHelper.createCurrencyPair(GlobalConstants.CC_BTC, GlobalConstants.MC_USD));
    currencies.parallelStream().forEach(currencyPair -> {
      List<Historyquote> historyquote = new ArrayList<>();
      try {
        historyquote = eodHistoricalDataConnector.getEodCurrencyHistory(currencyPair, fromDate, toDate);
      } catch (Exception e) {
        e.printStackTrace();
      }
      System.out.println(historyquote.size());
      assertThat(historyquote.size()).isGreaterThan(3400);
    });
  }
  
  @Test
  void updateSecurityLastPriceTest() {
    final List<HisoricalDate> hisoricalDate = getHistoricalSecurities();
    hisoricalDate.parallelStream().forEach(hd -> {
      hd.security.setUrlIntraExtend(hd.security.getUrlHistoryExtend());
      hd.security.setUrlHistoryExtend(null);
      try {
        eodHistoricalDataConnector.updateSecurityLastPrice(hd.security);
      } catch (final Exception e) {
        e.printStackTrace();
      }
      System.out.println(String.format("%s last:%f change: %f high: %f low: %f", hd.security.getUrlIntraExtend(),
          hd.security.getSLast(), hd.security.getSChangePercentage(), hd.security.getSHigh(), hd.security.getSLow()));
      assertThat(hd.security.getSLast()).isNotNull().isGreaterThan(0.0);
    });

  }
  
  
  @Test
  void updateCurrencyPairLastPriceTest() {
    final List<Currencypair> currencies = new ArrayList<>();
    currencies.add(ConnectorTestHelper.createCurrencyPair(GlobalConstants.CC_BTC, GlobalConstants.MC_USD));
    currencies.add(ConnectorTestHelper.createCurrencyPair(GlobalConstants.MC_USD, GlobalConstants.MC_CHF));
    currencies.add(ConnectorTestHelper.createCurrencyPair("ZAR", GlobalConstants.MC_CHF));
    currencies.add(ConnectorTestHelper.createCurrencyPair(GlobalConstants.MC_USD, GlobalConstants.MC_GBP));
    currencies.parallelStream().forEach(currencyPair -> {
      try {
        eodHistoricalDataConnector.updateCurrencyPairLastPrice(currencyPair);
        System.out.println(currencyPair);
      } catch (final Exception e) {
        e.printStackTrace();
      }
      assertThat(currencyPair.getSLast()).isNotNull().isGreaterThan(0.0);
    });
  }
  
  @Test
  void getDividendHistoryTest() {
    final DateTimeFormatter germanFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(Locale.GERMAN);
    final LocalDate from = LocalDate.parse("03.01.2000", germanFormatter);

    final List<Security> securities = new ArrayList<>();
    securities.add(ConnectorTestHelper.createDividendSecurity("Nestlé", "NESN.SW"));
    securities.add(ConnectorTestHelper.createDividendSecurity("Apple", "AAPL"));
    securities.add(ConnectorTestHelper.createDividendSecurity("iShares 20+ Year Treasury Bond", "TLT"));

    securities.parallelStream().forEach(security -> {
      List<Dividend> dividens = new ArrayList<>();
      try {
        dividens = eodHistoricalDataConnector.getDividendHistory(security, from);
      } catch (final Exception e) {
        e.printStackTrace();
      }
      assertThat(dividens.size()).isGreaterThanOrEqualTo(1);
    });
  }
  
  @Test
  void getSplitsTest() {
    final DateTimeFormatter germanFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(Locale.GERMAN);
    final LocalDate from = LocalDate.parse("03.01.2000", germanFormatter);

    final List<Security> securities = new ArrayList<>();
    securities.add(ConnectorTestHelper.createSplitSecurity("Nestlé", "NESN.SW"));
    securities.add(ConnectorTestHelper.createSplitSecurity("Apple", "AAPL"));

    securities.parallelStream().forEach(security -> {
      List<Securitysplit> seucritysplitList = new ArrayList<>();
      try {
        seucritysplitList = eodHistoricalDataConnector.getSplitHistory(security, from);
      } catch (final Exception e) {
        e.printStackTrace();
      }
      seucritysplitList.forEach(System.out::println);
      assertThat(seucritysplitList.size()).isGreaterThanOrEqualTo(1);
    });

  }
  
  

  private List<HisoricalDate> getHistoricalSecurities() {
    List<HisoricalDate> hisoricalDate = new ArrayList<>();
    try {
    
      hisoricalDate.add(new HisoricalDate("Cisco", SpecialInvestmentInstruments.DIRECT_INVESTMENT, "AF.PA",
          GlobalConstants.STOCK_EX_MIC_FRANCE, GlobalConstants.MC_EUR, 6056, "2000-01-03", "2023-08-31"));
      hisoricalDate.add(new HisoricalDate("Cisco", SpecialInvestmentInstruments.DIRECT_INVESTMENT, "csco",
          GlobalConstants.STOCK_EX_MIC_NASDAQ, GlobalConstants.MC_USD, 5926, "2000-01-03", "2023-07-24"));
          
      hisoricalDate.add(new HisoricalDate("Express Inc", SpecialInvestmentInstruments.DIRECT_INVESTMENT, "EXPR.US",
          GlobalConstants.STOCK_EX_MIC_NASDAQ, GlobalConstants.MC_USD, 3349, "2010-05-13", "2023-08-31"));
      
      hisoricalDate.add(new HisoricalDate("Lyxor CAC 40", SpecialInvestmentInstruments.ETF, "CAC.PA",
          GlobalConstants.STOCK_EX_MIC_FRANCE, GlobalConstants.MC_EUR, 4011, "2008-01-02", "2023-08-31"));
          
      hisoricalDate.add(new HisoricalDate("iShares SMIM ETF (CH)", SpecialInvestmentInstruments.ETF, "CSSMIM.SW",
          GlobalConstants.STOCK_EX_MIC_SIX, GlobalConstants.MC_CHF, 4708, "2004-12-09", "2023-08-31"));
      hisoricalDate.add(new HisoricalDate("ZKB Gold ETF (CHF)", SpecialInvestmentInstruments.ETF, "ZGLD.SW",
          GlobalConstants.STOCK_EX_MIC_SIX, GlobalConstants.MC_CHF, 4206, "2006-03-15", "2023-08-31"));
      hisoricalDate.add(new HisoricalDate("NASDAQ 100", SpecialInvestmentInstruments.NON_INVESTABLE_INDICES, "NDX.INDX",
          GlobalConstants.STOCK_EX_MIC_NASDAQ, GlobalConstants.MC_USD, 5954, "2000-01-03", "2023-08-31"));
       
       } catch (ParseException pe) {
      pe.printStackTrace();
    }
    return hisoricalDate;
  }

  
  
}
