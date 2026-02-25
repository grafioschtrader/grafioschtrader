package grafioschtrader.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafioschtrader.dto.TransactionCostEstimateRequest;
import grafioschtrader.dto.TransactionCostEstimateResult;

/**
 * Tests for EvalEx-based fee model evaluation without Spring context.
 * Uses {@link TransactionCostEvalExEstimator#evaluateYaml(String, TransactionCostEstimateRequest)} directly.
 */
class TransactionCostEvalExEstimatorTest {

  private TransactionCostEvalExEstimator estimator;

  @BeforeEach
  void setUp() {
    estimator = new TransactionCostEvalExEstimator();
  }

  // ---------- Flat fee ----------

  @Test
  @DisplayName("Flat fee rule returns constant cost")
  void flatFee() {
    String yaml = """
        rules:
          - name: "Flat fee"
            condition: "true"
            expression: "25.0"
        """;
    TransactionCostEstimateRequest req = buildRequest(10000.0, 100.0, null, null, null, null, null, null);
    TransactionCostEstimateResult result = estimator.evaluateYaml(yaml, req);
    assertThat(result.getError()).isNull();
    assertThat(result.getEstimatedCost()).isEqualTo(25.0);
    assertThat(result.getMatchedRuleName()).isEqualTo("Flat fee");
  }

  // ---------- Percentage with minimum ----------

  @Test
  @DisplayName("MAX(min, percentage) returns the higher of flat minimum or percentage")
  void percentageWithMinimum() {
    String yaml = """
        rules:
          - name: "Swiss stocks"
            condition: "true"
            expression: "MAX(9.0, tradeValue * 0.001)"
        """;
    // 0.1% of 5000 = 5.0 → MIN wins → 9.0
    TransactionCostEstimateRequest req = buildRequest(5000.0, 50.0, null, null, null, null, null, null);
    TransactionCostEstimateResult result = estimator.evaluateYaml(yaml, req);
    assertThat(result.getError()).isNull();
    assertThat(result.getEstimatedCost()).isEqualTo(9.0);

    // 0.1% of 20000 = 20.0 → percentage wins → 20.0
    req = buildRequest(20000.0, 200.0, null, null, null, null, null, null);
    result = estimator.evaluateYaml(yaml, req);
    assertThat(result.getEstimatedCost()).isEqualTo(20.0);
  }

  // ---------- Multiple rules, first match ----------

  @Test
  @DisplayName("First matching rule wins in top-to-bottom evaluation")
  void firstMatchWins() {
    String yaml = """
        rules:
          - name: "Swiss stocks (SIX)"
            condition: "mic == \\"XSWX\\""
            expression: "MAX(9.0, tradeValue * 0.001)"
          - name: "US stocks"
            condition: "mic == \\"XNYS\\" OR mic == \\"XNAS\\""
            expression: "MAX(15.0, tradeValue * 0.0015)"
          - name: "Default"
            condition: "true"
            expression: "tradeValue * 0.002"
        """;
    // SIX match
    TransactionCostEstimateRequest req = buildRequest(10000.0, 100.0, null, null, "XSWX", null, null, null);
    TransactionCostEstimateResult result = estimator.evaluateYaml(yaml, req);
    assertThat(result.getMatchedRuleName()).isEqualTo("Swiss stocks (SIX)");
    assertThat(result.getEstimatedCost()).isEqualTo(10.0);

    // NYSE match
    req = buildRequest(10000.0, 100.0, null, null, "XNYS", null, null, null);
    result = estimator.evaluateYaml(yaml, req);
    assertThat(result.getMatchedRuleName()).isEqualTo("US stocks");
    assertThat(result.getEstimatedCost()).isEqualTo(15.0);

    // Default match
    req = buildRequest(10000.0, 100.0, null, null, "XLON", null, null, null);
    result = estimator.evaluateYaml(yaml, req);
    assertThat(result.getMatchedRuleName()).isEqualTo("Default");
    assertThat(result.getEstimatedCost()).isEqualTo(20.0);
  }

  // ---------- Portfolio-value tiers ----------

