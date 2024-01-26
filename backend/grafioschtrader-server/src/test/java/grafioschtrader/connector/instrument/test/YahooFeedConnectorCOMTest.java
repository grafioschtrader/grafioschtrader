package grafioschtrader.connector.instrument.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.text.ParseException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import grafioschtrader.GlobalConstants;
import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.connector.instrument.test.ConnectorTestHelper.CurrencyPairHisoricalDate;
import grafioschtrader.connector.instrument.test.ConnectorTestHelper.SecurityHisoricalDate;
import grafioschtrader.connector.instrument.yahoo.YahooFeedConnectorCOM;
import grafioschtrader.entities.Assetclass;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Dividend;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Stockexchange;
import grafioschtrader.types.SpecialInvestmentInstruments;

class YahooFeedConnectorCOMTest extends BaseFeedConnectorCheck {

  private YahooFeedConnectorCOM yahooFeedConnector = new YahooFeedConnectorCOM();

  // Security price tests
  //=======================================
  @Test
  void getEodSecurityHistoryTest() {
    getEodSecurityHistory(false);
  }

  @Test
  void updateSecurityLastPriceTest() {
    updateSecurityLastPrice();
  }


  @Override
  protected List<SecurityHisoricalDate> getHistoricalSecurities() {
    List<SecurityHisoricalDate> hisoricalDate = new ArrayList<>();
    try {
      hisoricalDate.add(new SecurityHisoricalDate("Cisco", SpecialInvestmentInstruments.DIRECT_INVESTMENT, "csco",
          GlobalConstants.STOCK_EX_MIC_NASDAQ, GlobalConstants.MC_USD, 5926, "2000-01-03", "2023-07-24"));
      hisoricalDate.add(new SecurityHisoricalDate("NASDAQ 100", SpecialInvestmentInstruments.NON_INVESTABLE_INDICES,
          "^NDX", GlobalConstants.STOCK_EX_MIC_NASDAQ, GlobalConstants.MC_USD, 5926, "2000-01-03", "2023-07-24"));
      hisoricalDate.add(new SecurityHisoricalDate("Tesla", SpecialInvestmentInstruments.DIRECT_INVESTMENT, "TSLA",
          GlobalConstants.STOCK_EX_MIC_NASDAQ, GlobalConstants.MC_USD, 3289, "2010-06-29", "2023-07-24"));
      hisoricalDate.add(new SecurityHisoricalDate("Nestlé S.A", SpecialInvestmentInstruments.DIRECT_INVESTMENT,
          "NESN.SW", GlobalConstants.STOCK_EX_MIC_SIX, GlobalConstants.MC_CHF, 5620, "2000-04-21", "2023-07-24"));
      hisoricalDate.add(new SecurityHisoricalDate("Tesco PLC", SpecialInvestmentInstruments.DIRECT_INVESTMENT, "TSCO.L",
          GlobalConstants.STOCK_EX_MIC_UK, GlobalConstants.MC_GBP, 5999, "2000-01-03", "2023-07-24"));
      hisoricalDate.add(new SecurityHisoricalDate("Lyxor CAC 40", SpecialInvestmentInstruments.ETF, "CAC.PA",
          GlobalConstants.STOCK_EX_MIC_FRANCE, GlobalConstants.MC_EUR, 3982, "2008-01-02", "2023-07-24"));
      hisoricalDate.add(new SecurityHisoricalDate("UBSFund Solutions - CMCI Oil SF ETF",
          SpecialInvestmentInstruments.ETF, "OILUSA.SW", GlobalConstants.STOCK_EX_MIC_FRANCE, GlobalConstants.MC_USD,
          3282, "2010-06-15", "2023-07-24"));
    } catch (ParseException pe) {
      pe.printStackTrace();
    }
    return hisoricalDate;
  }


 // Currency pair price tests
 //=======================================
  @Test
  void getEodCurrencyHistoryTest() throws ParseException {
    final List<CurrencyPairHisoricalDate> currencies = new ArrayList<>();
    currencies.add(new CurrencyPairHisoricalDate(GlobalConstants.MC_EUR, GlobalConstants.MC_CHF, 4863, "2005-01-03",
        "2023-09-29"));
    currencies.add(new CurrencyPairHisoricalDate(GlobalConstants.MC_USD, GlobalConstants.MC_CHF, 4875, "2005-01-03",
        "2023-09-29"));
    currencies.add(new CurrencyPairHisoricalDate(GlobalConstants.CC_BTC, GlobalConstants.MC_USD, 3300, "2014-09-17",
        "2023-09-29"));
    currencies.parallelStream().forEach(cphd -> {
      List<Historyquote> historyquotes = new ArrayList<>();
      try {
        historyquotes = yahooFeedConnector.getEodCurrencyHistory(cphd.currencypair, cphd.from, cphd.to);
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


  // Split and dividend tests
  //=======================================
  @Test
  void getSplitsTest() throws ParseException {
    ConnectorTestHelper.standardSplitTest(yahooFeedConnector);
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

  @Override
  protected IFeedConnector getIFeedConnector() {
    return yahooFeedConnector;
  }

}
