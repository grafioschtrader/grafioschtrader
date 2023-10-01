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

import grafioschtrader.GlobalConstants;
import grafioschtrader.connector.instrument.boursorama.BoursoramaFeedConnector;
import grafioschtrader.connector.instrument.test.ConnectorTestHelper.SecurityHisoricalDate;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.SpecialInvestmentInstruments;

public class BoursoramaFeedConnectorTest {

  private BoursoramaFeedConnector boursoramaFeedConnector = new BoursoramaFeedConnector();

  @Test
  void getEodSecurityHistoryTest() {

    List<SecurityHisoricalDate> hisoricalDate = getHistoricalSecurities();
    hisoricalDate.parallelStream().forEach(hd -> {
      List<Historyquote> historyquotes = new ArrayList<>();
      try {
        historyquotes = boursoramaFeedConnector.getEodSecurityHistory(hd.security, hd.from, hd.to);
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
    final DateTimeFormatter germanFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(Locale.GERMAN);

    final LocalDate from = LocalDate.parse("03.01.2003", germanFormatter);
    final LocalDate to = LocalDate.parse("26.01.2022", germanFormatter);

    final Date fromDate = Date.from(from.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
    final Date toDate = Date.from(to.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());

    final List<Currencypair> currencies = new ArrayList<>();

    currencies.add(ConnectorTestHelper.createCurrencyPair(GlobalConstants.MC_CHF, "COP", "3fCHF_COP"));
    currencies.add(ConnectorTestHelper.createCurrencyPair(GlobalConstants.MC_JPY, GlobalConstants.MC_USD, "3fJPY_USD"));
    currencies.add(ConnectorTestHelper.createCurrencyPair("CAD", GlobalConstants.MC_EUR, "3fCAD_EUR"));
    currencies.add(ConnectorTestHelper.createCurrencyPair(GlobalConstants.MC_CHF, GlobalConstants.MC_GBP, "3fCHF_GBP"));
    currencies
        .add(ConnectorTestHelper.createCurrencyPair(GlobalConstants.CC_BTC, GlobalConstants.MC_USD, "9xXBTUSDSPOT"));

    currencies.parallelStream().forEach(currencyPair -> {
      List<Historyquote> historyquote = new ArrayList<>();
      try {
        historyquote = boursoramaFeedConnector.getEodCurrencyHistory(currencyPair, fromDate, toDate);
      } catch (Exception e) {
        e.printStackTrace();
      }
      assertThat(historyquote.size()).isGreaterThan(2680);
    });
  }

  @Test
  void updateSecurityLastPriceTest() {
    final List<SecurityHisoricalDate> hisoricalDate = getHistoricalSecurities();
    hisoricalDate.parallelStream().forEach(hd -> {
      hd.security.setUrlIntraExtend(hd.security.getUrlHistoryExtend());
      hd.security.setUrlHistoryExtend(null);
      try {
        boursoramaFeedConnector.updateSecurityLastPrice(hd.security);
      } catch (final Exception e) {
        e.printStackTrace();
      }
      System.out.println(String.format("%s Url-Extend: %s last:%f change: %f%% open: %f high: %f low: %f", hd.security.getName(),
          hd.security.getUrlIntraExtend(), hd.security.getSLast(), hd.security.getSChangePercentage(),
          hd.security.getSOpen(), hd.security.getSHigh(), hd.security.getSLow()));
      assertThat(hd.security.getSLast()).isNotNull().isGreaterThan(0.0);
    });
  }

  @Test
  void updateCurrencyPairLastPriceTest() {
    final List<Currencypair> currencies = new ArrayList<>();
    currencies
        .add(ConnectorTestHelper.createIntraCurrencyPair(GlobalConstants.MC_JPY, GlobalConstants.MC_USD, "3fJPY_USD"));
    currencies.add(ConnectorTestHelper.createIntraCurrencyPair("CAD", GlobalConstants.MC_EUR, "3fCAD_EUR"));
    currencies
        .add(ConnectorTestHelper.createIntraCurrencyPair(GlobalConstants.MC_CHF, GlobalConstants.MC_GBP, "3fCHF_GBP"));
    currencies.add(
        ConnectorTestHelper.createIntraCurrencyPair(GlobalConstants.CC_BTC, GlobalConstants.MC_USD, "9xXBTUSDSPOT"));
    currencies.parallelStream().forEach(currencyPair -> {
      try {
        boursoramaFeedConnector.updateCurrencyPairLastPrice(currencyPair);
        System.out.println(currencyPair);
      } catch (final Exception e) {
        e.printStackTrace();
      }
      assertThat(currencyPair.getSLast()).isNotNull().isGreaterThan(0.0);
    });

  }

  private List<SecurityHisoricalDate> getHistoricalSecurities() {
    List<SecurityHisoricalDate> hisoricalDate = new ArrayList<>();
    String dateTo = "2023-08-31";
    try {
      hisoricalDate.add(new SecurityHisoricalDate("iShares SMIM ETF (CH)", SpecialInvestmentInstruments.ETF, "2aCSSMIM",
          GlobalConstants.STOCK_EX_MIC_SIX, GlobalConstants.MC_CHF, 3195, "2010-12-09", dateTo));
      hisoricalDate.add(new SecurityHisoricalDate("0.362 Bank of New Zealand 21-29", null,
          SpecialInvestmentInstruments.DIRECT_INVESTMENT, AssetclassType.FIXED_INCOME, "2aBNZ01",
          GlobalConstants.STOCK_EX_MIC_SIX, GlobalConstants.MC_CHF, 135, "2021-12-15", dateTo));
      hisoricalDate.add(new SecurityHisoricalDate("Cisco", SpecialInvestmentInstruments.DIRECT_INVESTMENT, "CSCO",
          GlobalConstants.STOCK_EX_MIC_FRANCE, GlobalConstants.MC_EUR, 4686, "2005-01-03", dateTo));
      hisoricalDate.add(new SecurityHisoricalDate("Lyxor CAC 40", SpecialInvestmentInstruments.ETF, "1rPCAC",
          GlobalConstants.STOCK_EX_MIC_FRANCE, GlobalConstants.MC_EUR, 4007, "2008-01-02", dateTo));
      hisoricalDate.add(new SecurityHisoricalDate("ZKB Gold ETF (CHF)", SpecialInvestmentInstruments.ETF, "2aZGLD",
          GlobalConstants.STOCK_EX_MIC_SIX, GlobalConstants.MC_CHF, 4370, "2006-03-15", dateTo));
      hisoricalDate.add(new SecurityHisoricalDate("NASDAQ 100", SpecialInvestmentInstruments.NON_INVESTABLE_INDICES, "$COMPX",
          GlobalConstants.STOCK_EX_MIC_NASDAQ, GlobalConstants.MC_USD, 4698, "2005-01-03", dateTo));

    } catch (ParseException pe) {
      pe.printStackTrace();
    }
    return hisoricalDate;
  }

}
