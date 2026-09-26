package grafioschtrader.service;

import static org.assertj.core.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafioschtrader.dto.FxFeeConfig;
import grafioschtrader.dto.FxMarkupRequest;
import grafioschtrader.dto.FxOutcome;
import grafioschtrader.dto.FxQuote;

@DisplayName("FX tariff coverage, validation and published template boundaries")
class FxMarkupEngineTest {
  static final String COMMISSION = "rules:\n  - name: Commission\n    condition: 'true'\n    expression: '10'\n";
  static final LocalDate DATE = LocalDate.of(2026, 9, 25);
  private final FxMarkupEngine engine = new FxMarkupEngine();

  static String flat(String expression) {
    return "fx:\n  rules:\n    - name: Markup\n      condition: 'true'\n      expression: \"" + expression + "\"\n";
  }

  static FxFeeConfig config(String yaml) {
    return FeeModelResolver.parse(yaml, true).getFx();
  }

  static FxMarkupRequest request(String from, String to, double amount) {
    return new FxMarkupRequest(from, to, FxMarkupRequest.Kind.TRADE, amount, DATE, "XSWX");
  }

  static String template(String name) throws Exception {
    try (var input = FxMarkupEngineTest.class.getResourceAsStream("/fee-models/fx/" + name + ".yaml")) {
      return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private FxQuote quote(String yaml) {
    return engine.quote(config(yaml), request("USD", "CHF", 100), (_, _, _) -> null);
  }

  @Test
  void missingCoverageIsDifferentFromKnownZeroAndInvalid() {
    assertThat(engine.quote(null, request("USD", "CHF", 1), null).outcome()).isEqualTo(FxOutcome.NO_SECTION);
    assertThat(quote(flat("0"))).extracting(FxQuote::outcome, FxQuote::percent).containsExactly(FxOutcome.MATCHED, 0.0);
    assertThat(quote(flat("1").replace("condition: 'true'", "condition: 'false'")).outcome())
        .isEqualTo(FxOutcome.NO_RULE);
    for (String expression : List.of("-0.01", "5", "1 / 0", "'text'", "true", "10^10000"))
      assertThat(quote(flat(expression)).outcome()).as(expression).isEqualTo(FxOutcome.INVALID);
    assertThat(quote(flat("4.999")).percent()).isEqualTo(4.999);
  }

  @Test
  void accountOnlyShapeAndDomainErrorsUseTheSameValidationContract() {
    var validator = new FeeModelYamlValidator();
    assertThat(validator.validate(flat("1"))).isNotEmpty();
    assertThat(validator.validate(flat("1"), true)).isEmpty();
    assertThat(validator.validate(COMMISSION + flat("1"))).isEmpty();
    for (String yaml : List.of(flat("unknown"), flat("tierAmount"), flat("1") + "fx: {}\n",
        flat("1") + "custody: {periods: []}\n", flat("1").replace("  rules:", "  amountCurrency: chf\n  rules:"),
        flat("1").replace("  rules:", "  currencyClasses: {MAJOR: [USD], MINOR: [USD]}\n  rules:")))
      assertThat(validator.validate(yaml, true)).as(yaml).isNotEmpty();
  }

  @Test
  void datedTariffsAreInclusiveAndRejectOverlapReversalAndMissingProvenance() throws Exception {
    String tariff = template("saxo").replace("1900-01-01", "2026-01-01").replace("      status:",
        "      validTo: '2026-09-25'\n      status:");
    assertThat(quote(tariff).outcome()).isEqualTo(FxOutcome.MATCHED);
    assertThat(quote(tariff.replace("2026-09-25", "2026-09-24")).outcome()).isEqualTo(FxOutcome.NO_PERIOD);
    var validator = new FeeModelYamlValidator();
    for (String yaml : List.of(tariff.replace("2026-09-25", "2025-01-01"), tariff.replace("ASSUMED", "UNRESOLVED"),
        tariff + tariff.substring(tariff.indexOf("    - validFrom:")),
        tariff.replace("      note:", "      missingNote:"), tariff.replace("      source:", "      missingSource:"),
        tariff.replace("2026-01-01", "2026-02-30")))
      assertThat(validator.validate(yaml, true)).as(yaml).isNotEmpty();
  }

  @Test
  void everySwissquoteClassPairUsesThePublishedMatrixIncludingNzdAndCnh() throws Exception {
    var config = config(template("swissquote"));
    String[] representatives = { "CHF", "NOK", "HKD", "AED" };
    double[][] matrix = { { .95, 1.25, 1.5, 1 }, { 1.25, 1.5, 2, 1.5 }, { 1.5, 2, 2.5, 2 }, { 1, 1.5, 2, 1 } };
    for (int i = 0; i < 4; i++)
      for (int j = 0; j < 4; j++) {
        var quote = engine.quote(config, request(representatives[i], representatives[j], 100), null);
        assertThat(quote.outcome()).isEqualTo(FxOutcome.MATCHED);
        assertThat(quote.percent()).isEqualTo(matrix[i][j]);
      }
    assertThat(engine.quote(config, request("NZD", "CHF", 100), null).percent()).isEqualTo(.95);
    assertThat(engine.quote(config, request("CNH", "CHF", 100), null).percent()).isEqualTo(1.25);
    assertThat(engine.quote(config, request("HKD", "ZAR", 100), null).percent()).isEqualTo(2.5);
    assertThat(engine.quote(config, request("USD", "INR", 100), null).outcome()).isEqualTo(FxOutcome.NO_RULE);
  }

  @Test
  void migrosTiersUseTariffCurrencyRegardlessOfTenantOrPaymentCurrency() throws Exception {
    var config = config(template("migros"));
    for (String pay : List.of("CHF", "USD", "EUR")) {
      double rate = Map.of("CHF", 1.0, "USD", .8, "EUR", .95).get(pay);
      for (double amount : new double[] { 49999.99, 50000, 50000.01, 249999.99, 250000, 250000.01 }) {
        var quote = engine.quote(config, request(pay, "JPY", amount / rate), (from, to, date) -> {
          assertThat(from).isEqualTo(pay);
          assertThat(to).isEqualTo("CHF");
          assertThat(date).isEqualTo(DATE);
          return rate;
        });
        assertThat(quote.outcome()).isEqualTo(FxOutcome.MATCHED);
        assertThat(quote.percent()).isEqualTo(amount <= 50000 ? 1.5 : 1.0);
      }
    }
    assertThat(engine.quote(config, request("USD", "CHF", 100), (_, _, _) -> null).outcome())
        .isEqualTo(FxOutcome.NO_TIER_RATE);
    assertThat(engine.quote(config, request("CHF", "USD", 100), (_, _, _) -> {
      throw new AssertionError();
    }).percent()).isEqualTo(1.5);
  }

  @Test
  void tierLookupIsLimitedToSelectedPeriodAndTierVariablesAreAvailableInExpressions() {
    String yaml = flat("tierAmount / 1000").replace("  rules:", "  amountCurrency: CHF\n  rules:");
    assertThat(engine.quote(config(yaml), request("USD", "CHF", 1000), (_, _, _) -> .8).percent()).isEqualTo(.8);
    assertThat(quote(flat(
        "IF(payCurrency == 'USD' && receiveCurrency == 'CHF' && kind == 'TRADE' && mic == 'XSWX' && payClass == '' && receiveClass == '', amount / 100, 0)"))
            .percent()).isEqualTo(1);
  }

  @Test
  void anotherPeriodsTierDoesNotRequireAQuoteAndTheFirstMatchWins() {
    String yaml = """
        fx:
          amountCurrency: CHF
          periods:
            - validFrom: '2000-01-01'
              validTo: '2025-12-31'
              status: VERIFIED
              source: Test
              rules:
                - name: Historical tier
                  condition: 'tierAmount > 0'
                  expression: '1'
            - validFrom: '2026-01-01'
              status: VERIFIED
              source: Test
              rules:
                - name: First
                  condition: 'true'
                  expression: '0.5'
                - name: Second
                  condition: 'true'
                  expression: '2'
        """;
    var quote = engine.quote(config(yaml), request("USD", "EUR", 100), (_, _, _) -> {
      throw new AssertionError();
    });
    assertThat(quote.percent()).isEqualTo(.5);
    assertThat(quote.ruleName()).isEqualTo("First");
    assertThat(quote.periodValidFrom()).isEqualTo("2026-01-01");
    assertThat(quote.status()).isEqualTo(FxFeeConfig.Status.VERIFIED);
  }

  @Test
  void transfersHaveNoSecurityMic() {
    var request = new FxMarkupRequest("USD", "CHF", FxMarkupRequest.Kind.TRANSFER, 100, DATE, "XSWX");
    assertThat(engine.quote(config(flat("IF(mic == '', 1, 2)")), request, null).percent()).isEqualTo(1);
  }

  @Test
  void allTemplatesValidateAsPartOfAPlan() throws Exception {
    for (String broker : List.of("swissquote", "migros", "postfinance", "saxo"))
      assertThat(YamlConfigurationValidation.validate(YamlConfigurationValidation.Format.FEES,
          COMMISSION + template(broker), true)).isEmpty();
  }
}
