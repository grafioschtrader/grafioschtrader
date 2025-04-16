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
    updateSecurityLastPrice();
  }

  @Test
  void getEodSecurityHistoryTest() {
    getEodSecurityHistory(false);
  }

  @Override
  protected List<SecurityHistoricalDate> getHistoricalSecurities() {
    List<SecurityHistoricalDate> hisoricalDate = new ArrayList<>();
    try {
      hisoricalDate
          .add(new SecurityHistoricalDate("ANDRITZ AG", "AT0000730007", SpecialInvestmentInstruments.DIRECT_INVESTMENT,
              AssetclassType.EQUITIES, "740752", null, 5363, "2001-06-25", "2023-01-11"));
      hisoricalDate.add(new SecurityHistoricalDate("3 Banken Anleihefonds-Selektion", "AT0000744594",
          SpecialInvestmentInstruments.MUTUAL_FUND, AssetclassType.FIXED_INCOME, "8595610", null, 3755, "2004-11-04",
          "2023-01-09"));
      hisoricalDate.add(
          new SecurityHistoricalDate("ATX Index", "AT0000999982", SpecialInvestmentInstruments.NON_INVESTABLE_INDICES,
              AssetclassType.EQUITIES, "92866", null, 5728, "2000-01-03", "2023-01-10"));
      hisoricalDate.add(new SecurityHistoricalDate("Land NOE var. Schuldv. 13-28", "AT0000A11772",
          SpecialInvestmentInstruments.DIRECT_INVESTMENT, AssetclassType.FIXED_INCOME, "90108510", null, 21,
          "2022-12-05", "2023-01-09"));
      hisoricalDate.add(
          new SecurityHistoricalDate("iShares EURO STOXX 50 U.ETF", "IE0008471009", SpecialInvestmentInstruments.ETF,
              AssetclassType.EQUITIES, "200477480", null, 1272, "2017-10-16", "2023-01-09"));
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
