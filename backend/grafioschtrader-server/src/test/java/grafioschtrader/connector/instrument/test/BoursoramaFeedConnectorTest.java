package grafioschtrader.connector.instrument.test;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import grafioschtrader.GlobalConstants;
import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.connector.instrument.boursorama.BoursoramaFeedConnector;
import grafioschtrader.connector.instrument.test.ConnectorTestHelper.CurrencyPairHistoricalDate;
import grafioschtrader.connector.instrument.test.ConnectorTestHelper.SecurityHistoricalDate;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.SpecialInvestmentInstruments;

public class BoursoramaFeedConnectorTest extends BaseFeedConnectorCheck {

  private BoursoramaFeedConnector boursoramaFeedConnector = new BoursoramaFeedConnector();

  // Security price tests
  // =======================================
  @Test
  void getEodSecurityHistoryTest() {
    getEodSecurityHistory(false);
  }

  @Test
  void updateSecurityLastPriceTest() {
    updateSecurityLastPriceByHistoricalData();
  }

  @Override
  protected List<SecurityHistoricalDate> getHistoricalSecurities(HistoricalIntra histroricalIntra) {
    List<SecurityHistoricalDate> hisoricalDate = new ArrayList<>();
    String dateTo = "2026-02-09";
    try {
      hisoricalDate.add(new SecurityHistoricalDate("iShares SMIM ETF (CH)", SpecialInvestmentInstruments.ETF,
          "2aCSSMIM", GlobalConstants.STOCK_EX_MIC_SIX, GlobalConstants.MC_CHF, 3804, "2010-12-09", dateTo));
      hisoricalDate.add(new SecurityHistoricalDate("0.362 Bank of New Zealand 21-29", null,
          SpecialInvestmentInstruments.DIRECT_INVESTMENT, AssetclassType.FIXED_INCOME, "2aBNZ01",
          GlobalConstants.STOCK_EX_MIC_SIX, GlobalConstants.MC_CHF, 417, "2021-12-15", "2026-02-06"));
      hisoricalDate.add(new SecurityHistoricalDate("Cisco", SpecialInvestmentInstruments.DIRECT_INVESTMENT, "CSCO",
          GlobalConstants.STOCK_EX_MIC_NASDAQ, GlobalConstants.MC_USD, 5018, "2006-02-13", dateTo));
      hisoricalDate.add(new SecurityHistoricalDate("Lyxor CAC 40", SpecialInvestmentInstruments.ETF, "1rPCAC",
          GlobalConstants.STOCK_EX_MIC_FRANCE, GlobalConstants.MC_EUR, 4629, "2008-01-02", dateTo));
      hisoricalDate.add(new SecurityHistoricalDate("ZKB Gold ETF (CHF)", SpecialInvestmentInstruments.ETF, "2aZGLD",
          GlobalConstants.STOCK_EX_MIC_SIX, GlobalConstants.MC_CHF, 4979, "2006-03-15", dateTo));
      hisoricalDate.add(new SecurityHistoricalDate("NASDAQ 100", SpecialInvestmentInstruments.NON_INVESTABLE_INDICES,
          "$COMPX", GlobalConstants.STOCK_EX_MIC_NASDAQ, GlobalConstants.MC_USD, 5013, "2006-03-06", dateTo));

    } catch (ParseException pe) {
      pe.printStackTrace();
    }
    return hisoricalDate;
  }

  @Override
  protected IFeedConnector getIFeedConnector() {
    return boursoramaFeedConnector;
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
      currencies.add(
          new CurrencyPairHistoricalDate(GlobalConstants.MC_CHF, "COP", 3507, "2011-08-04", "2025-04-17", "3fCHF_COP"));
      currencies.add(new CurrencyPairHistoricalDate(GlobalConstants.MC_JPY, GlobalConstants.MC_USD, 3507, "2011-08-04",
          "2025-04-17", "3fJPY_USD"));
      currencies.add(new CurrencyPairHistoricalDate(GlobalConstants.CC_BTC, GlobalConstants.MC_USD, 869, "2022-10-02",
          "2025-04-17", "9xBTCUSD"));
    } catch (ParseException pe) {
      pe.printStackTrace();
    }
    return currencies;
  }

}
