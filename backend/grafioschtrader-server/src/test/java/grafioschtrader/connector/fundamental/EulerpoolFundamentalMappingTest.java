package grafioschtrader.connector.fundamental;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import grafioschtrader.entities.SecurityFundamental;
import grafioschtrader.fundamental.FundamentalSelector;
import grafioschtrader.fundamental.FundamentalSourceChecks;
import grafioschtrader.types.FundamentalMetric;
import grafioschtrader.types.FundamentalSource;
import grafioschtrader.types.FundamentalSourceCheck;
import grafioschtrader.types.FundamentalState;
import tools.jackson.databind.JsonNode;

/**
 * Recorded Eulerpool answers pass through the mapping and, together with the Yahoo answer of the same listing, through
 * the load-time checks and the selection. Eulerpool states the share count in millions and every other amount in full
 * units, in the reporting currency of the company, for the last fiscal year.
 */
class EulerpoolFundamentalMappingTest {

  private static final LocalDateTime FETCHED = LocalDateTime.of(2026, 10, 21, 6, 46);
  private static final LocalDate TODAY = FETCHED.toLocalDate();

  @ParameterizedTest
  @CsvSource({ "CH0038863350, CHF, 2025-12-31, 3.50138, 12.62, 34.68819771768791, 2599000000, 53273000000",
      "DK0062498333, DKK, 2025-12-31, 23.02198, 43.63, 69.4619499258327, 4447851000, 104494000000",
      "US01609W1027, CNY, 2026-03-31, 42.88636, 54.73, 423.79217553301595, 19318000000, 86913880000",
      "DE0005557508, EUR, 2025-12-31, 1.9861423, 12.76, 24.613571414102704, 4871000000, 125546000000" })
  @DisplayName("Per-share values, the share count multiplied out of millions and the net debt in full units, all in "
      + "the reporting currency of the newest fiscal year")
  void recordedAnswers(String isin, String currency, LocalDate periodEnd, double eps, double bvps, double sps,
      double shares, double netDebt) throws IOException {
    FundamentalAnswer answer = mapped(sample(isin));

    assertRow(answer, FundamentalMetric.EPS_FY, eps, currency);
    assertRow(answer, FundamentalMetric.BVPS, bvps, currency);
    assertRow(answer, FundamentalMetric.SPS_FY, sps, currency);
    assertRow(answer, FundamentalMetric.SHARES, shares, null);
    assertRow(answer, FundamentalMetric.NET_DEBT, netDebt, currency);
    assertThat(answer.rows()).extracting(SecurityFundamental::getPeriodEnd).containsOnly(periodEnd);
    // The net income of Eulerpool's history includes non-controlling interests (Deutsche Telekom: EPS times shares
    // gives only 65 % of it), so it is not passed on as evidence of the unit check.
    assertThat(answer.evidence()).isEmpty();
  }

  @Test
  @DisplayName("Nestle: Eulerpool's share count reproduces Yahoo's market capitalisation and confirms Yahoo's count")
  void sharesConfirmYahoo() throws IOException {
    List<SecurityFundamental> rows = checked("CH0038863350", "NESN.SW", 83.34, "CHF");

    assertThat(row(rows, FundamentalSource.EULERPOOL, FundamentalMetric.SHARES).getSourceCheck())
        .isEqualTo(FundamentalSourceCheck.PASSED);
    Map<FundamentalMetric, SecurityFundamental> selected = FundamentalSelector.select(rows, TODAY);
    assertThat(selected.get(FundamentalMetric.SHARES).getSource()).isEqualTo(FundamentalSource.YAHOO);
    assertThat(selected.get(FundamentalMetric.SHARES).getState()).isEqualTo(FundamentalState.CONFIRMED);
  }

  @Test
  @DisplayName("Alibaba ADR: Eulerpool counts ordinary shares, fails the market capitalisation and Yahoo's ADR count "
      + "is chosen")
  void adrShareCountFails() throws IOException {
    List<SecurityFundamental> rows = checked("US01609W1027", "BABA", 108.5887, "USD");

    assertThat(row(rows, FundamentalSource.EULERPOOL, FundamentalMetric.SHARES).getSourceCheck())
        .isEqualTo(FundamentalSourceCheck.FAILED);
    SecurityFundamental shares = FundamentalSelector.select(rows, TODAY).get(FundamentalMetric.SHARES);
    assertThat(shares.getSource()).isEqualTo(FundamentalSource.YAHOO);
    assertThat(shares.getValue()).isCloseTo(2_487_744_939.0, within(0.5));
  }

  /** Maps the Eulerpool and the Yahoo answer of one listing and runs the load-time checks over both. */
  private static List<SecurityFundamental> checked(String isin, String yahooSymbol, double price, String currency)
      throws IOException {
    FundamentalAnswer eulerpool = mapped(sample(isin));
    FundamentalAnswer yahoo = FundamentalAnswerMapper.yahoo(yahooSample(yahooSymbol), 1, FETCHED);
    List<SecurityFundamental> rows = new ArrayList<>(eulerpool.rows());
    rows.addAll(yahoo.rows());
    FundamentalSourceChecks.apply(rows, rows,
        Map.of(FundamentalSource.EULERPOOL, eulerpool.evidence(), FundamentalSource.YAHOO, yahoo.evidence()), price,
        currency, (_, _) -> null);
    return rows;
  }

  private static void assertRow(FundamentalAnswer answer, FundamentalMetric metric, double expected, String currency) {
    SecurityFundamental row = answer.rows().stream().filter(r -> r.getMetric() == metric).findFirst().orElseThrow();
    assertThat(row.getValue()).isCloseTo(expected, within(0.000001));
    assertThat(row.getCurrency()).isEqualTo(currency);
  }

  private static SecurityFundamental row(List<SecurityFundamental> rows, FundamentalSource source,
      FundamentalMetric metric) {
    return rows.stream().filter(r -> r.getSource() == source && r.getMetric() == metric).findFirst().orElseThrow();
  }

  private static FundamentalAnswer mapped(JsonNode root) {
    return FundamentalAnswerMapper.eulerpool(root, 1, FETCHED);
  }

  static JsonNode sample(String isin) throws IOException {
    return read("/fundamental/samples/eulerpool-" + isin + "-2026-10-21.json");
  }

  private static JsonNode yahooSample(String symbol) throws IOException {
    return read("/fundamental/samples/yahoo-" + symbol + "-2026-10-20.json");
  }

  private static JsonNode read(String resource) throws IOException {
    try (var stream = EulerpoolFundamentalMappingTest.class.getResourceAsStream(resource)) {
      return FundamentalFeedSupport.MAPPER.readTree(stream);
    }
  }
}
