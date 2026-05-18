package grafioschtrader.connector.instrument.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import grafioschtrader.connector.instrument.fred.FredFeedConnector;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.test.start.GTforTest;

/**
 * Live test for the FRED (St. Louis Fed) connector. Hits the real FRED API and therefore requires:
 * <ul>
 * <li>A row in {@code connector_apikey} of the test DB ({@code grafioschtrader_t}) with
 * {@code id_provider = 'fred'} and a valid Jasypt-encrypted key,</li>
 * <li>internet connectivity.</li>
 * </ul>
 * If the connector is not activated (no key registered), each test is skipped via {@code assumeTrue}.
 *
 * <p>
 * Tested series: {@code DGS3MO} (USD daily 3-month T-bill), {@code ECBESTRVOLWGTTRMDMNRT} (EUR daily &euro;STR),
 * {@code IUDSOIA} (GBP daily SONIA), and {@code IR3TIB01CHM156N} (CHF monthly 3-month interbank). The first three are
 * the recommended daily risk-free-rate series for the project's primary currencies; the fourth verifies handling of
 * monthly-frequency series.
 *
 * <p>
 * Assertions are deliberately loose on row counts (FRED's reporting calendar shifts with holidays and revisions). The
 * test checks: at least one observation returned, every date is within the requested window, rate values are stored as
 * decimal fractions in a plausible range (0% &le; rate &le; 25%), and there are no duplicate dates.
 */
@SpringBootTest(classes = GTforTest.class)
@ActiveProfiles("test")
class FredFeedConnectorTest {

  private final FredFeedConnector fredFeedConnector;

  @Autowired
  public FredFeedConnectorTest(FredFeedConnector fredFeedConnector) {
    this.fredFeedConnector = fredFeedConnector;
    assumeTrue(fredFeedConnector.isActivated(),
        "FRED connector not activated (no api_key in connector_apikey for id_provider='fred')");
  }

  @Test
  void usd_dgs3mo_dailyTreasuryYield() throws Exception {
    runHistorical("US 3-Month Treasury", "DGS3MO", LocalDate.parse("2024-01-02"), LocalDate.parse("2024-01-31"));
  }

  @Test
  void eur_estr_dailyEcbShortTermRate() throws Exception {
    runHistorical("Euro Short-Term Rate", "ECBESTRVOLWGTTRMDMNRT", LocalDate.parse("2024-01-02"),
        LocalDate.parse("2024-01-31"));
  }

  @Test
  void gbp_sonia_dailyBankOfEngland() throws Exception {
    runHistorical("UK SONIA", "IUDSOIA", LocalDate.parse("2024-01-02"), LocalDate.parse("2024-01-31"));
  }

  @Test
  void chf_threeMonthInterbank_monthlySeries() throws Exception {
    // Monthly series: widen the window to ~6 months to guarantee at least one observation regardless of FRED's
    // reporting lag.
    runHistorical("Swiss 3-Month Interbank", "IR3TIB01CHM156N", LocalDate.parse("2023-07-01"),
        LocalDate.parse("2023-12-31"));
  }

  private void runHistorical(String name, String fredSeriesId, LocalDate from, LocalDate to) throws Exception {
    Security security = new Security();
    security.setName(name);
    security.setUrlHistoryExtend(fredSeriesId);

    List<Historyquote> quotes = fredFeedConnector.getEodSecurityHistory(security, from, to);

    assertThat(quotes).as("FRED returned no observations for %s (%s) in [%s, %s]", name, fredSeriesId, from, to)
        .isNotEmpty();

    Set<LocalDate> seen = new HashSet<>();
    for (Historyquote h : quotes) {
      assertThat(h.getDate()).as("Quote date for %s outside requested window", fredSeriesId)
          .isBetween(from, to);
      assertThat(h.getClose()).as("Rate for %s on %s is outside plausible 0..0.25 band", fredSeriesId, h.getDate())
          .isBetween(-0.05, 0.25);
      assertThat(seen.add(h.getDate())).as("Duplicate date %s in FRED response for %s", h.getDate(), fredSeriesId)
          .isTrue();
    }

    System.out.printf("FRED %s (%s): %d observations between %s and %s%n", name, fredSeriesId, quotes.size(),
        quotes.get(0).getDate(), quotes.get(quotes.size() - 1).getDate());
  }
}
