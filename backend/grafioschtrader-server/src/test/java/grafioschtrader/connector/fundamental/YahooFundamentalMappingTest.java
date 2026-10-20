package grafioschtrader.connector.fundamental;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
import tools.jackson.databind.node.ObjectNode;

/**
 * Recorded Yahoo answers pass through mapping, load-time checks and selection. The foreign listing, ADR and pence cases
 * distinguish quote-currency book value from reporting-currency totals and listing-equivalent share counts.
 */
class YahooFundamentalMappingTest {

  private static final LocalDateTime FETCHED = LocalDateTime.of(2026, 10, 20, 16, 0);
  private static final LocalDate TODAY = FETCHED.toLocalDate();

  @ParameterizedTest
  @CsvSource({ "NESN.SW, CHF, CHF, 83.34, 11.36, 2572220000, 34.51298880500113, 56711001600",
      "NOV.DE, EUR, DKK, 34.905, 6.6848507, 4413110124, 74.64825975142604, 95144992768",
      "BABA, USD, CNY, 108.5887, 67.17209, 2487744939, 420.04748128722855, -119166992384",
      "AZN.L, GBP, USD, 126.04, 24.511501, 1550905742, 39.56784735664484, 27381999104" })
  @DisplayName("Four recorded listings expose all four Yahoo fields in the right currency and share unit")
  void recordedListings(String symbol, String quoteCurrency, String financialCurrency, double price, double bookValue,
      double shares, double revenuePerShare, double netDebt) throws IOException {
    FundamentalAnswer answer = mapped(sample(symbol));
    Map<FundamentalMetric, SecurityFundamental> selected = checked(answer, price, quoteCurrency);

    assertValue(selected, FundamentalMetric.BVPS, bookValue, quoteCurrency);
    assertValue(selected, FundamentalMetric.SPS_TTM, revenuePerShare, financialCurrency);
    assertValue(selected, FundamentalMetric.SHARES, shares, null);
    assertValue(selected, FundamentalMetric.NET_DEBT, netDebt, financialCurrency);
    assertThat(selected.get(FundamentalMetric.SHARES).getPeriodEnd()).isNull();
    assertThat(selected.get(FundamentalMetric.SHARES).getState()).isEqualTo(FundamentalState.CHECKED);
    assertThat(selected.get(FundamentalMetric.SPS_TTM).getSourceCheck()).isEqualTo(FundamentalSourceCheck.PASSED);
    assertThat(selected.get(FundamentalMetric.BVPS).getPeriodEnd()).isEqualTo(LocalDate.of(2026, 6, 30));
    assertThat(selected.get(FundamentalMetric.NET_DEBT).getState()).isEqualTo(FundamentalState.SINGLE);
  }

  @Test
  @DisplayName("Novo Nordisk uses all listing-equivalent shares, not only the listed B class")
  void allClassesCountTowardsCompanyRevenue() throws IOException {
    JsonNode root = sample("NOV.DE");
    var selected = checked(mapped(root), 34.905, "EUR");
    double marketCap = selected.get(FundamentalMetric.SHARES).getValue() * 34.905;
    assertThat(marketCap).isCloseTo(154_039_615_488.0, within(10_000.0));

    // Without the implied total, the B-class count cannot reproduce the company market capitalisation.
    object(root, "/defaultKeyStatistics").remove("impliedSharesOutstanding");
    selected = checked(mapped(root), 34.905, "EUR");
    assertThat(selected).doesNotContainKeys(FundamentalMetric.SHARES, FundamentalMetric.SPS_TTM);
    assertThat(selected).containsKeys(FundamentalMetric.BVPS, FundamentalMetric.NET_DEBT);
  }

  @Test
  @DisplayName("Alibaba ordinary shares in place of ADRs reject both the count and revenue per ADR")
  void wrongAdrBasisRejected() throws IOException {
    JsonNode root = sample("BABA");
    object(root, "/defaultKeyStatistics/impliedSharesOutstanding").put("raw", 19_901_959_512.0);
    FundamentalAnswer answer = mapped(root);
    var selected = checked(answer, 108.5887, "USD");
    assertThat(selected).doesNotContainKeys(FundamentalMetric.SHARES, FundamentalMetric.SPS_TTM);
    assertThat(row(answer, FundamentalMetric.SHARES).getSourceCheck()).isEqualTo(FundamentalSourceCheck.FAILED);
    assertThat(row(answer, FundamentalMetric.SPS_TTM).getSourceCheck()).isEqualTo(FundamentalSourceCheck.FAILED);
  }

