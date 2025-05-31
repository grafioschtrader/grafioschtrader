package grafioschtrader.connector.instrument.test;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import grafioschtrader.GlobalConstants;
import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.connector.instrument.finanzench.FinanzenCHFeedConnector;
import grafioschtrader.connector.instrument.test.ConnectorTestHelper.CurrencyPairHistoricalDate;
import grafioschtrader.connector.instrument.test.ConnectorTestHelper.SecurityHistoricalDate;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.SpecialInvestmentInstruments;

class FinanzenCHFeedConnectorTest extends BaseFeedConnectorCheck {
  private FinanzenCHFeedConnector finanzenCHFeedConnector = new FinanzenCHFeedConnector();

  // Security price tests
  // =======================================
  @Test
  void updateSecurityLastPriceTest() {
    updateSecurityLastPriceByHistoricalData();
  }

//  @Test
//  void getEodSecurityHistoryTest() {
//    getEodSecurityHistory(true);
//  }

  @Override
  protected List<SecurityHistoricalDate> getHistoricalSecurities() {
    List<SecurityHistoricalDate> hisoricalDate = new ArrayList<>();
    try {
      hisoricalDate.add(new SecurityHistoricalDate("iShares II PLC FTSE DEV MAR PROP ETF (USD)", "IE00B1FZS350",
          SpecialInvestmentInstruments.ETF, AssetclassType.EQUITIES,
          "ishares-developed-markets-property-yield-etf-ie00b1fzs350/swu", null, 6023, "2000-01-04", "2025-05-16"));
      hisoricalDate.add(
          new SecurityHistoricalDate("Geberit Aktie", "CH0030170408", SpecialInvestmentInstruments.DIRECT_INVESTMENT,
              AssetclassType.EQUITIES, "geberit-aktie/swx", null, 6023, "2000-01-04", "2023-12-08"));
      hisoricalDate.add(new SecurityHistoricalDate("Swisscanto (CH) Gold ETF EA CHF", "CH0139101593",
          SpecialInvestmentInstruments.ETF, AssetclassType.COMMODITIES, "swisscanto-ch-gold-etf-ea-ch0139101593/swx",
          null, 3508, "2010-01-04", "2023-12-08"));
      hisoricalDate.add(new SecurityHistoricalDate("Crédit Agricole S.A.SF-Preferred MTN 2021(29)", "CH1118460984",
          SpecialInvestmentInstruments.DIRECT_INVESTMENT, AssetclassType.FIXED_INCOME,
          "crédit_agricole_sasf-preferred_mtn_202129-obligation-2029-ch1118460984/swx", null, 633, "2021-06-16",
          "2023-12-08"));
      hisoricalDate.add(new SecurityHistoricalDate("Allianz Global Investors Fund - Allianz Income and Growth AMg2",
          "LU1597252862", SpecialInvestmentInstruments.MUTUAL_FUND, AssetclassType.MULTI_ASSET,
          "allianz-global-investors-fund-allianz-income-and-growth-amg2-h2-cad-lu1597252862/sonst", null, 1565,
          "2017-05-18", "2023-12-08"));
      hisoricalDate
          .add(new SecurityHistoricalDate("SLI", "CH0030252883", SpecialInvestmentInstruments.NON_INVESTABLE_INDICES,
              AssetclassType.EQUITIES, "SLI", null, 6023, "2000-01-04", "2023-12-08"));
    } catch (ParseException pe) {
      pe.printStackTrace();
    }
    return hisoricalDate;
  }

  // Currency pair price tests
  // =======================================
  // @Test
  void getEodCurrencyHistoryTest() throws ParseException {
    getEodCurrencyHistory(true);
  }

  @Test
  void updateCurrencyPairLastPriceTest() {
    updateCurrencyPairLastPrice();
  }

  @Override
  protected List<CurrencyPairHistoricalDate> getHistoricalCurrencies() {
    final List<CurrencyPairHistoricalDate> currencies = new ArrayList<>();
    try {
      currencies.add(new CurrencyPairHistoricalDate(GlobalConstants.MC_CHF, GlobalConstants.MC_EUR, 7707, "2000-01-03",
          "2025-04-17", "schweizer_franken-euro-kurs"));
      currencies.add(new CurrencyPairHistoricalDate(GlobalConstants.CC_BTC, GlobalConstants.MC_CHF, 4490, "2013-01-01",
          "2025-04-17", "bitcoin-franken-kurs"));
    } catch (ParseException pe) {
      pe.printStackTrace();
    }
    return currencies;
  }

  @Override
  protected IFeedConnector getIFeedConnector() {
    return finanzenCHFeedConnector;
  }

}
