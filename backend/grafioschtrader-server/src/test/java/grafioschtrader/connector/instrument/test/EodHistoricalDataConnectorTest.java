package grafioschtrader.connector.instrument.test;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import grafioschtrader.GlobalConstants;
import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.connector.instrument.eodhistoricaldata.EodHistoricalDataConnector;
import grafioschtrader.connector.instrument.test.ConnectorTestHelper.CurrencyPairHistoricalDate;
import grafioschtrader.connector.instrument.test.ConnectorTestHelper.SecurityHistoricalDate;
import grafioschtrader.test.start.GTforTest;
import grafioschtrader.types.SpecialInvestmentInstruments;

@SpringBootTest(classes = GTforTest.class)
public class EodHistoricalDataConnectorTest extends BaseFeedConnectorCheck {

  private final EodHistoricalDataConnector eodHistoricalDataConnector;

  @Autowired
  public EodHistoricalDataConnectorTest(EodHistoricalDataConnector eodHistoricalDataConnector) {
    this.eodHistoricalDataConnector = eodHistoricalDataConnector;
    assumeTrue(eodHistoricalDataConnector.isActivated());
  }


  // Security price tests
  // =======================================
  @Test
  void updateSecurityLastPriceTest() {
    updateSecurityLastPriceByHistoricalData();
  }

  @Test
  void getEodSecurityHistoryTest() {
    getEodSecurityHistory(false);
  }

  @Override
  protected List<SecurityHistoricalDate> getHistoricalSecurities() {
    List<SecurityHistoricalDate> hisoricalDate = new ArrayList<>();
    try {
      hisoricalDate.add(new SecurityHistoricalDate("Cisco", SpecialInvestmentInstruments.DIRECT_INVESTMENT, "AF.PA",
          GlobalConstants.STOCK_EX_MIC_FRANCE, GlobalConstants.MC_EUR, 6056, "2000-01-03", "2023-08-31"));
      hisoricalDate.add(new SecurityHistoricalDate("Cisco", SpecialInvestmentInstruments.DIRECT_INVESTMENT, "csco",
          GlobalConstants.STOCK_EX_MIC_NASDAQ, GlobalConstants.MC_USD, 5926, "2000-01-03", "2023-07-24"));
      hisoricalDate.add(new SecurityHistoricalDate("Lyxor CAC 40", SpecialInvestmentInstruments.ETF, "CAC.PA",
          GlobalConstants.STOCK_EX_MIC_FRANCE, GlobalConstants.MC_EUR, 4011, "2008-01-02", "2023-08-31"));
      hisoricalDate.add(new SecurityHistoricalDate("iShares SMIM ETF (CH)", SpecialInvestmentInstruments.ETF,
          "CSSMIM.SW", GlobalConstants.STOCK_EX_MIC_SIX, GlobalConstants.MC_CHF, 4705, "2004-12-09", "2023-08-31"));
      hisoricalDate.add(new SecurityHistoricalDate("ZKB Gold ETF (CHF)", SpecialInvestmentInstruments.ETF, "ZGLD.SW",
          GlobalConstants.STOCK_EX_MIC_SIX, GlobalConstants.MC_CHF, 4206, "2006-03-15", "2023-08-31"));
      hisoricalDate.add(new SecurityHistoricalDate("NASDAQ 100", SpecialInvestmentInstruments.NON_INVESTABLE_INDICES,
          "NDX.INDX", GlobalConstants.STOCK_EX_MIC_NASDAQ, GlobalConstants.MC_USD, 5954, "2000-01-03", "2023-08-31"));
    } catch (ParseException pe) {
      pe.printStackTrace();
    }
    return hisoricalDate;
  }

  @Override
  protected IFeedConnector getIFeedConnector() {
    return eodHistoricalDataConnector;
  }

  // Currency pair price tests
  // =======================================

  @Test
  void getEodCurrencyHistoryTest() {
    getEodCurrencyHistory();
  }

  @Test
  void updateCurrencyPairLastPriceTest() {
    updateCurrencyPairLastPrice();
  }



  @Override
  protected List<CurrencyPairHistoricalDate> getHistoricalCurrencies() {
    String oldestDate = "2000-01-03";
    final List<CurrencyPairHistoricalDate> currencies = new ArrayList<>();
    try {
      currencies.add(new CurrencyPairHistoricalDate("ZAR", "NOK", 7231, oldestDate, "2025-05-09"));
      currencies.add(
          new CurrencyPairHistoricalDate(GlobalConstants.MC_EUR, GlobalConstants.MC_CHF, 6687, oldestDate, "2025-05-09"));
      currencies.add(
          new CurrencyPairHistoricalDate(GlobalConstants.MC_USD, GlobalConstants.MC_CHF, 6690, oldestDate, "2025-05-09"));
      currencies.add(new CurrencyPairHistoricalDate(GlobalConstants.CC_BTC, GlobalConstants.MC_USD, 3888, "2014-09-17",
          "2025-05-09"));
    } catch (ParseException pe) {
      pe.printStackTrace();
    }
    return currencies;
  }


  // Split and dividend tests
  // =======================================
  @Test
  void getSplitsTest() throws ParseException {
    ConnectorTestHelper.standardSplitTest(eodHistoricalDataConnector, getSymbolMapping());
  }

  @Test
  void getDividendHistoryTest() throws ParseException {
    ConnectorTestHelper.standardDividendTest(eodHistoricalDataConnector, getSymbolMapping(),
        Map.of(ConnectorTestHelper.ISIN_TLT, 259));
  }

  private Map<String, String> getSymbolMapping() {
    return Map.of("HUBG", "HUBG.US", "AAPL", "AAPL.US", "NKE", "NKE.US", "TLT", "TLT.US", "WMT", "WMT.US");
  }

}
