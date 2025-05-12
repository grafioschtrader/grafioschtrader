package grafioschtrader.connector.instrument.test;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import grafioschtrader.GlobalConstants;
import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.connector.instrument.investing.InvestingConnector;
import grafioschtrader.connector.instrument.test.ConnectorTestHelper.SecurityHistoricalDate;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.types.SpecialInvestmentInstruments;

class InvestingConnectorTest extends BaseFeedConnectorCheck {

  private InvestingConnector investingConnector = new InvestingConnector();

  // Security price tests
  // =======================================

  @Test
  void updateSecurityLastPriceTest() {
    updateSecurityLastPriceByHistoricalData();
  }

  @Override
  protected List<SecurityHistoricalDate> getHistoricalSecurities() {
    List<SecurityHistoricalDate> hisoricalDate = new ArrayList<>();
    try {
      hisoricalDate.add(new SecurityHistoricalDate("United States Treasury Note 6.25% May 15, 2030", SpecialInvestmentInstruments.DIRECT_INVESTMENT,
          "rates-bonds/usgovt-6.25-15-may-2030"));
      hisoricalDate.add(new SecurityHistoricalDate("iShares Russell 2000 ETF", SpecialInvestmentInstruments.ETF,
          "etfs/ishares-russell-2000-index-etf"));
      hisoricalDate.add(new SecurityHistoricalDate("Bitcoin Tracker EUR XBT Provider (SE0007525332)",
          SpecialInvestmentInstruments.ISSUER_RISK_PRODUCT, "etfs/bitcoin-tracker-eur-xbt-provider"));
      hisoricalDate.add(new SecurityHistoricalDate("CAC 40", SpecialInvestmentInstruments.NON_INVESTABLE_INDICES,
          "indices/france-40"));
    } catch (ParseException pe) {
      pe.printStackTrace();
    }
    return hisoricalDate;
  }

  // Currency pair price tests
  // =======================================
  @Test
  void updateCurrencyPairLastPriceTest() {
    updateCurrencyPairLastPrice(getCurrencies());
  }

  protected List<Currencypair> getCurrencies() {
    final List<Currencypair> currencies = new ArrayList<>();
    // currencies.add(ConnectorTestHelper.createCurrencyPair(GlobalConstants.CC_BTC,
    // GlobalConstants.MC_USD));
    currencies.add(ConnectorTestHelper.createCurrencyPair(GlobalConstants.MC_EUR, GlobalConstants.MC_CHF));
    currencies.add(ConnectorTestHelper.createCurrencyPair(GlobalConstants.MC_CHF, "AUD"));
    currencies.add(ConnectorTestHelper.createCurrencyPair(GlobalConstants.MC_USD, GlobalConstants.MC_CHF));
    currencies.add(ConnectorTestHelper.createCurrencyPair(GlobalConstants.MC_USD, GlobalConstants.MC_EUR));
    currencies.add(ConnectorTestHelper.createCurrencyPair(GlobalConstants.MC_GBP, GlobalConstants.MC_EUR));
    return currencies;
  }

  @Override
  protected IFeedConnector getIFeedConnector() {
    return investingConnector;
  }

}
