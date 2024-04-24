package grafioschtrader.connector.instrument.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.text.ParseException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import grafioschtrader.GlobalConstants;
import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.connector.instrument.onvista.OnvistaFeedConnector;
import grafioschtrader.connector.instrument.test.ConnectorTestHelper.SecurityHistoricalDate;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.types.SpecialInvestmentInstruments;

class OnvistaFeedConnectorTest extends BaseFeedConnectorCheck {

  private OnvistaFeedConnector onvistafeedConnector = new OnvistaFeedConnector();

  @Test
  void getEodSecurityHistoryTest() {
    getEodSecurityHistory(false);
  }

  @Override
  protected List<SecurityHistoricalDate> getHistoricalSecurities() {
    List<SecurityHistoricalDate> hisoricalDate = new ArrayList<>();
    try {
      hisoricalDate.add(new SecurityHistoricalDate("Siemens", SpecialInvestmentInstruments.DIRECT_INVESTMENT,
          "STOCK/82902/eod_history?idNotation=1929749", 6084, "2000-01-03", "2023-12-08"));
      hisoricalDate.add(new SecurityHistoricalDate("iShares Core DAX", SpecialInvestmentInstruments.ETF,
          "FUND/3567527/eod_history?idNotation=28520648", 3734, "2009-04-06", "2023-12-08"));
      hisoricalDate.add(new SecurityHistoricalDate("BGF World Energy Fund I2 USD", SpecialInvestmentInstruments.MUTUAL_FUND,
          "FUND/20982583/eod_history?idNotation=26071169", 3394, "2008-11-18", "2023-12-08"));
      hisoricalDate.add(new SecurityHistoricalDate("Amazon", SpecialInvestmentInstruments.MUTUAL_FUND,
          "STOCK/90929/eod_history?idNotation=9386187", 4889, "2004-03-17", "2023-12-08"));
      hisoricalDate.add(new SecurityHistoricalDate("Autoneum Holding AG SF-Anl. 2017(25)", SpecialInvestmentInstruments.DIRECT_INVESTMENT,
          "BOND/130304815/eod_history?idNotation=202439144", 1265, "2017-12-06", "2023-12-08"));

    } catch (ParseException pe) {
      pe.printStackTrace();
    }
    return hisoricalDate;
  }

  @Override
  protected IFeedConnector getIFeedConnector() {
    return onvistafeedConnector;
  }

  @Test
  void getEodCurrencyHistoryTest() {
    final DateTimeFormatter germanFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(Locale.GERMAN);

    final List<Currencypair> currencies = new ArrayList<>();
    currencies.add(ConnectorTestHelper.createHistoricalCurrencyPair(GlobalConstants.MC_GBP, GlobalConstants.MC_USD, "CURRENCY/GBPUSD/eod_history?idNotation=1305587"));
    final LocalDate from = LocalDate.parse("03.01.2000", germanFormatter);
    final Date fromDate = Date.from(from.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
    final LocalDate to = LocalDate.parse("08.12.2023", germanFormatter);
    Date toDate = Date.from(to.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
    currencies.parallelStream().forEach(currencyPair -> {
      List<Historyquote> historyquote = new ArrayList<>();
      try {
        historyquote = onvistafeedConnector.getEodCurrencyHistory(currencyPair, fromDate, toDate);
      } catch (Exception e) {
        e.printStackTrace();
      }
      assertThat(historyquote.size()).isEqualByComparingTo(7514);
    });
  }

}
