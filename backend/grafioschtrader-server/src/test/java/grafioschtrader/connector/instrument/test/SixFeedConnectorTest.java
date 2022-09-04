package grafioschtrader.connector.instrument.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import grafioschtrader.connector.instrument.six.SixFeedConnector;
import grafioschtrader.entities.Assetclass;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.types.SpecialInvestmentInstruments;

class SixFeedConnectorTest {

  private SixFeedConnector swissquoteConnector = new SixFeedConnector();
  
  @Test
  void getEodSecurityHistoryTest() {

    final List<Security> securities = new ArrayList<>();

    final DateTimeFormatter germanFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(Locale.GERMAN);
    final LocalDate from = LocalDate.parse("03.12.2018", germanFormatter);
    final LocalDate to = LocalDate.parse("04.06.2021", germanFormatter);

    final Date fromDate = Date.from(from.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
    final Date toDate = Date.from(to.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());

    securities.add(createSecurity("SMI PR", "CH0009980894CHF9", false));
    securities.add(createSecurity("1 HOLCIM 15-25", "CH0306179125CHF4", false));
    securities.add(createSecurity("ABB Ltd", "CH0012221716CHF4", false));

    securities.parallelStream().forEach(security -> {
      List<Historyquote> historyquote = new ArrayList<>();
      try {
        historyquote = swissquoteConnector.getEodSecurityHistory(security, fromDate, toDate);
      } catch (final Exception e) {
        e.printStackTrace();
      }
      assertThat(historyquote.size()).isEqualTo(624);
    });
  }

  @Test
  void updateSecurityLastPriceTest() {
    final List<Security> securities = new ArrayList<>();

    securities.add(createSecurity("ZKB Silver ETF - A (CHF)", "CH0183135976CHF4", true));
    securities.add(createSecurity("SMI PR", "CH0009980894CHF9", true));
    securities.add(createSecurity("1 HOLCIM 15-25", "CH0306179125CHF4", true));
    securities.add(createSecurity("ABB Ltd", "CH0012221716CHF4", true));
    securities.parallelStream().forEach(security -> {
      try {
        swissquoteConnector.updateSecurityLastPrice(security);
      } catch (final Exception e) {
        e.printStackTrace();
      }
      assertThat(security.getSLast()).isGreaterThan(0.0);
    });

  }

  private Security createSecurity(final String name, final String ticker, boolean intra) {
    final Security security = new Security();
    security.setName(name);
    if(intra) {
      security.setUrlIntraExtend(ticker);
    } else {
      security.setUrlHistoryExtend(ticker);
    }
    final Assetclass assetclass = new Assetclass();
    assetclass.setSpecialInvestmentInstrument(SpecialInvestmentInstruments.ETF);
    security.setAssetClass(assetclass);
    return security;
  }

}
