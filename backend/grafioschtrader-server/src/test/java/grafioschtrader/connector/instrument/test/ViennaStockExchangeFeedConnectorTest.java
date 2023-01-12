package grafioschtrader.connector.instrument.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import grafioschtrader.connector.instrument.test.ConnectorTestHelper.HisoricalDate;
import grafioschtrader.connector.instrument.vienna.ViennaStockExchangeFeedConnector;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.SpecialInvestmentInstruments;

public class ViennaStockExchangeFeedConnectorTest {

  private ViennaStockExchangeFeedConnector vsefc = new ViennaStockExchangeFeedConnector();

  @Test
  void updateSecurityLastPriceTest() {
    try {
      final List<HisoricalDate> hisoricalDate = getHistoricalSecurities();
      hisoricalDate.parallelStream().forEach(hd -> {
        hd.security.setUrlIntraExtend(hd.security.getUrlHistoryExtend());
        hd.security.setUrlHistoryExtend(null);
        try {
          vsefc.updateSecurityLastPrice(hd.security);
        } catch (final Exception e) {
          e.printStackTrace();
        }
        assertThat(hd.security.getSLast()).isNotNull().isGreaterThan(0.0);
      });
    } catch (ParseException pe) {
      pe.printStackTrace();
    }
  }

  @Test
  void getEodSecurityHistoryTest() {

    try {
      final List<HisoricalDate> hisoricalDate = getHistoricalSecurities();
      hisoricalDate.parallelStream().forEach(hd -> {
        List<Historyquote> historyquote = new ArrayList<>();
        try {
          historyquote = vsefc.getEodSecurityHistory(hd.security, hd.from, hd.to);
        } catch (final Exception e) {
          e.printStackTrace();
        }
        assertThat(historyquote.size()).isEqualTo(hd.expectedRows);
        assertThat(historyquote.get(0).getDate()).isEqualTo(hd.from);
        assertThat(historyquote.get(historyquote.size() - 1).getDate()).isEqualTo(hd.to);

      });

    } catch (ParseException pe) {
      pe.printStackTrace();
    }
  }

  private List<HisoricalDate> getHistoricalSecurities() throws ParseException {
    List<HisoricalDate> hisoricalDate = new ArrayList<>();
    hisoricalDate.add(new HisoricalDate("ANDRITZ AG", "AT0000730007",
        SpecialInvestmentInstruments.DIRECT_INVESTMENT, AssetclassType.EQUITIES, "740752", null, 5363,
        "2001-06-25", "2023-01-11"));
    hisoricalDate.add(new HisoricalDate("3 Banken Anleihefonds-Selektion", "AT0000A1LFF3",
        SpecialInvestmentInstruments.ISSUER_RISK_PRODUCT, AssetclassType.EQUITIES, "162838966", null, 1543,
        "2016-07-06", "2023-01-09"));
    hisoricalDate.add(
        new HisoricalDate("3 Banken Anleihefonds-Selektion", "AT0000744594", SpecialInvestmentInstruments.MUTUAL_FUND,
            AssetclassType.FIXED_INCOME, "8595610", null, 3755, "2004-11-04", "2023-01-09"));
    hisoricalDate
        .add(new HisoricalDate("ATX Index", "AT0000999982", SpecialInvestmentInstruments.NON_INVESTABLE_INDICES,
            AssetclassType.EQUITIES, "92866", null, 5728, "2000-01-03", "2023-01-10"));
    hisoricalDate.add(new HisoricalDate("CA Immo 1,875% Anleihe 17-24", "AT0000A1TBC2",
        SpecialInvestmentInstruments.DIRECT_INVESTMENT, AssetclassType.FIXED_INCOME, "182213360", null, 1131,
        "2017-02-22", "2023-01-09"));
    hisoricalDate.add(
        new HisoricalDate("Land NOE var. Schuldv. 13-28", "AT0000A11772", SpecialInvestmentInstruments.DIRECT_INVESTMENT,
            AssetclassType.FIXED_INCOME, "90108510", null, 21, "2022-12-05", "2023-01-09"));
    hisoricalDate.add(new HisoricalDate("iShares EURO STOXX 50 U.ETF", "IE0008471009", SpecialInvestmentInstruments.ETF,
        AssetclassType.EQUITIES, "200477480", null, 1272, "2017-10-16", "2023-01-09"));

    return hisoricalDate;
  }

}
