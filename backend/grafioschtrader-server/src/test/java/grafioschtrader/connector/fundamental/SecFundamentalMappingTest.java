package grafioschtrader.connector.fundamental;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import grafioschtrader.types.FundamentalMetric;
import tools.jackson.databind.JsonNode;

/** Tests the EDGAR stock concept and issuer mapping without depending on a live SEC response. */
class SecFundamentalMappingTest {

  private static final LocalDateTime FETCHED = LocalDateTime.of(2026, 11, 13, 6, 0);

  @ParameterizedTest
  @CsvSource({ "ADBE,389200000,2026-09-18", "BKNG,743701257,2026-10-26" })
  @DisplayName("Original SEC answers reproduce the stock and measurement date of the companies' latest disclosures")
  void recordedStocks(String ticker, double expected, LocalDate date) throws IOException {
    try (var stream = getClass().getResourceAsStream("/fundamental/samples/sec-" + ticker + "-2026-11-13.json")) {
      var answer = FundamentalAnswerMapper.secShares(FundamentalFeedSupport.MAPPER.readTree(stream), 42, FETCHED);
      assertThat(answer.rows()).hasSize(1);
      assertThat(answer.rows().getFirst().getValue()).isEqualTo(expected);
      assertThat(answer.rows().getFirst().getPeriodEnd()).isEqualTo(date);
    }
  }

  @Test
  @DisplayName("Adobe: the measurement date wins over filing order, and a later amendment corrects that stock")
  void newestMeasurementAndAmendment() {
    JsonNode root = concept("""
        {"end":"2026-09-18","val":389200000,"filed":"2026-09-24","form":"10-Q"},
        {"end":"2026-06-11","val":397500000,"filed":"2026-10-01","form":"10-Q/A"},
        {"end":"2026-09-18","val":389100000,"filed":"2026-10-02","form":"10-Q/A"},
        {"end":"2026-12-01","val":999,"filed":"2026-12-02","form":"10-Q"}
        """);
    var stock = FundamentalAnswerMapper.secShares(root, 42, FETCHED).rows().getFirst();
    assertThat(stock.getValue()).isEqualTo(389_100_000);
    assertThat(stock.getPeriodEnd()).isEqualTo(LocalDate.of(2026, 9, 18));
    assertThat(stock.getMetric()).isEqualTo(FundamentalMetric.SHARES);
  }

  @Test
  @DisplayName("A period average, foreign ADR report, malformed date or wrong units cannot become a stock")
  void noAverageOrAdrFallback() {
    var answer = FundamentalAnswerMapper.secShares(concept("""
        {"start":"2025-01-01","end":"2025-12-31","val":427000000,"filed":"2026-01-20","form":"10-K"},
        {"end":"2026-09-18","val":389200000,"filed":"2026-09-24","form":"20-F"},
        {"end":"2026-02-30","val":389200000,"filed":"2026-09-24","form":"10-Q"},
        {"end":"2026-09-18","val":0,"filed":"2026-09-24","form":"10-Q"}
        """), 42, FETCHED);
    assertThat(answer.isUsable()).isTrue();
    assertThat(answer.rows()).isEmpty();
    assertThat(FundamentalAnswerMapper.secShares(FundamentalFeedSupport.parse("""
        {"taxonomy":"us-gaap","tag":"WeightedAverageNumberOfDilutedSharesOutstanding","units":{"shares":[]}}
        """), 42, FETCHED).isUsable()).isFalse();
  }

  @Test
  @DisplayName("An ambiguous stock at the same measurement and filing date is not guessed")
  void conflictingFactsSkipped() {
    assertThat(FundamentalAnswerMapper.secShares(concept("""
        {"end":"2026-09-18","val":389200000,"filed":"2026-09-24","form":"10-Q"},
        {"end":"2026-09-18","val":427000000,"filed":"2026-09-24","form":"10-Q"}
        """), 42, FETCHED).rows()).isEmpty();
  }

  @Test
  @DisplayName("The ticker directory excludes ambiguous tickers and companies with multiple known listed classes")
  void issuerMappingDoesNotMixClasses() {
    var directory = FundamentalFeedSupport.parse("""
        {"0":{"cik_str":796343,"ticker":"ADBE"},
         "1":{"cik_str":1652044,"ticker":"GOOG"},"2":{"cik_str":1652044,"ticker":"GOOGL"},
         "3":{"cik_str":111,"ticker":"DUP"},"4":{"cik_str":222,"ticker":"DUP"},
         "5":{"cik_str":12927,"ticker":"BA"},"6":{"cik_str":12927,"ticker":"BA-PA"}}
        """);
    assertThat(SecFundamentalConnector.singleClassTickers(directory)).isEqualTo(Map.of("ADBE", 796343L));
  }

  private static JsonNode concept(String facts) {
    return FundamentalFeedSupport.parse("""
        {"cik":796343,"taxonomy":"dei","tag":"EntityCommonStockSharesOutstanding","units":{"shares":[
        """ + facts + "]}}");
  }
}