  @Test
  @DisplayName("Portfolio-value dependent tiers select correct fee level")
  void portfolioTiers() {
    String yaml = """
        rules:
          - name: "Premium (>= 200k)"
            condition: "fixedAssets >= 200000"
            expression: "MAX(8.0, tradeValue * 0.0008)"
          - name: "Standard (< 200k)"
            condition: "true"
            expression: "MAX(15.0, tradeValue * 0.0015)"
        """;
    // Premium tier
    TransactionCostEstimateRequest req = buildRequest(10000.0, 100.0, null, null, null, null, 250000.0, null);
    TransactionCostEstimateResult result = estimator.evaluateYaml(yaml, req);
    assertThat(result.getMatchedRuleName()).isEqualTo("Premium (>= 200k)");
    assertThat(result.getEstimatedCost()).isEqualTo(8.0);

    // Standard tier
    req = buildRequest(10000.0, 100.0, null, null, null, null, 50000.0, null);
    result = estimator.evaluateYaml(yaml, req);
    assertThat(result.getMatchedRuleName()).isEqualTo("Standard (< 200k)");
    assertThat(result.getEstimatedCost()).isEqualTo(15.0);
  }

  // ---------- Per-share pricing ----------

  @Test
  @DisplayName("Per-share pricing uses units variable")
  void perSharePricing() {
    String yaml = """
        rules:
          - name: "US stocks per share"
            condition: "true"
            expression: "MAX(1.0, units * 0.005)"
        """;
    TransactionCostEstimateRequest req = buildRequest(50000.0, 1000.0, null, null, null, null, null, null);
    TransactionCostEstimateResult result = estimator.evaluateYaml(yaml, req);
    assertThat(result.getEstimatedCost()).isEqualTo(5.0);
  }

  // ---------- ETF flat fee using instrument string variable ----------

  @Test
  @DisplayName("instrument string variable discriminates ETF flat fee from direct stock percentage")
  void etfFlatFee() {
    String yaml = """
        rules:
          - name: "ETF flat fee"
            condition: "instrument == \\"ETF\\""
            expression: "5.0"
          - name: "Stocks"
            condition: "true"
            expression: "MAX(9.0, tradeValue * 0.001)"
        """;
    // ETF (specInvestInstrument=1 → instrument="ETF")
    TransactionCostEstimateRequest req = buildRequest(10000.0, 100.0, 1, null, null, null, null, null);
    TransactionCostEstimateResult result = estimator.evaluateYaml(yaml, req);
    assertThat(result.getMatchedRuleName()).isEqualTo("ETF flat fee");
    assertThat(result.getEstimatedCost()).isEqualTo(5.0);

    // Direct investment (specInvestInstrument=0 → instrument="DIRECT_INVESTMENT")
    req = buildRequest(10000.0, 100.0, 0, null, null, null, null, null);
    result = estimator.evaluateYaml(yaml, req);
    assertThat(result.getMatchedRuleName()).isEqualTo("Stocks");
    assertThat(result.getEstimatedCost()).isEqualTo(10.0);
  }

  // ---------- Bond (FIXED_INCOME) with DIRECT_INVESTMENT ----------

  @Test
  @DisplayName("Bond with direct investment uses assetclass and instrument string variables")
  void bondDirectInvestment() {
    String yaml = """
        rules:
          - name: "Bond direct"
            condition: "assetclass == \\"FIXED_INCOME\\" AND instrument == \\"DIRECT_INVESTMENT\\""
            expression: "MAX(25.0, tradeValue * 0.002)"
          - name: "Bond ETF"
            condition: "assetclass == \\"FIXED_INCOME\\" AND instrument == \\"ETF\\""
            expression: "MAX(9.0, tradeValue * 0.001)"
          - name: "Default"
            condition: "true"
            expression: "tradeValue * 0.003"
        """;
    // Bond direct: FIXED_INCOME=1, DIRECT_INVESTMENT=0, tradeValue=50000 → MAX(25, 100) = 100
    TransactionCostEstimateRequest req = buildRequest(50000.0, 50.0, 0, 1, null, null, null, null);
    TransactionCostEstimateResult result = estimator.evaluateYaml(yaml, req);
    assertThat(result.getError()).isNull();
    assertThat(result.getMatchedRuleName()).isEqualTo("Bond direct");
    assertThat(result.getEstimatedCost()).isEqualTo(100.0);

    // Bond ETF: FIXED_INCOME=1, ETF=1, tradeValue=50000 → MAX(9, 50) = 50
    req = buildRequest(50000.0, 50.0, 1, 1, null, null, null, null);
    result = estimator.evaluateYaml(yaml, req);
    assertThat(result.getMatchedRuleName()).isEqualTo("Bond ETF");
    assertThat(result.getEstimatedCost()).isEqualTo(50.0);

    // Equity direct: EQUITIES=0, DIRECT_INVESTMENT=0 → falls to Default
    req = buildRequest(50000.0, 50.0, 0, 0, null, null, null, null);
    result = estimator.evaluateYaml(yaml, req);
    assertThat(result.getMatchedRuleName()).isEqualTo("Default");
    assertThat(result.getEstimatedCost()).isEqualTo(150.0);
  }

