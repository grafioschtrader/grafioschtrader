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

import grafioschtrader.connector.instrument.onvista.OnVistaFeedConnector;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;

class OnVistaFeedConnectorTest {

  @Test
  void getEodCurrencyHistoryTest() {
    final DateTimeFormatter germanFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(Locale.GERMAN);
    final OnVistaFeedConnector onVistaConnector = new OnVistaFeedConnector();
    final List<Currencypair> currencies = new ArrayList<>();
    currencies.add(ConnectorTestHelper.createHistoricalCurrencyPair("EUR", "USD", "8381868"));
    final LocalDate from = LocalDate.parse("01.12.2017", germanFormatter);
    final Date fromDate = Date.from(from.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
    final LocalDate to = LocalDate.parse("25.10.2019", germanFormatter);
    Date toDate = Date.from(to.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
    currencies.parallelStream().forEach(currencyPair -> {
      List<Historyquote> historyquote = new ArrayList<>();
      try {
        historyquote = onVistaConnector.getEodCurrencyHistory(currencyPair, fromDate, toDate);
      } catch (Exception e) {
        e.printStackTrace();
      }
      assertThat(historyquote.size()).isEqualByComparingTo(694);
    });
  }

  @Test
  void getEodSecurityHistoryTest() {

    final OnVistaFeedConnector onVistaConnector = new OnVistaFeedConnector();
    final List<Security> securities = new ArrayList<>();

    final DateTimeFormatter germanFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(Locale.GERMAN);
    final LocalDate from = LocalDate.parse("01.01.2000", germanFormatter);
    final LocalDate to = LocalDate.parse("25.10.2019", germanFormatter);

    final Date fromDate = Date.from(from.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
    final Date toDate = Date.from(to.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());

    securities.add(createSecurity("Siemens", "1929749"));
    securities.add(createSecurity("ISHARES CORE DAX UCITS ETF (DE)", "18355296"));

    securities.parallelStream().forEach(security -> {

      List<Historyquote> historyquote = new ArrayList<>();
      try {
        historyquote = onVistaConnector.getEodSecurityHistory(security, fromDate, toDate);
      } catch (final Exception e) {
        e.printStackTrace();
      }
      assertThat(historyquote.size()).isGreaterThan(3900);
    });
  }

  private Security createSecurity(final String name, final String url) {
    final Security security = new Security();
    security.setName(name);
    security.setUrlIntraExtend(url);
    security.setUrlHistoryExtend(url);
    return security;
  }

}
