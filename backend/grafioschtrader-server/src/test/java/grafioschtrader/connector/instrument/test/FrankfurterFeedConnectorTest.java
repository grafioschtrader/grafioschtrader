package grafioschtrader.connector.instrument.test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import grafiosch.exceptions.GeneralNotTranslatedWithArgumentsException;
import grafioschtrader.GlobalConstants;
import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.connector.instrument.IFeedConnector.FeedSupport;
import grafioschtrader.connector.instrument.frankfurter.FrankfurterFeedConnector;
import grafioschtrader.connector.instrument.test.ConnectorTestHelper.CurrencyPairHistoricalDate;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Historyquote;

/**
 * Checks the connector against the public Frankfurter instance, therefore this test needs internet access. No Spring
 * context is required, because the connector reads neither the database nor any other bean.
 */
class FrankfurterFeedConnectorTest extends BaseFeedConnectorCheck {

  private static final String PUBLIC_API_ROOT_URL = "https://api.frankfurter.dev";

  private final FrankfurterFeedConnector frankfurterFeedConnector = new FrankfurterFeedConnector(PUBLIC_API_ROOT_URL);

  @Test
  void getEodCurrencyHistoryTest() {
    getEodCurrencyHistory();
  }

  /**
   * Frankfurter serves base and quote as free parameters, so a well conditioned pair is requested directly in both
   * directions. The rates are published with a limited number of digits, which is why the reciprocal is compared
   * relatively rather than absolutely.
   */
  @Test
  void bothDirectionsOfAWellConditionedPairAgreeTest() throws Exception {
    LocalDate from = LocalDate.of(2024, 1, 2);
    LocalDate to = LocalDate.of(2024, 1, 31);
    Currencypair direct = new Currencypair(GlobalConstants.MC_USD, GlobalConstants.MC_CHF);
    Currencypair inverse = new Currencypair(GlobalConstants.MC_CHF, GlobalConstants.MC_USD);

    List<Historyquote> directQuotes = frankfurterFeedConnector.getEodCurrencyHistory(direct, from, to);
    List<Historyquote> inverseQuotes = frankfurterFeedConnector.getEodCurrencyHistory(inverse, from, to);

    Assertions.assertThat(directQuotes).isNotEmpty();
    Assertions.assertThat(inverseQuotes).hasSameSizeAs(directQuotes);
    for (int i = 0; i < directQuotes.size(); i++) {
      Assertions.assertThat(inverseQuotes.get(i).getDate()).isEqualTo(directQuotes.get(i).getDate());
      Assertions.assertThat(inverseQuotes.get(i).getClose()).isCloseTo(1.0 / directQuotes.get(i).getClose(),
          Assertions.withinPercentage(0.05));
    }
  }

  /**
   * The provider rounds by magnitude and gives a rate below 1 five decimal places, so it answers JPY/GBP with
   * {@code 0.005} although the true rate is {@code 0.0050012}. The connector has to notice this and derive the series
   * from GBP/JPY instead, otherwise every JPY based pair would be stored with one or two significant digits.
   */
  @Test
  void smallRateIsTakenFromTheOppositeDirectionTest() throws Exception {
    LocalDate day = LocalDate.of(2024, 6, 3);
    Currencypair currencypair = new Currencypair(GlobalConstants.MC_JPY, GlobalConstants.MC_GBP);

    List<Historyquote> historyquotes = frankfurterFeedConnector.getEodCurrencyHistory(currencypair, day, day);

    Assertions.assertThat(historyquotes).hasSize(1);
    double close = historyquotes.getFirst().getClose();
    // A value coming straight from the direct endpoint would be an exact multiple of 0.00001.
    Assertions.assertThat(Math.abs(close * 100_000 - Math.rint(close * 100_000)))
        .as("Rate must carry more precision than the five decimal places of the direct endpoint").isGreaterThan(1e-6);
    Assertions.assertThat(close).isCloseTo(0.005001, Assertions.withinPercentage(1.0));
  }

