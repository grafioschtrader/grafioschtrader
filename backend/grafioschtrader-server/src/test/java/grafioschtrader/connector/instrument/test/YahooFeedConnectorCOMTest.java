package grafioschtrader.connector.instrument.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafioschtrader.GlobalConstants;
import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.connector.instrument.test.ConnectorTestHelper.CurrencyPairHistoricalDate;
import grafioschtrader.connector.instrument.test.ConnectorTestHelper.SecurityHistoricalDate;
import grafioschtrader.connector.instrument.yahoo.YahooFeedConnectorCOM;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Security;
import grafioschtrader.types.SpecialInvestmentInstruments;

class YahooFeedConnectorCOMTest extends BaseFeedConnectorCheck {

  private YahooFeedConnectorCOM yahooFeedConnector = new YahooFeedConnectorCOM();

  // Security price tests
  // =======================================
  @Test
  void getEodSecurityHistoryTest() {
    getEodSecurityHistory(false, true);
  }

  @Test
  void updateSecurityLastPriceTest() {
    updateSecurityLastPriceByHistoricalData();
  }

  @Test
  @DisplayName("Spark batch: all intraday securities and currency pairs with one request each")
  void updateLastPriceBatchTest() throws Exception {
    List<Security> securities = getHistoricalSecurities(HistoricalIntra.INTRADAY).stream().map(hd -> hd.security)
        .toList();
    securities.forEach(security -> security.setUrlIntraExtend(security.getUrlHistoryExtend()));
    List<Currencypair> currencypairs = getHistoricalCurrencies().stream().map(hd -> hd.currencypair).toList();
    assertThat(securities.size()).isLessThanOrEqualTo(yahooFeedConnector.getIntradayBatchSize());

    assertThat(yahooFeedConnector.updateSecuritiesLastPrice(securities)).isEmpty();
    assertThat(yahooFeedConnector.updateCurrencyPairsLastPrice(currencypairs)).isEmpty();

    securities.forEach(s -> assertThat(s.getSLast()).as("Last price for " + s.getName()).isGreaterThan(0.0));
    currencypairs.forEach(c -> assertThat(c.getSLast()).as("Last price for " + c.getName()).isGreaterThan(0.0));
  }

  @Override
  protected List<SecurityHistoricalDate> getHistoricalSecurities(HistoricalIntra histroricalIntra) {
    List<SecurityHistoricalDate> hisoricalDate = new ArrayList<>();
    try {
      hisoricalDate.add(new SecurityHistoricalDate("S&P/ASX All Ordinaries",
          SpecialInvestmentInstruments.NON_INVESTABLE_INDICES, "^AORD", GlobalConstants.STOCK_EX_MIC_AUSTRALIA,
          GlobalConstants.MC_AUD, 6392, "2000-01-04", "2025-04-11"));
      hisoricalDate.add(new SecurityHistoricalDate("Cisco", SpecialInvestmentInstruments.DIRECT_INVESTMENT, "csco",
          GlobalConstants.STOCK_EX_MIC_NASDAQ, GlobalConstants.MC_USD, 5926, "2000-01-03", "2023-07-24"));
      hisoricalDate.add(new SecurityHistoricalDate("NASDAQ 100", SpecialInvestmentInstruments.NON_INVESTABLE_INDICES,
          "^NDX", GlobalConstants.STOCK_EX_MIC_NASDAQ, GlobalConstants.MC_USD, 5926, "2000-01-03", "2023-07-24"));
      hisoricalDate.add(new SecurityHistoricalDate("Tesla", SpecialInvestmentInstruments.DIRECT_INVESTMENT, "TSLA",
          GlobalConstants.STOCK_EX_MIC_NASDAQ, GlobalConstants.MC_USD, 3289, "2010-06-29", "2023-07-24"));
      hisoricalDate.add(new SecurityHistoricalDate("Nestlé S.A", SpecialInvestmentInstruments.DIRECT_INVESTMENT,
          "NESN.SW", GlobalConstants.STOCK_EX_MIC_SIX, GlobalConstants.MC_CHF, 5911, "2000-04-21", "2023-07-24"));
      hisoricalDate.add(new SecurityHistoricalDate("Tesco PLC", SpecialInvestmentInstruments.DIRECT_INVESTMENT,
          "TSCO.L", GlobalConstants.STOCK_EX_MIC_UK, GlobalConstants.MC_GBP, 6000, "2000-01-03", "2023-07-24"));
      hisoricalDate.add(new SecurityHistoricalDate("Lyxor CAC 40", SpecialInvestmentInstruments.ETF, "CAC.PA",
          GlobalConstants.STOCK_EX_MIC_FRANCE, GlobalConstants.MC_EUR, 3982, "2008-01-02", "2023-07-24"));
      hisoricalDate.add(new SecurityHistoricalDate("UBSFund Solutions - CMCI Oil SF ETF",
          SpecialInvestmentInstruments.ETF, "OILUSA.SW", GlobalConstants.STOCK_EX_MIC_FRANCE, GlobalConstants.MC_USD,
          3282, "2010-06-15", "2023-07-24"));
      if (histroricalIntra == HistoricalIntra.INTRADAY) {
        // Yahoo sends firstTradeDate: null for this FTSE index; Jackson 3 would reject a primitive long.
        hisoricalDate.add(new SecurityHistoricalDate("FTSE China 50 Index",
            SpecialInvestmentInstruments.NON_INVESTABLE_INDICES, "XIN0.FGI"));
      }
    } catch (ParseException pe) {
      pe.printStackTrace();
    }
    return hisoricalDate;
  }

  // Currency pair price tests
  // =======================================
  @Test
  void getEodCurrencyHistoryTest() throws ParseException {
    getEodCurrencyHistory();
  }

  @Test
  void updateCurrencyPairLastPriceTest() {
    updateCurrencyPairLastPrice();
  }

  @Override
  protected List<CurrencyPairHistoricalDate> getHistoricalCurrencies() {
    final List<CurrencyPairHistoricalDate> currencies = new ArrayList<>();
    try {
      currencies.add(new CurrencyPairHistoricalDate(GlobalConstants.MC_EUR, GlobalConstants.MC_CHF, 4863, "2005-01-03",
          "2023-09-29"));
      currencies.add(new CurrencyPairHistoricalDate(GlobalConstants.MC_USD, GlobalConstants.MC_CHF, 4875, "2005-01-03",
          "2023-09-29"));
      currencies.add(new CurrencyPairHistoricalDate(GlobalConstants.CC_BTC, GlobalConstants.MC_USD, 3300, "2014-09-17",
          "2023-09-29"));
    } catch (ParseException pe) {
      pe.printStackTrace();
    }
    return currencies;
  }

  // Split and dividend tests
  // =======================================
  @Test
  void getSplitsTest() throws ParseException {
    ConnectorTestHelper.standardSplitTest(yahooFeedConnector, null);
  }

  @Test
  void getDividendHistoryTest() throws ParseException {
    ConnectorTestHelper.standardDividendTest(yahooFeedConnector, null, null);
  }

  @Override
  protected IFeedConnector getIFeedConnector() {
    return yahooFeedConnector;
  }

}
