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

import grafioschtrader.connector.instrument.fxubc.FxUbcFeedConnector;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Historyquote;

/**
 * This test can often fail, because the provider may be to busy. 
 * 
 * @author Hugo Graf
 *
 */
class FxUbcFeedConnectorTest {

  private FxUbcFeedConnector fxUbcFeedConnector = new FxUbcFeedConnector();
  
  @Test
  void getEodCurrencyHistoryLongPeriodTest() {

    final List<Currencypair> currencies = new ArrayList<>();
    currencies.add(ConnectorTestHelper.createCurrencyPair("USD", "CHF"));
    currencies.add(ConnectorTestHelper.createCurrencyPair("EUR", "CHF"));
    currencies.add(ConnectorTestHelper.createCurrencyPair("USD", "JPY"));
    currencies.add(ConnectorTestHelper.createCurrencyPair("ZAR", "USD"));
    currencies.add(ConnectorTestHelper.createCurrencyPair("ZAR", "NOK"));
  
    final DateTimeFormatter germanFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(Locale.GERMAN);

    final LocalDate from = LocalDate.parse("01.01.2000", germanFormatter);
    final LocalDate to = LocalDate.parse("25.10.2019", germanFormatter);

    final Date fromDate = Date.from(from.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
    final Date toDate = Date.from(to.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());

    currencies.parallelStream().forEach(currencyPair -> {
      List<Historyquote> historyquote = new ArrayList<>();
      try {
        historyquote = fxUbcFeedConnector.getEodCurrencyHistory(currencyPair, fromDate, toDate);
      } catch (Exception e) {
        e.printStackTrace();
      }
      assertThat(historyquote.size()).isGreaterThan(4000);
    });

  }

  @Test
  void getEodCurrencyHistoryShortPeriodTest() {

    final List<Currencypair> currencies = new ArrayList<>();
    currencies.add(ConnectorTestHelper.createCurrencyPair("USD", "CHF"));
    currencies.add(ConnectorTestHelper.createCurrencyPair("EUR", "CHF"));
    currencies.add(ConnectorTestHelper.createCurrencyPair("USD", "JPY"));

    final DateTimeFormatter germanFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(Locale.GERMAN);

    final LocalDate from = LocalDate.parse("21.10.2019", germanFormatter);
    final LocalDate to = LocalDate.parse("25.10.2019", germanFormatter);

    final Date fromDate = Date.from(from.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
    final Date toDate = Date.from(to.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());

    currencies.parallelStream().forEach(currencyPair -> {
      List<Historyquote> historyquote = new ArrayList<>();
      try {
        historyquote = fxUbcFeedConnector.getEodCurrencyHistory(currencyPair, fromDate, toDate);
      } catch (Exception e) {
        e.printStackTrace();
      }
      assertThat(historyquote.size()).isEqualTo(5);
    });

  }

}
