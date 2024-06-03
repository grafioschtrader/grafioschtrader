package grafioschtrader.connector.instrument.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import grafioschtrader.GlobalConstants;
import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.connector.instrument.boursorama.BoursoramaFeedConnector;
import grafioschtrader.connector.instrument.test.ConnectorTestHelper.CurrencyPairHistoricalDate;
import grafioschtrader.connector.instrument.test.ConnectorTestHelper.SecurityHistoricalDate;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.SpecialInvestmentInstruments;

public class BoursoramaFeedConnectorTest extends BaseFeedConnectorCheck {

  private BoursoramaFeedConnector boursoramaFeedConnector = new BoursoramaFeedConnector();

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
  protected List<SecurityHistoricalDate> getHistoricalSecurities() {
    List<SecurityHistoricalDate> hisoricalDate = new ArrayList<>();
    String dateTo = "2023-08-31";
    try {
      hisoricalDate.add(new SecurityHistoricalDate("iShares SMIM ETF (CH)", SpecialInvestmentInstruments.ETF, "2aCSSMIM",
          GlobalConstants.STOCK_EX_MIC_SIX, GlobalConstants.MC_CHF, 3195, "2010-12-09", dateTo));
      hisoricalDate.add(new SecurityHistoricalDate("0.362 Bank of New Zealand 21-29", null,
          SpecialInvestmentInstruments.DIRECT_INVESTMENT, AssetclassType.FIXED_INCOME, "2aBNZ01",
          GlobalConstants.STOCK_EX_MIC_SIX, GlobalConstants.MC_CHF, 135, "2021-12-15", dateTo));
      hisoricalDate.add(new SecurityHistoricalDate("Cisco", SpecialInvestmentInstruments.DIRECT_INVESTMENT, "CSCO",
          GlobalConstants.STOCK_EX_MIC_FRANCE, GlobalConstants.MC_EUR, 4686, "2005-01-03", dateTo));
      hisoricalDate.add(new SecurityHistoricalDate("Lyxor CAC 40", SpecialInvestmentInstruments.ETF, "1rPCAC",
          GlobalConstants.STOCK_EX_MIC_FRANCE, GlobalConstants.MC_EUR, 4007, "2008-01-02", dateTo));
      hisoricalDate.add(new SecurityHistoricalDate("ZKB Gold ETF (CHF)", SpecialInvestmentInstruments.ETF, "2aZGLD",
          GlobalConstants.STOCK_EX_MIC_SIX, GlobalConstants.MC_CHF, 4370, "2006-03-15", dateTo));
      hisoricalDate.add(new SecurityHistoricalDate("NASDAQ 100", SpecialInvestmentInstruments.NON_INVESTABLE_INDICES, "$COMPX",
          GlobalConstants.STOCK_EX_MIC_NASDAQ, GlobalConstants.MC_USD, 4698, "2005-01-03", dateTo));

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
  //=======================================
  @Test
  void getEodCurrencyHistoryTest() throws ParseException {
    final List<CurrencyPairHistoricalDate> currencies = new ArrayList<>();
    currencies.add(new CurrencyPairHistoricalDate(GlobalConstants.MC_CHF, "COP", 3280, "2011-08-04",
        "2024-05-30", "3fCHF_COP"));
    currencies.add(new CurrencyPairHistoricalDate(GlobalConstants.MC_JPY, GlobalConstants.MC_USD, 3280, "2011-08-04",
        "2024-05-30", "3fJPY_USD"));
    currencies.add(new CurrencyPairHistoricalDate(GlobalConstants.CC_BTC, GlobalConstants.MC_USD, 558, "2022-10-02",
        "2024-05-30", "9xBTCUSD"));

    currencies.parallelStream().forEach(cphd -> {
      List<Historyquote> historyquotes = new ArrayList<>();
      try {
        historyquotes = boursoramaFeedConnector.getEodCurrencyHistory(cphd.currencypair, cphd.from, cphd.to);
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


}