  // ---------- Graduated model with IF ----------

  @Test
  @DisplayName("IF() function for graduated trade value tiers")
  void graduatedWithIf() {
    String yaml = """
        rules:
          - name: "Graduated"
            condition: "true"
            expression: "IF(tradeValue < 5000, 20.0, IF(tradeValue < 25000, 35.0, 50.0))"
        """;
    TransactionCostEstimateRequest req = buildRequest(3000.0, 30.0, null, null, null, null, null, null);
    TransactionCostEstimateResult result = estimator.evaluateYaml(yaml, req);
    assertThat(result.getEstimatedCost()).isEqualTo(20.0);

    req = buildRequest(15000.0, 150.0, null, null, null, null, null, null);
    result = estimator.evaluateYaml(yaml, req);
    assertThat(result.getEstimatedCost()).isEqualTo(35.0);

    req = buildRequest(50000.0, 500.0, null, null, null, null, null, null);
    result = estimator.evaluateYaml(yaml, req);
    assertThat(result.getEstimatedCost()).isEqualTo(50.0);
  }

  // ---------- No match ----------

  @Test
  @DisplayName("Returns error when no rule matches")
  void noMatch() {
    String yaml = """
        rules:
          - name: "Swiss only"
            condition: "mic == \\"XSWX\\""
            expression: "10.0"
        """;
    TransactionCostEstimateRequest req = buildRequest(10000.0, 100.0, null, null, "XLON", null, null, null);
    TransactionCostEstimateResult result = estimator.evaluateYaml(yaml, req);
    assertThat(result.getError()).contains("No rule matched");
    assertThat(result.getEstimatedCost()).isNull();
  }

  // ---------- Invalid YAML ----------

  @Test
  @DisplayName("Invalid YAML returns evaluation error")
  void invalidYaml() {
    String yaml = "not: valid: yaml: [";
    TransactionCostEstimateRequest req = buildRequest(10000.0, 100.0, null, null, null, null, null, null);
    TransactionCostEstimateResult result = estimator.evaluateYaml(yaml, req);
    assertThat(result.getError()).isNotNull();
  }

  // ---------- Invalid expression ----------

  @Test
  @DisplayName("Invalid EvalEx expression returns evaluation error")
  void invalidExpression() {
    String yaml = """
        rules:
          - name: "Bad expression"
            condition: "true"
            expression: "NONEXISTENT_FUNC(tradeValue)"
        """;
    TransactionCostEstimateRequest req = buildRequest(10000.0, 100.0, null, null, null, null, null, null);
    TransactionCostEstimateResult result = estimator.evaluateYaml(yaml, req);
    assertThat(result.getError()).isNotNull();
  }

  // ---------- Validation ----------

  @Test
  @DisplayName("Valid YAML passes validation")
  void validYamlPasses() {
    String yaml = """
        rules:
          - name: "Default"
            condition: "true"
            expression: "MAX(9.0, tradeValue * 0.001)"
        """;
    List<String> errors = estimator.validate(yaml);
    assertThat(errors).isEmpty();
  }

