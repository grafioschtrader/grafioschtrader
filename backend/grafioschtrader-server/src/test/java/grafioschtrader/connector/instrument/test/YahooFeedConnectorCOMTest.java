package grafioschtrader.connector.instrument.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import grafioschtrader.common.DateHelper;
import grafioschtrader.connector.instrument.test.ConnectorTestHelper.HisoricalDate;
import grafioschtrader.connector.instrument.yahoo.YahooFeedConnectorCOM;
import grafioschtrader.entities.Assetclass;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Dividend;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitysplit;
import grafioschtrader.entities.Stockexchange;
import grafioschtrader.types.SpecialInvestmentInstruments;

class YahooFeedConnectorCOMTest {

  private YahooFeedConnectorCOM yahooFeedConnector = new YahooFeedConnectorCOM();

  @Test
  void getEodCurrencyHistoryTest() {

    final DateTimeFormatter germanFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(Locale.GERMAN);

    final List<Currencypair> currencies = new ArrayList<>();
    currencies.add(ConnectorTestHelper.createCurrencyPair(GlobalConstants.CC_BTC, GlobalConstants.MC_USD));
    currencies.add(ConnectorTestHelper.createCurrencyPair(GlobalConstants.MC_USD, GlobalConstants.MC_CHF));
    currencies.add(ConnectorTestHelper.createCurrencyPair(GlobalConstants.MC_EUR, GlobalConstants.MC_CHF));
    final LocalDate from = LocalDate.parse("08.08.2019", germanFormatter);
    final LocalDate to = LocalDate.parse("15.01.2021", germanFormatter);

    final Date fromDate = Date.from(from.atStartOfDay().atZone(ZoneId.of(GlobalConstants.TIME_ZONE)).toInstant());
    final Date toDate = Date.from(to.atStartOfDay().atZone(ZoneId.of(GlobalConstants.TIME_ZONE)).toInstant());

    currencies.parallelStream().forEach(currencyPair -> {
      List<Historyquote> historyquotes = new ArrayList<>();
      try {
        historyquotes = yahooFeedConnector.getEodCurrencyHistory(currencyPair, fromDate, toDate);
      } catch (Exception e) {
        e.printStackTrace();
      }
      assertThat(historyquotes.size()).isGreaterThan(350);
      assertTrue(DateHelper.isSameDay(historyquotes.get(0).getDate(), fromDate));
      assertTrue(DateHelper.isSameDay(historyquotes.get(historyquotes.size() - 1).getDate(), toDate));

    });
  }

  @Test
  void updateCurrencyPairLastPriceTest() {

    final List<Currencypair> currencies = new ArrayList<>();

    currencies.add(ConnectorTestHelper.createCurrencyPair(GlobalConstants.MC_EUR, GlobalConstants.MC_CHF));
    currencies.add(ConnectorTestHelper.createCurrencyPair(GlobalConstants.CC_BTC, GlobalConstants.MC_USD));
    currencies.add(ConnectorTestHelper.createCurrencyPair(GlobalConstants.MC_CHF, "AUD"));
    currencies.add(ConnectorTestHelper.createCurrencyPair(GlobalConstants.MC_USD, GlobalConstants.MC_CHF));
    currencies.add(ConnectorTestHelper.createCurrencyPair(GlobalConstants.MC_USD, GlobalConstants.MC_EUR));
    currencies.add(ConnectorTestHelper.createCurrencyPair(GlobalConstants.MC_GBP, GlobalConstants.MC_EUR));

    currencies.parallelStream().forEach(currencyPair -> {
      try {
        yahooFeedConnector.updateCurrencyPairLastPrice(currencyPair);
      } catch (final Exception e) {
        e.printStackTrace();
      }
      System.out.println(String.format("%s/%s last:%f change: %f high: %f low: %f", currencyPair.getFromCurrency(),
          currencyPair.getToCurrency(), currencyPair.getSLast(), currencyPair.getSChangePercentage(),
          currencyPair.getSHigh(), currencyPair.getSLow()));
      assertThat(currencyPair.getSLast()).isNotNull().isGreaterThan(0.0);
    });
  }