  @Test
  @DisplayName("An absent implied count falls back to sharesOutstanding; missing check input never proves its basis")
  void fallbackAndMissingEvidence() throws IOException {
    JsonNode root = sample("BABA");
    object(root, "/defaultKeyStatistics").remove("impliedSharesOutstanding");
    assertThat(checked(mapped(root), 108.5887, "USD")).containsKeys(FundamentalMetric.SHARES,
        FundamentalMetric.SPS_TTM);

    object(root, "/summaryDetail").remove("marketCap");
    FundamentalAnswer answer = mapped(root);
    assertThat(checked(answer, 108.5887, "USD")).doesNotContainKeys(FundamentalMetric.SHARES,
        FundamentalMetric.SPS_TTM);
    assertThat(row(answer, FundamentalMetric.SPS_TTM).getSourceCheck()).isEqualTo(FundamentalSourceCheck.NOT_CHECKED);
  }

  @Test
  @DisplayName("A checked undated share count expires after 21 days, even if its last check passed")
  void currentSharesExpire() throws IOException {
    FundamentalAnswer answer = mapped(sample("BABA"));
    checked(answer, 108.5887, "USD");
    assertThat(FundamentalSelector.select(answer.rows(), TODAY.plusDays(21))).containsKey(FundamentalMetric.SHARES);
    assertThat(FundamentalSelector.select(answer.rows(), TODAY.plusDays(22)))
        .doesNotContainKey(FundamentalMetric.SHARES);
  }

  @Test
  @DisplayName("A missing reporting currency never falls back to the quote currency")
  void unknownReportingCurrency() throws IOException {
    JsonNode root = sample("BABA");
    object(root, "/financialData").remove("financialCurrency");
    FundamentalAnswer answer = mapped(root);
    assertThat(row(answer, FundamentalMetric.NET_DEBT).getCurrency()).isNull();
    assertThat(row(answer, FundamentalMetric.SPS_TTM).getCurrency()).isNull();
    assertThat(row(answer, FundamentalMetric.BVPS).getCurrency()).isEqualTo("USD");
  }

  @Test
  @DisplayName("Absent or invalid raw fields write no invented zero or non-finite value")
  void incompleteAnswer() throws IOException {
    JsonNode root = sample("NESN.SW");
    object(root, "/defaultKeyStatistics/bookValue").put("raw", "NaN");
    object(root, "/defaultKeyStatistics/impliedSharesOutstanding").put("raw", 0);
    object(root, "/defaultKeyStatistics/sharesOutstanding").put("raw", -1);
    object(root, "/financialData").remove("totalCash");
    FundamentalAnswer answer = mapped(root);
    assertThat(answer.rows()).extracting(SecurityFundamental::getMetric).doesNotContain(FundamentalMetric.BVPS,
        FundamentalMetric.SHARES, FundamentalMetric.SPS_TTM, FundamentalMetric.NET_DEBT);
  }

  private static void assertValue(Map<FundamentalMetric, SecurityFundamental> selected, FundamentalMetric metric,
      double expected, String currency) {
    assertThat(selected).containsKey(metric);
    assertThat(selected.get(metric).getValue()).isCloseTo(expected, within(0.000001));
    assertThat(selected.get(metric).getCurrency()).isEqualTo(currency);
  }

  private static FundamentalAnswer mapped(JsonNode root) {
    return FundamentalAnswerMapper.yahoo(root, 1, FETCHED);
  }

  private static Map<FundamentalMetric, SecurityFundamental> checked(FundamentalAnswer answer, double price,
      String currency) {
    FundamentalSourceChecks.apply(answer.rows(), answer.rows(), Map.of(FundamentalSource.YAHOO, answer.evidence()),
        price, currency, (_, _) -> null);
    return FundamentalSelector.select(answer.rows(), TODAY);
  }

  private static SecurityFundamental row(FundamentalAnswer answer, FundamentalMetric metric) {
    return answer.rows().stream().filter(r -> r.getMetric() == metric).findFirst().orElseThrow();
  }

  private static JsonNode sample(String symbol) throws IOException {
    try (var stream = YahooFundamentalMappingTest.class
        .getResourceAsStream("/fundamental/samples/yahoo-" + symbol + "-2026-10-20.json")) {
      return FundamentalFeedSupport.MAPPER.readTree(stream);
    }
  }

  private static ObjectNode object(JsonNode root, String pointer) {
    return (ObjectNode) root.at("/quoteSummary/result/0" + pointer);
  }
}
