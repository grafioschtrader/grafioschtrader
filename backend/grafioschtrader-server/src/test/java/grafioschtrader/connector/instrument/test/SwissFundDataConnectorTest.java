package grafioschtrader.connector.instrument.test;

import static org.junit.Assert.assertTrue;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import grafioschtrader.connector.instrument.swissfunddata.SwissFundDataConnector;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;

class SwissFundDataConnectorTest {

  @Test
  void getEodSecurityHistoryTest() {
    final List<Security> securities = new ArrayList<>();

    final DateTimeFormatter germanFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(Locale.GERMAN);
    final LocalDate from = LocalDate.parse("05.01.2018", germanFormatter);
    final LocalDate to = LocalDate.parse("06.12.2022", germanFormatter);

    SwissFundDataConnector swissFundDataConnector = new SwissFundDataConnector();
    final Date fromDate = Date.from(from.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
    final Date toDate = Date.from(to.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());

    securities.add(createSecurity("Migros Bank (CH) Fonds 45 V", "79260"));
    securities.add(createSecurity("Focused SICAV - Global Bond (EUR hedged) F-UKdist", "46234"));
    securities.parallelStream().forEach(security -> {

      List<Historyquote> historyquote = new ArrayList<>();
      try {
        historyquote = swissFundDataConnector.getEodSecurityHistory(security, fromDate, toDate);
      } catch (final Exception e) {
        e.printStackTrace();
      }
      System.out.println(security.getName() + " Size: " + historyquote.size());
      assertTrue(historyquote.size() >= 1240);
    });

  }

  private Security createSecurity(final String name, final String historyExtended) {
    final Security security = new Security();
    security.setName(name);
    security.setUrlHistoryExtend(historyExtended);
    return security;
  }
}
