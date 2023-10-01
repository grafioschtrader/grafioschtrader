package grafioschtrader.connector.instrument.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import grafioschtrader.connector.instrument.test.ConnectorTestHelper.SecurityHisoricalDate;
import grafioschtrader.connector.instrument.xetra.XetraFeedConnector;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.SpecialInvestmentInstruments;

public class XetraFeedConnectorTest {

  private XetraFeedConnector xetraFeedConnector = new XetraFeedConnector();

  @Test
  void updateSecurityLastPriceTest() {

    final List<SecurityHisoricalDate> hisoricalDate = getHistoricalSecurities();
    hisoricalDate.parallelStream().forEach(hd -> {
      hd.security.setUrlIntraExtend(hd.security.getUrlHistoryExtend());
      hd.security.setUrlHistoryExtend(null);
      try {
        xetraFeedConnector.updateSecurityLastPrice(hd.security);
      } catch (final Exception e) {
        e.printStackTrace();
      }
      assertThat(hd.security.getSLast()).isNotNull().isGreaterThan(0.0);
    });
  }

  @Test
  void getEodSecurityHistoryTest() {
    final List<SecurityHisoricalDate> hisoricalDate = getHistoricalSecurities();
    hisoricalDate.parallelStream().forEach(hd -> {
      List<Historyquote> historyquote = new ArrayList<>();
      try {
        historyquote = xetraFeedConnector.getEodSecurityHistory(hd.security, hd.from, hd.to);
      } catch (final Exception e) {
        e.printStackTrace();
      }
      assertThat(historyquote.size()).isEqualTo(hd.expectedRows);
      assertThat(historyquote.get(0).getDate()).isEqualTo(hd.from);
      assertThat(historyquote.get(historyquote.size() - 1).getDate()).isEqualTo(hd.to);

    });

  }

  private List<SecurityHisoricalDate> getHistoricalSecurities() {
    List<SecurityHisoricalDate> hisoricalDate = new ArrayList<>();
    try {
      hisoricalDate.add(new SecurityHisoricalDate("EURO STOXX 50", "EU0009658145",
          SpecialInvestmentInstruments.NON_INVESTABLE_INDICES, AssetclassType.EQUITIES, "ARIVA:EU0009658145",
          XetraFeedConnector.STOCK_EX_MIC_XETRA, 2546, "2013-01-02", "2023-01-18"));
      hisoricalDate.add(new SecurityHisoricalDate("NORIS-FONDS", "DE0008492356", SpecialInvestmentInstruments.MUTUAL_FUND,
          AssetclassType.EQUITIES, "XFRA:DE0008492356", XetraFeedConnector.STOCK_EX_MIC_FRANKFURT, 2060, "2014-11-11",
          "2023-01-18"));
      hisoricalDate.add(new SecurityHisoricalDate("Deutsche Lufthansa AG 4,382", "XS1271836600",
          SpecialInvestmentInstruments.DIRECT_INVESTMENT, AssetclassType.FIXED_INCOME, "XS1271836600",
          XetraFeedConnector.STOCK_EX_MIC_FRANKFURT, 1889, "2015-08-06", "2023-01-18"));
      hisoricalDate.add(new SecurityHisoricalDate("DAX 40", "DE0008469008", SpecialInvestmentInstruments.NON_INVESTABLE_INDICES,
          AssetclassType.EQUITIES, "DE0008469008", XetraFeedConnector.STOCK_EX_MIC_XETRA, 5864, "2000-01-03",
          "2023-01-18"));
      hisoricalDate.add(new SecurityHisoricalDate("Beiersdorf Aktiengesellschaft", "DE0005200000",
          SpecialInvestmentInstruments.DIRECT_INVESTMENT, AssetclassType.EQUITIES, "DE0005200000",
          XetraFeedConnector.STOCK_EX_MIC_XETRA, 5876, "2000-01-03", "2023-01-18"));
      hisoricalDate.add(new SecurityHisoricalDate("iShares Core MSCI World UCITS ETF", "IE00B4L5Y983",
          SpecialInvestmentInstruments.ETF, AssetclassType.EQUITIES, "IE00B4L5Y983",
          XetraFeedConnector.STOCK_EX_MIC_XETRA, 3325, "2009-10-14", "2023-01-18"));
      hisoricalDate.add(new SecurityHisoricalDate("Nickelpreis", "XC0005705543", SpecialInvestmentInstruments.DIRECT_INVESTMENT,
          AssetclassType.COMMODITIES, "ARIVA:XC0005705543", XetraFeedConnector.STOCK_EX_MIC_FRANKFURT, 2513,
          "2013-01-02", "2023-01-18"));
    } catch (ParseException pe) {
      pe.printStackTrace();
    }
    return hisoricalDate;
  }
}
