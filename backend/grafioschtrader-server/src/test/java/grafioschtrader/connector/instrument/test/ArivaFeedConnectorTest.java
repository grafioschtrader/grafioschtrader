package grafioschtrader.connector.instrument.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import grafioschtrader.connector.instrument.ariva.ArivaFeedConnector;
import grafioschtrader.entities.Assetclass;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.Language;
import grafioschtrader.types.SpecialInvestmentInstruments;

class ArivaFeedConnectorTest {

  @Test
  void getEodSecurityHistoryTest() {

    final ArivaFeedConnector arivaConnector = new ArivaFeedConnector();
    final DateTimeFormatter germanFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(Locale.GERMAN);
    final LocalDate from = LocalDate.parse("03.12.2018", germanFormatter);
    final LocalDate to = LocalDate.parse("25.10.2019", germanFormatter);

    final Date fromDate = Date.from(from.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
    final Date toDate = Date.from(to.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());

    final List<Security> securities = getStocks();

    securities.parallelStream().forEach(security -> {

      List<Historyquote> historyquote = new ArrayList<>();
      try {
        historyquote = arivaConnector.getEodSecurityHistory(security, fromDate, toDate);
      } catch (final Exception e) {
        e.printStackTrace();
      }
      assertEquals(historyquote.size(), 225);
    });
  }

  private List<Security> getStocks() {
    final List<Security> securities = new ArrayList<>();
    securities.add(createSecurity("ZinsFix Express StepDown 08 18/21 Zertifikat auf BMW St [DZ Bank]", "DE000DGE3N66", AssetclassType.MULTI_ASSET, "136307168&boerse_id=47"));
    securities.add(createSecurity("Allianz Aktie", "DE0008404005", AssetclassType.EQUITIES, "292&boerse_id=6"));
    securities.add(createSecurity("Software AG", "DE000A2GS401", AssetclassType.EQUITIES, "121673&boerse_id=6"));
    securities.add(createSecurity("2,375% Daimler AG 13/23", "DE000A1R0691", AssetclassType.FIXED_INCOME,
        "111198042&boerse_id=1"));
    return securities;
  }

  private Security createSecurity(final String name, final String intraTicker, AssetclassType assetclassType,
      final String urlQuoteFeedExtend) {
    final Security security = new Security();
    security.setName(name);
    security.setUrlHistoryExtend(urlQuoteFeedExtend);
    security.setUrlIntraExtend(urlQuoteFeedExtend);

    security.setAssetClass(new Assetclass(assetclassType, "Bond/Aktien Schweiz",
        SpecialInvestmentInstruments.DIRECT_INVESTMENT, Language.GERMAN));
    return security;
  }

}
