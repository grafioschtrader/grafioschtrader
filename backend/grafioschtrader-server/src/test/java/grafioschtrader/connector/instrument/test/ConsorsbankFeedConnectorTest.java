package grafioschtrader.connector.instrument.test;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.connector.instrument.consorsbank.ConsorsbankFeedConnector;
import grafioschtrader.connector.instrument.test.ConnectorTestHelper.CurrencyPairHistoricalDate;
import grafioschtrader.connector.instrument.test.ConnectorTestHelper.SecurityHistoricalDate;
import grafioschtrader.entities.Security;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.SpecialInvestmentInstruments;

/**
 * Live provider checks over the available history, with fixed endpoints and row counts verified on 2026-10-11.
 * Investigate changes in provider coverage before updating expectations; do not derive them from the response.
 */
class ConsorsbankFeedConnectorTest extends BaseFeedConnectorCheck {
  private final ConsorsbankFeedConnector connector = new ConsorsbankFeedConnector();

  @Override
  protected IFeedConnector getIFeedConnector() {
    return connector;
  }

  @Test
  @DisplayName("Load complete equity, ETF and bond histories over multiple pages")
  void getEodSecurityHistoryTest() {
    getEodSecurityHistory(false);
  }

  @Test
  @DisplayName("Load decades of forex and crypto history, retaining weekend observations")
  void getEodCurrencyHistoryTest() {
    getEodCurrencyHistory(false);
  }

  @Test
  @DisplayName("Update current quotes for the historical securities")
  void updateSecurityLastPriceTest() {
    updateSecurityLastPriceByHistoricalData();
  }

  @Test
  @DisplayName("Keep the existing gold and Societe Generale intraday cases")
  void updateAdditionalSecurityLastPriceTest() {
    List<Security> securities = List.of(
        ConnectorTestHelper.createIntraSecurity("Gold", "_1326189,@W$",
            SpecialInvestmentInstruments.NON_INVESTABLE_INDICES, "GOLD", "USD"),
        ConnectorTestHelper.createIntraSecurity("0.25 Societe Generale 20-27", "_282883661,SWX",
            SpecialInvestmentInstruments.DIRECT_INVESTMENT, "XSWX", "CHF"));
    updateSecurityLastPrice(securities);
  }

  @Test
  @DisplayName("Update current forex and cryptocurrency quotes")
  void updateCurrencyPairLastPriceTest() {
    updateCurrencyPairLastPrice();
  }

  @Override
  protected List<SecurityHistoricalDate> getHistoricalSecurities(HistoricalIntra historicalIntra) {
    try {
      return List.of(
          new SecurityHistoricalDate("adidas", "DE000A1EWWW0", SpecialInvestmentInstruments.DIRECT_INVESTMENT,
              AssetclassType.EQUITIES, "_39471840,GER", "XETR", "EUR", 7555, "1996-12-13", "2026-10-09"),
          new SecurityHistoricalDate("SPDR S&P U.S. Energy Select Sector", "IE00BWBXM492",
              SpecialInvestmentInstruments.ETF, AssetclassType.EQUITIES, "_139582518,SWX", "XSWX", "USD", 2703,
              "2015-10-28", "2026-10-09"),
          new SecurityHistoricalDate("0.50 Glencore Capital Finance DAC 21-28", "CH1132966263",
              SpecialInvestmentInstruments.DIRECT_INVESTMENT, AssetclassType.FIXED_INCOME, "_350105114,SWX", "XSWX",
              "CHF", 986, "2021-09-03", "2026-10-07"));
    } catch (ParseException e) {
      throw new AssertionError("Invalid historical security test data", e);
    }
  }

  @Override
  protected List<CurrencyPairHistoricalDate> getHistoricalCurrencies() {
    try {
      List<CurrencyPairHistoricalDate> pairs = new ArrayList<>();
      pairs.add(new CurrencyPairHistoricalDate("EUR", "USD", 9155, "1999-01-04", "2026-10-09", "_1390634,@W$"));
      pairs.add(new CurrencyPairHistoricalDate("EUR", "CHF", 9239, "1997-01-23", "2026-10-09", "_1619898,@W$"));
      pairs.add(new CurrencyPairHistoricalDate("BTC", "USD", 5603, "2010-07-22", "2026-10-09", "_213086262,BTC"));
      pairs.add(new CurrencyPairHistoricalDate("BTC", "EUR", 5204, "2011-08-26", "2026-10-09", "_213086260,BTC"));
      pairs.add(new CurrencyPairHistoricalDate("ETH", "USD", 2775, "2018-03-15", "2026-10-09", "_213086261,BTC"));
      pairs.add(new CurrencyPairHistoricalDate("ETH", "EUR", 2775, "2018-03-15", "2026-10-09", "_213086259,BTC"));
      return pairs;
    } catch (ParseException e) {
      throw new AssertionError("Invalid historical currency test data", e);
    }
  }
}
