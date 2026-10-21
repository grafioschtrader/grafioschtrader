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
import grafioschtrader.types.FundamentalMetric;
import grafioschtrader.types.FundamentalSource;
import grafioschtrader.types.FundamentalState;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Recorded Finnhub answers of two US listings pass through the mapping and, together with the Eulerpool answers of the
 * same day, through the selection. Finnhub's {@code metric} object names no period; the fiscal year earnings take the
 * period of the first annual {@code series} entry only when that entry carries the same value.
 */
class FinnhubFundamentalMappingTest {

  private static final LocalDateTime FETCHED = LocalDateTime.of(2026, 10, 21, 6, 46);
  private static final LocalDate TODAY = FETCHED.toLocalDate();

  @ParameterizedTest
  @CsvSource({ "AAPL, 8.7233, 7.465, 2025-09-27, 7.3599, 31.725", "MRK, 1.2521, 7.2812, 2025-12-31, 16.9863, 26.951" })
  @DisplayName("Apple and Merck: four per-share values in USD, the fiscal year dated by the annual series")
  void recordedListings(String symbol, double epsTtm, double epsFy, LocalDate fiscalYearEnd, double bvps,
      double sps) throws IOException {
    FundamentalAnswer answer = mapped(sample(symbol));

    assertRow(answer, FundamentalMetric.EPS_TTM, epsTtm, null);
    assertRow(answer, FundamentalMetric.EPS_FY, epsFy, fiscalYearEnd);
    assertRow(answer, FundamentalMetric.BVPS, bvps, null);
    assertRow(answer, FundamentalMetric.SPS_TTM, sps, null);
    assertThat(answer.rows()).extracting(SecurityFundamental::getCurrency).containsOnly("USD");
    assertThat(answer.rows()).extracting(SecurityFundamental::getMetric).doesNotContain(FundamentalMetric.SHARES,
        FundamentalMetric.NET_DEBT, FundamentalMetric.PE_CHECK, FundamentalMetric.MCAP_CHECK);
  }

  @ParameterizedTest
  @CsvSource({ "AAPL, US0378331005", "MRK, US58933Y1055" })
  @DisplayName("Finnhub's fiscal year EPS agrees with Eulerpool's of the same year and is confirmed; the undated "
      + "trailing value alone is not chosen")
  void fiscalYearConfirmedByEulerpool(String symbol, String isin) throws IOException {
    List<SecurityFundamental> rows = new ArrayList<>(mapped(sample(symbol)).rows());
    rows.addAll(FundamentalAnswerMapper.eulerpool(EulerpoolFundamentalMappingTest.sample(isin), 1, FETCHED).rows());

    Map<FundamentalMetric, SecurityFundamental> selected = FundamentalSelector.select(rows, TODAY);

    assertThat(selected.get(FundamentalMetric.EPS_FY).getSource()).isEqualTo(FundamentalSource.FINNHUB);
    assertThat(selected.get(FundamentalMetric.EPS_FY).getState()).isEqualTo(FundamentalState.CONFIRMED);
    assertThat(selected).doesNotContainKey(FundamentalMetric.EPS_TTM);
  }

  @Test
  @DisplayName("An annual series entry with another value than epsAnnual does not date the fiscal year")
  void seriesWithOtherValueGivesNoPeriod() throws IOException {
    JsonNode root = sample("AAPL");
    ((ObjectNode) root.at("/series/annual/eps/0")).put("v", 7.0);
    FundamentalAnswer answer = mapped(root);
    assertRow(answer, FundamentalMetric.EPS_FY, 7.465, null);
  }

  private static void assertRow(FundamentalAnswer answer, FundamentalMetric metric, double expected,
      LocalDate periodEnd) {
    SecurityFundamental row = answer.rows().stream().filter(r -> r.getMetric() == metric).findFirst().orElseThrow();
    assertThat(row.getValue()).isCloseTo(expected, within(0.000001));
    assertThat(row.getPeriodEnd()).isEqualTo(periodEnd);
  }

  private static FundamentalAnswer mapped(JsonNode root) {
    return FundamentalAnswerMapper.finnhub(root, 1, "USD", FETCHED);
  }

  private static JsonNode sample(String symbol) throws IOException {
    try (var stream = FinnhubFundamentalMappingTest.class
        .getResourceAsStream("/fundamental/samples/finnhub-" + symbol + "-2026-10-21.json")) {
      return FundamentalFeedSupport.MAPPER.readTree(stream);
    }
  }
}