  @Test
  void getEodSecurityHistoryTest() {
    List<HisoricalDate> hisoricalDate = getHistoricalSecurities();
    hisoricalDate.parallelStream().forEach(hd -> {
      List<Historyquote> historyquotes = new ArrayList<>();
      try {
        historyquotes = yahooFeedConnector.getEodSecurityHistory(hd.security, hd.from, hd.to);
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

  private List<HisoricalDate> getHistoricalSecurities() {
    List<HisoricalDate> hisoricalDate = new ArrayList<>();
    try {
      hisoricalDate.add(new HisoricalDate("Cisco", SpecialInvestmentInstruments.DIRECT_INVESTMENT, "csco",
          GlobalConstants.STOCK_EX_MIC_NASDAQ, GlobalConstants.MC_USD, 5926, "2000-01-03", "2023-07-24"));
      hisoricalDate.add(new HisoricalDate("NASDAQ 100", SpecialInvestmentInstruments.NON_INVESTABLE_INDICES, "^NDX",
          GlobalConstants.STOCK_EX_MIC_NASDAQ, GlobalConstants.MC_USD, 5926, "2000-01-03", "2023-07-24"));
      hisoricalDate.add(new HisoricalDate("Tesla", SpecialInvestmentInstruments.DIRECT_INVESTMENT, "TSLA",
          GlobalConstants.STOCK_EX_MIC_NASDAQ, GlobalConstants.MC_USD, 3289, "2010-06-29", "2023-07-24"));
      hisoricalDate.add(new HisoricalDate("Nestlé S.A", SpecialInvestmentInstruments.DIRECT_INVESTMENT, "NESN.SW",
          GlobalConstants.STOCK_EX_MIC_SIX, GlobalConstants.MC_CHF, 5620, "2000-04-21", "2023-07-24"));
      hisoricalDate.add(new HisoricalDate("Tesco PLC", SpecialInvestmentInstruments.DIRECT_INVESTMENT, "TSCO.L",
          GlobalConstants.STOCK_EX_MIC_UK, GlobalConstants.MC_GBP, 5999, "2000-01-03", "2023-07-24"));
      hisoricalDate.add(new HisoricalDate("Lyxor CAC 40", SpecialInvestmentInstruments.ETF, "CAC.PA",
          GlobalConstants.STOCK_EX_MIC_FRANCE, GlobalConstants.MC_EUR, 3982, "2008-01-02", "2023-07-24"));
      hisoricalDate.add(new HisoricalDate("UBSFund Solutions - CMCI Oil SF ETF", SpecialInvestmentInstruments.ETF,
          "OILUSA.SW", GlobalConstants.STOCK_EX_MIC_FRANCE, GlobalConstants.MC_USD, 3282, "2010-06-15", "2023-07-24"));
    } catch (ParseException pe) {
      pe.printStackTrace();
    }
    return hisoricalDate;
  }

  @Test
  void updateSecurityLastPriceTest() {
    final List<HisoricalDate> hisoricalDate = getHistoricalSecurities();
    hisoricalDate.parallelStream().forEach(hd -> {
      hd.security.setUrlIntraExtend(hd.security.getUrlHistoryExtend());
      hd.security.setUrlHistoryExtend(null);
      try {
        yahooFeedConnector.updateSecurityLastPrice(hd.security);
      } catch (final Exception e) {
        e.printStackTrace();
      }
      System.out.println(String.format("%s last:%f change: %f high: %f low: %f", hd.security.getUrlIntraExtend(),
          hd.security.getSLast(), hd.security.getSChangePercentage(), hd.security.getSHigh(), hd.security.getSLow()));
      assertThat(hd.security.getSLast()).isNotNull().isGreaterThan(0.0);
    });

  }

  @Test
  void getSplitsTest() {
    final DateTimeFormatter germanFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(Locale.GERMAN);
    final LocalDate from = LocalDate.parse("03.01.2000", germanFormatter);

    final List<Security> securities = new ArrayList<>();
    securities.add(createSecurity("NESN.SW", GlobalConstants.STOCK_EX_MIC_SIX, GlobalConstants.MC_CHF,
        SpecialInvestmentInstruments.DIRECT_INVESTMENT));
    securities.add(createSecurity("csco", "America/New_York", GlobalConstants.MC_USD,
        SpecialInvestmentInstruments.DIRECT_INVESTMENT));

    securities.parallelStream().forEach(security -> {
      List<Securitysplit> seucritysplitList = new ArrayList<>();
      try {
        seucritysplitList = yahooFeedConnector.getSplitHistory(security, from);
      } catch (final Exception e) {
        e.printStackTrace();
      }
      seucritysplitList.forEach(System.out::println);
      assertThat(seucritysplitList.size()).isGreaterThanOrEqualTo(1);
    });

  }

  @Test
  void getDividendHistoryTest() {
    final DateTimeFormatter germanFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(Locale.GERMAN);
    final LocalDate from = LocalDate.parse("03.01.2000", germanFormatter);

    final List<Security> securities = new ArrayList<>();
    securities.add(createSecurity("NESN.SW", GlobalConstants.STOCK_EX_MIC_SIX, GlobalConstants.MC_CHF,
        SpecialInvestmentInstruments.DIRECT_INVESTMENT));
    securities.add(createSecurity("AAPL", "America/New_York", GlobalConstants.MC_USD,
        SpecialInvestmentInstruments.DIRECT_INVESTMENT));
    securities.add(createSecurity("TLT", "America/New_York", GlobalConstants.MC_USD, SpecialInvestmentInstruments.ETF));

    securities.parallelStream().forEach(security -> {
      List<Dividend> dividens = new ArrayList<>();
      try {
        dividens = yahooFeedConnector.getDividendHistory(security, from);
      } catch (final Exception e) {
        e.printStackTrace();
      }
      assertThat(dividens.size()).isGreaterThanOrEqualTo(1);
    });
  }

  private Security createSecurity(final String ticker, final String mic, String currency,
      SpecialInvestmentInstruments specialInvestmentInstrument) {
    final Stockexchange stockexchange = new Stockexchange();
    stockexchange.setMic(mic);
    Assetclass assetclass = new Assetclass();
    assetclass.setSpecialInvestmentInstrument(specialInvestmentInstrument);
    final Security security = new Security();
    security.setAssetClass(assetclass);
    security.setStockexchange(stockexchange);
    security.setUrlHistoryExtend(ticker);
    security.setUrlIntraExtend(ticker);
    security.setUrlSplitExtend(ticker);
    security.setUrlDividendExtend(ticker);
    security.setCurrency(currency);
    return security;
  }

}