  @Test
  @DisplayName("YAML with missing required field fails schema validation")
  void missingFieldFails() {
    String yaml = """
        rules:
          - name: "Missing expression"
            condition: "true"
        """;
    List<String> errors = estimator.validate(yaml);
    assertThat(errors).isNotEmpty();
    assertThat(errors.stream().anyMatch(e -> e.contains("expression"))).isTrue();
  }

  @Test
  @DisplayName("Empty rules array fails schema validation")
  void emptyRulesFails() {
    String yaml = """
        rules: []
        """;
    List<String> errors = estimator.validate(yaml);
    assertThat(errors).isNotEmpty();
  }

  // ---------- Periods: old period uses old rules ----------

  @Test
  @DisplayName("Transaction in old period uses old rules")
  void periodMatchesCorrectDate() {
    String yaml = """
        periods:
          - validFrom: "2020-01-01"
            validTo: "2023-12-31"
            rules:
              - name: "Old fee"
                condition: "true"
                expression: "30.0"
          - validFrom: "2024-01-01"
            rules:
              - name: "New fee"
                condition: "true"
                expression: "15.0"
        """;
    TransactionCostEstimateRequest req = buildRequest(10000.0, 100.0, null, null, null, null, null, null, "2022-06-15");
    TransactionCostEstimateResult result = estimator.evaluateYaml(yaml, req);
    assertThat(result.getError()).isNull();
    assertThat(result.getMatchedRuleName()).isEqualTo("Old fee");
    assertThat(result.getEstimatedCost()).isEqualTo(30.0);
  }

  // ---------- Periods: new period uses new rules ----------

  @Test
  @DisplayName("Transaction in new period uses new rules")
  void periodMatchesNewDate() {
    String yaml = """
        periods:
          - validFrom: "2020-01-01"
            validTo: "2023-12-31"
            rules:
              - name: "Old fee"
                condition: "true"
                expression: "30.0"
          - validFrom: "2024-01-01"
            rules:
              - name: "New fee"
                condition: "true"
                expression: "15.0"
        """;
    TransactionCostEstimateRequest req = buildRequest(10000.0, 100.0, null, null, null, null, null, null, "2025-03-01");
    TransactionCostEstimateResult result = estimator.evaluateYaml(yaml, req);
    assertThat(result.getError()).isNull();
    assertThat(result.getMatchedRuleName()).isEqualTo("New fee");
    assertThat(result.getEstimatedCost()).isEqualTo(15.0);
  }

  // ---------- Periods: open-ended period ----------

  @Test
  @DisplayName("Open-ended period (no validTo) matches future dates")
  void openEndedPeriod() {
    String yaml = """
        periods:
          - validFrom: "2024-01-01"
            rules:
              - name: "Current fee"
                condition: "true"
                expression: "20.0"
        """;
    TransactionCostEstimateRequest req = buildRequest(10000.0, 100.0, null, null, null, null, null, null, "2030-12-31");
    TransactionCostEstimateResult result = estimator.evaluateYaml(yaml, req);
    assertThat(result.getError()).isNull();
    assertThat(result.getMatchedRuleName()).isEqualTo("Current fee");
    assertThat(result.getEstimatedCost()).isEqualTo(20.0);
  }

  // ---------- Periods: no matching period ----------

  @Test
  @DisplayName("Transaction date outside all periods returns error")
  void noMatchingPeriod() {
    String yaml = """
        periods:
          - validFrom: "2024-01-01"
            validTo: "2024-12-31"
            rules:
              - name: "2024 fee"
                condition: "true"
                expression: "10.0"
        """;
    TransactionCostEstimateRequest req = buildRequest(10000.0, 100.0, null, null, null, null, null, null, "2023-06-15");
    TransactionCostEstimateResult result = estimator.evaluateYaml(yaml, req);
    assertThat(result.getError()).contains("No fee period applies for date");
    assertThat(result.getEstimatedCost()).isNull();
  }

  // ---------- Flat rules backward compatible ----------