  /**
   * The provider delivers a row for every calendar day, but its weekend values come from carrying the Friday rate
   * forward rather than from an observation. They must not reach the history, otherwise smoothed values would sit next
   * to real ones and the series would no longer match the other currency connectors.
   */
  @Test
  void weekendRowsAreDroppedTest() throws Exception {
    Currencypair currencypair = new Currencypair(GlobalConstants.MC_EUR, GlobalConstants.MC_CHF);

    List<Historyquote> historyquotes = frankfurterFeedConnector.getEodCurrencyHistory(currencypair,
        LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 31));

    Assertions.assertThat(historyquotes).isNotEmpty();
    Assertions.assertThat(historyquotes).as("No Saturday or Sunday may be stored")
        .noneMatch(historyquote -> historyquote.getDate().getDayOfWeek() == DayOfWeek.SATURDAY
            || historyquote.getDate().getDayOfWeek() == DayOfWeek.SUNDAY);
  }

  /**
   * Guards against rounding the rate to the precision of the quote currency, as the cryptocurrency connectors do. That
   * precision defaults to two decimal places and would reduce an exchange rate such as USD/CHF 0.81136 to 0.81.
   */
  @Test
  void rateIsNotRoundedToCurrencyPrecisionTest() throws Exception {
    Currencypair currencypair = new Currencypair(GlobalConstants.MC_USD, GlobalConstants.MC_CHF);

    List<Historyquote> historyquotes = frankfurterFeedConnector.getEodCurrencyHistory(currencypair,
        LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 31));

    Assertions.assertThat(historyquotes).isNotEmpty();
    Assertions.assertThat(historyquotes).as("Rates must keep more than two decimal places")
        .anyMatch(historyquote -> Math.abs(historyquote.getClose() * 100 - Math.rint(historyquote.getClose() * 100))
            > 1e-9);
  }

  /**
   * The connector identifies the pair by its currencies, so a URL extension is meaningless and the framework has to
   * remove a leftover value on the next save.
   */
  @Test
  void urlExtensionIsWipedTest() {
    Currencypair currencypair = new Currencypair(GlobalConstants.MC_EUR, GlobalConstants.MC_CHF);
    currencypair.setUrlHistoryExtend("EURCHF");

    frankfurterFeedConnector.checkAndClearSecuritycurrencyUrlExtend(currencypair, FeedSupport.FS_HISTORY);

    Assertions.assertThat(currencypair.getUrlHistoryExtend()).isNull();
  }

  @Test
  void rejectsCurrencyNotOfferedByTheInstanceTest() {
    Currencypair currencypair = new Currencypair(GlobalConstants.MC_EUR, "XYZ");

    Assertions
        .assertThatThrownBy(
            () -> frankfurterFeedConnector.checkAndClearSecuritycurrencyUrlExtend(currencypair, FeedSupport.FS_HISTORY))
        .isInstanceOf(GeneralNotTranslatedWithArgumentsException.class);
  }

  @Override
  protected List<CurrencyPairHistoricalDate> getHistoricalCurrencies() {
    String oldestDate = "2000-01-04";
    String youngFromDate = "2025-01-03";
    String toDate = "2025-01-13";

    final List<CurrencyPairHistoricalDate> currencies = new ArrayList<>();
    try {
      // Expected rows count working days only, because the connector discards the carried-forward weekend rows.
      currencies.add(new CurrencyPairHistoricalDate(GlobalConstants.MC_EUR, GlobalConstants.MC_CHF, 7, youngFromDate,
          toDate));
      currencies.add(new CurrencyPairHistoricalDate(GlobalConstants.MC_USD, GlobalConstants.MC_JPY, 7, youngFromDate,
          toDate));
      currencies.add(new CurrencyPairHistoricalDate("ZAR", "NOK", 6530, oldestDate, toDate));
      currencies.add(new CurrencyPairHistoricalDate(GlobalConstants.MC_EUR, GlobalConstants.MC_CHF, 6524, oldestDate,
          youngFromDate));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
    return currencies;
  }

  @Override
  protected IFeedConnector getIFeedConnector() {
    return frankfurterFeedConnector;
  }
}
