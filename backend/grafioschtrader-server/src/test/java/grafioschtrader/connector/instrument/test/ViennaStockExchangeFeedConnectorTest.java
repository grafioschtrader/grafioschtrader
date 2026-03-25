package grafioschtrader.connector.instrument.test;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.connector.instrument.test.ConnectorTestHelper.SecurityHistoricalDate;
import grafioschtrader.connector.instrument.vienna.ViennaStockExchangeFeedConnector;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.SpecialInvestmentInstruments;

public class ViennaStockExchangeFeedConnectorTest extends BaseFeedConnectorCheck {

  private ViennaStockExchangeFeedConnector viennaStockExchangeFeedConnector = new ViennaStockExchangeFeedConnector();

  @Test
  void updateSecurityLastPriceTest() {
    updateSecurityLastPriceByHistoricalData();
  }

  @Test
  void getEodSecurityHistoryTest() {
    getEodSecurityHistory(false);
  }

  @Override
  protected List<SecurityHistoricalDate> getHistoricalSecurities(HistoricalIntra histroricalIntra) {
    List<SecurityHistoricalDate> hisoricalDate = new ArrayList<>();
    String dateTo = "2026-03-23";
    try {
      hisoricalDate
      .add(new SecurityHistoricalDate("3 Banken Anleihefonds-Selektion", "AT0000744594", SpecialInvestmentInstruments.DIRECT_INVESTMENT,
          AssetclassType.FIXED_INCOME, "8595610", null, 4428, "2004-11-04", dateTo));
      hisoricalDate
          .add(new SecurityHistoricalDate("ANDRITZ AG", "AT0000730007", SpecialInvestmentInstruments.DIRECT_INVESTMENT,
              AssetclassType.EQUITIES, "740752", null, 6173, "2001-06-25", dateTo));
      hisoricalDate.add(
          new SecurityHistoricalDate("ATX Index", "AT0000999982", SpecialInvestmentInstruments.NON_INVESTABLE_INDICES,
              AssetclassType.EQUITIES, "92866", null, 6539, "2000-01-03", dateTo));
      hisoricalDate.add(
          new SecurityHistoricalDate("iShares EURO STOXX 50 U.ETF", "IE0008471009", SpecialInvestmentInstruments.ETF,
              AssetclassType.EQUITIES, "200477480", null, 2060, "2017-10-16", dateTo));
    } catch (ParseException pe) {
      pe.printStackTrace();
    }
    return hisoricalDate;
  }

  @Override
  protected IFeedConnector getIFeedConnector() {
    return viennaStockExchangeFeedConnector;
  }

}
