package grafioschtrader.connector.instrument.test;

import java.text.ParseException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import grafioschtrader.GlobalConstants;
import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.connector.instrument.six.SixFeedConnector;
import grafioschtrader.connector.instrument.test.ConnectorTestHelper.SecurityHistoricalDate;
import grafioschtrader.entities.Dividend;
import grafioschtrader.entities.Security;
import grafioschtrader.types.SpecialInvestmentInstruments;

class SixFeedConnectorTest extends BaseFeedConnectorCheck {

  private SixFeedConnector sixFeedConnector = new SixFeedConnector();

  @Test
  void updateSecurityLastPriceTest() {
    updateSecurityLastPriceByHistoricalData();
  }

  @Test
  void getEodSecurityHistoryTest() {
    getEodSecurityHistory(false);
  }

  /**
   * Reads ABB's dividend history from the live SIX v3 endpoint. The urlDividendExtend is the same
   * valorId (ISIN + currency + check digit) used for price history. Verifies the list is non-empty,
   * returned in ascending ex-date order, with positive raw amounts and a currency on every row.
   */
  @Test
  void getDividendHistoryTest() throws Exception {
    final Security security = ConnectorTestHelper.createDividendSecurity("ABB Ltd", "CH0012221716CHF4");
    final List<Dividend> dividends = sixFeedConnector.getDividendHistory(security,
        LocalDate.parse(GlobalConstants.OLDEST_TRADING_DAY));

    Assertions.assertThat(dividends).isNotEmpty();
    Assertions.assertThat(dividends).isSortedAccordingTo((d1, d2) -> d1.getExDate().compareTo(d2.getExDate()));
    Assertions.assertThat(dividends).allSatisfy(d -> {
      Assertions.assertThat(d.getAmount()).isGreaterThan(0d);
      Assertions.assertThat(d.getCurrency()).isNotBlank();
      Assertions.assertThat(d.getAmountAdjusted()).isNull();
    });
  }

  /**
   * A bond valorId has no dividends; the connector must return an empty list rather than fail.
   */
  @Test
  void getDividendHistoryBondReturnsEmptyTest() throws Exception {
    final Security security = ConnectorTestHelper.createDividendSecurity("0 SONOVA 19-29", "CH0419041592CHF4");
    final List<Dividend> dividends = sixFeedConnector.getDividendHistory(security,
        LocalDate.parse(GlobalConstants.OLDEST_TRADING_DAY));

    Assertions.assertThat(dividends).isEmpty();
  }

  @Override
  protected List<SecurityHistoricalDate> getHistoricalSecurities(HistoricalIntra histroricalIntra) {
    List<SecurityHistoricalDate> hisoricalDate = new ArrayList<>();
    try {
      hisoricalDate
      .add(new SecurityHistoricalDate("0 SONOVA 19-29", "CH0419041592", SpecialInvestmentInstruments.DIRECT_INVESTMENT,
          "CH0419041592CHF4", null, 1551, "2019-10-10", "2025-12-05"));
      hisoricalDate
          .add(new SecurityHistoricalDate("SMI PR", "CH0009980894", SpecialInvestmentInstruments.NON_INVESTABLE_INDICES,
              "CH0009980894CHF9", null, 6539, "2000-01-04", "2025-12-05"));
      hisoricalDate.add(new SecurityHistoricalDate("ABB Ltd", "CH0012221716",
          SpecialInvestmentInstruments.DIRECT_INVESTMENT, "CH0012221716CHF4", null, 6520, "2000-01-04", "2025-12-05"));
      hisoricalDate.add(new SecurityHistoricalDate("ZKB Silver ETF - A (CHF)", "CH0183135976",
          SpecialInvestmentInstruments.ETF, "CH0183135976CHF4", null, 4670, "2007-05-10", "2025-12-05"));
    } catch (ParseException pe) {
      pe.printStackTrace();
    }
    return hisoricalDate;
  }

  @Override
  protected IFeedConnector getIFeedConnector() {
    return sixFeedConnector;
  }

}
