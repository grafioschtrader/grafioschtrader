package grafioschtrader.service;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import grafioschtrader.dto.TaxEstimateRequest;
import grafioschtrader.dto.TaxEstimateRequest.EventKind;

/** Synthetic rates exercise evaluator semantics without claiming national tax coverage. */
@DisplayName("Simulation tax validation and partial estimates")
class TaxEvalExEstimatorTest {
  private final TaxEvalExEstimator estimator = new TaxEvalExEstimator();
  private static final String MODEL = """
      version: 1
      transactionTaxes:
        rules:
          - name: purchase
            condition: 'eventKind == "BUY"'
            expression: 'cleanValue * 0.01'
          - name: other
            expression: '0'
      incomeWithholding:
        rules:
          - name: withholding
            expression: 'grossIncome * 0.2'
      """;

  private TaxEstimateRequest request(EventKind kind) {
    TaxEstimateRequest request = new TaxEstimateRequest();
    request.eventKind = kind;
    request.eventDate = LocalDate.of(2025, 6, 1);
    request.currency = "CHF";
    request.cleanValue = 1000;
    request.grossIncome = 100;
    request.yaml = MODEL;
    return request;
  }

  @Test
  @DisplayName("Countries add in deterministic order and explicit zero remains complete")
  void additiveAndZero() {
    var model = estimator.parse(MODEL);
    var result = estimator.evaluate(Map.of("GB", model, "CH", model), request(EventKind.BUY));
    assertThat(result.complete()).isTrue();
    assertThat(result.estimatedTax()).isEqualTo(20);
    assertThat(result.matchedRules()).extracting(m -> m.country()).containsExactly("CH", "GB");
    assertThat(estimator.estimate(request(EventKind.SELL)).complete()).isTrue();
    assertThat(estimator.estimate(request(EventKind.SELL)).estimatedTax()).isEqualTo(0);
  }

  @Test
  @DisplayName("Both income kinds use gross income rather than trade consideration")
  void incomeKinds() {
    for (var kind : new EventKind[] { EventKind.DIVIDEND, EventKind.SECURITY_INTEREST })
      assertThat(estimator.estimate(request(kind)).estimatedTax()).isEqualTo(20);
  }

  @Test
  @DisplayName("Missing referenced metadata does not select a foreign or catch-all rule")
  void absentMetadata() {
    var request = request(EventKind.BUY);
    request.yaml = MODEL.replace("eventKind == \"BUY\"", "issuerCountry != \"CH\"");
    var result = estimator.estimate(request);
    assertThat(result.complete()).isFalse();
    assertThat(result.estimatedTax()).isEqualTo(0);
    assertThat(result.warnings()).extracting(w -> w.code()).containsExactly("TAX_INPUT_MISSING");
  }

  @Test
  @DisplayName("An earlier explicit zero can establish non-applicability without absent metadata")
  void earlyZero() {
    var request = request(EventKind.SELL);
    request.yaml = MODEL.replace("eventKind == \"BUY\"", "eventKind == \"SELL\"").replace("cleanValue * 0.01", "0")
        .replace("expression: '0'\n", "expression: 'issuerCountry'\n");
    // A separate known-input first rule is authoritative.
    request.yaml = """
        version: 1
        transactionTaxes:
          rules:
            - name: sale
              condition: 'eventKind == "SELL"'
              expression: '0'
            - name: foreign
              condition: 'issuerCountry != "CH"'
              expression: '10'
        """;
    assertThat(estimator.estimate(request).complete()).isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = { "version: 1\nversion: 1\ntransactionTaxes: {rules: []}",
      "version: 1\nunknown: true\ntransactionTaxes: {rules: []}",
      "version: 1\ntransactionTaxes: {rules: [{name: bad, expression: today}]}",
      "version: 1\ntransactionTaxes: {rules: [{name: bad, expression: '1 +'}]}",
      "version: 1\ntransactionTaxes: {rules: [], periods: []}" })
  @DisplayName("Invalid schema, duplicates and unknown variables are rejected on load and save")
  void invalidModels(String yaml) {
    assertThat(estimator.validate(yaml)).isNotEmpty();
    assertThatThrownBy(() -> estimator.parse(yaml)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("Inclusive period boundaries reject overlap but permit adjacent dates")
  void periods() {
    String yaml = """
        version: 1
        transactionTaxes:
          periods:
            - validFrom: "2025-01-01"
              validTo: "2025-06-01"
              rules: [{name: first, expression: "1"}]
            - validFrom: "2025-06-02"
              rules: [{name: second, expression: "2"}]
        """;
    var request = request(EventKind.BUY);
    request.yaml = yaml;
    assertThat(estimator.estimate(request).estimatedTax()).isEqualTo(1);
    request.eventDate = request.eventDate.plusDays(1);
    assertThat(estimator.estimate(request).estimatedTax()).isEqualTo(2);
    assertThat(estimator.validate(yaml.replace("2025-06-02", "2025-06-01"))).isNotEmpty();
    assertThat(estimator.validate(yaml.replace("2025-01-01", "2025-02-30"))).isNotEmpty();
  }

  @Test
  @DisplayName("One failing country retains the successful country contribution")
  void partialFailure() {
    var result = estimator.evaluate(
        Map.of("CH", estimator.parse(MODEL), "GB", estimator.parse(MODEL.replace("cleanValue * 0.01", "-1"))),
        request(EventKind.BUY));
    assertThat(result.complete()).isFalse();
    assertThat(result.estimatedTax()).isEqualTo(10);
  }

  @Test
  @DisplayName("Over-withholding is omitted while full withholding is valid")
  void excessiveWithholding() {
    var request = request(EventKind.DIVIDEND);
    request.yaml = MODEL.replace("grossIncome * 0.2", "grossIncome * 1.01");
    assertThat(estimator.estimate(request).estimatedTax()).isEqualTo(0);
    assertThat(estimator.estimate(request).complete()).isFalse();
    request.yaml = MODEL.replace("grossIncome * 0.2", "grossIncome");
    assertThat(estimator.estimate(request).estimatedTax()).isEqualTo(100);
    assertThat(estimator.estimate(request).complete()).isTrue();
  }

  @Test
  @DisplayName("price is accepted and quotation is an unknown variable")
  void priceNotQuotation() {
    var request = request(EventKind.BUY);
    request.units = 10;
    request.price = 100;
    request.yaml = """
        version: 1
        transactionTaxes:
          rules:
            - name: by price
              expression: 'units * price * 0.01'
        """;
    assertThat(estimator.estimate(request).estimatedTax()).isEqualTo(10);
    assertThat(estimator.validate("""
        version: 1
        transactionTaxes:
          rules:
            - name: old name
              expression: quotation
        """)).isNotEmpty();
  }

  @ParameterizedTest
  @CsvSource({ "0, 11", "2, 10.56", "3, 10.556", "8, 10.55555" })
  @DisplayName("Estimate totals and country matches use the requested currency precision")
  void currencyPrecision(int precision, double expected) {
    var request = request(EventKind.BUY);
    request.yaml = """
        version: 1
        transactionTaxes:
          rules:
            - name: fixed
              expression: '10.55555'
        """;

    var result = estimator.estimate(request, precision);

    assertThat(result.estimatedTax()).isEqualTo(expected);
    assertThat(result.matchedRules()).singleElement()
        .satisfies(match -> assertThat(match.amount()).isEqualTo(expected));
  }
}