  @Test
  @DisplayName("Existing flat rules YAML works without transactionDate")
  void flatRulesBackwardCompatible() {
    String yaml = """
        rules:
          - name: "Default"
            condition: "true"
            expression: "25.0"
        """;
    // No transactionDate set — should work as before
    TransactionCostEstimateRequest req = buildRequest(10000.0, 100.0, null, null, null, null, null, null, null);
    TransactionCostEstimateResult result = estimator.evaluateYaml(yaml, req);
    assertThat(result.getError()).isNull();
    assertThat(result.getEstimatedCost()).isEqualTo(25.0);
    assertThat(result.getMatchedRuleName()).isEqualTo("Default");
  }

  // ---------- Periods: boundary date on validFrom ----------

  @Test
  @DisplayName("Transaction on exact validFrom date matches period")
  void periodBoundaryValidFrom() {
    String yaml = """
        periods:
          - validFrom: "2024-01-01"
            validTo: "2024-12-31"
            rules:
              - name: "2024 fee"
                condition: "true"
                expression: "12.0"
        """;
    TransactionCostEstimateRequest req = buildRequest(10000.0, 100.0, null, null, null, null, null, null, "2024-01-01");
    TransactionCostEstimateResult result = estimator.evaluateYaml(yaml, req);
    assertThat(result.getError()).isNull();
    assertThat(result.getMatchedRuleName()).isEqualTo("2024 fee");
  }

  // ---------- Periods: boundary date on validTo ----------

  @Test
  @DisplayName("Transaction on exact validTo date matches period")
  void periodBoundaryValidTo() {
    String yaml = """
        periods:
          - validFrom: "2024-01-01"
            validTo: "2024-12-31"
            rules:
              - name: "2024 fee"
                condition: "true"
                expression: "12.0"
        """;
    TransactionCostEstimateRequest req = buildRequest(10000.0, 100.0, null, null, null, null, null, null, "2024-12-31");
    TransactionCostEstimateResult result = estimator.evaluateYaml(yaml, req);
    assertThat(result.getError()).isNull();
    assertThat(result.getMatchedRuleName()).isEqualTo("2024 fee");
  }

  // ---------- Periods: validation passes ----------

  @Test
  @DisplayName("Valid periods YAML passes validation")
  void validPeriodsYamlPasses() {
    String yaml = """
        periods:
          - validFrom: "2020-01-01"
            validTo: "2023-12-31"
            rules:
              - name: "Old"
                condition: "true"
                expression: "30.0"
          - validFrom: "2024-01-01"
            rules:
              - name: "New"
                condition: "true"
                expression: "15.0"
        """;
    List<String> errors = estimator.validate(yaml);
    assertThat(errors).isEmpty();
  }

  // ---------- Periods: neither rules nor periods ----------

  @Test
  @DisplayName("YAML with neither rules nor periods returns error")
  void neitherRulesNorPeriods() {
    String yaml = """
        other: "something"
        """;
    TransactionCostEstimateRequest req = buildRequest(10000.0, 100.0, null, null, null, null, null, null, null);
    TransactionCostEstimateResult result = estimator.evaluateYaml(yaml, req);
    assertThat(result.getError()).contains("neither rules nor periods");
  }

  // ---------- Helper ----------

  private TransactionCostEstimateRequest buildRequest(Double tradeValue, Double units,
      Integer specInvestInstrument, Integer categoryType, String mic, String currency,
      Double fixedAssets, Integer tradeDirection) {
    return buildRequest(tradeValue, units, specInvestInstrument, categoryType, mic, currency,
        fixedAssets, tradeDirection, null);
  }

  private TransactionCostEstimateRequest buildRequest(Double tradeValue, Double units,
      Integer specInvestInstrument, Integer categoryType, String mic, String currency,
      Double fixedAssets, Integer tradeDirection, String transactionDate) {
    TransactionCostEstimateRequest req = new TransactionCostEstimateRequest();
    req.setTradeValue(tradeValue);
    req.setUnits(units);
    req.setSpecInvestInstrument(specInvestInstrument);
    req.setCategoryType(categoryType);
    req.setMic(mic);
    req.setCurrency(currency);
    req.setFixedAssets(fixedAssets);
    req.setTradeDirection(tradeDirection);
    req.setTransactionDate(transactionDate);
    return req;
  }
}
