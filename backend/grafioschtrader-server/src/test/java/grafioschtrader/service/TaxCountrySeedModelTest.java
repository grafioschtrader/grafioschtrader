package grafioschtrader.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import grafioschtrader.common.DataBusinessHelper;
import grafioschtrader.dto.TaxEstimateRequest;
import grafioschtrader.dto.TaxEstimateRequest.EventKind;
import grafioschtrader.dto.TaxModelConfig;

/** Validates and exercises the active country models embedded in the merged Flyway migration. */
class TaxCountrySeedModelTest {
  private static final String MIGRATION = "src/main/resources/db/migration/"
      + "V0_37_0__algo_alert_state_schedule_delivery_simulation_opening_dashboard_and_tenant_fx_convention.sql";
  private static final double GROSS = 10000;
  private static final TaxEvalExEstimator ESTIMATOR = new TaxEvalExEstimator();
  private static final Pattern TUPLE_START = Pattern.compile("(?m)^\\('([A-Z]{2})', CONCAT_WS\\(CHAR\\(10\\),");
  private static final Pattern SQL_STRING = Pattern.compile("'((?:''|[^'])*)'");

  @Test
  void migrationContainsTheRequestedValidDocumentedModels() throws Exception {
    Map<String, String> yamlByCountry = seededYaml();

    assertThat(yamlByCountry.keySet()).containsExactlyInAnyOrder("US", "CN", "DE", "JP", "IN", "GB", "FR", "IT", "CA",
        "BR", "CH", "AT", "PL");
    yamlByCountry.forEach((country, yaml) -> {
      assertThat(yaml).contains("Checked 2026-09-13", "# Source").doesNotContain("periods:");
      TaxModelConfig model = ESTIMATOR.parse(yaml);
      assertThat(model.transactionTaxes()).as(country + " transaction rules").isNotNull();
      assertThat(model.incomeWithholding()).as(country + " income rules").isNotNull();
    });
  }

  @ParameterizedTest(name = "{0} dividend={1}, interest={2}")
  @MethodSource("incomeRates")
  void incomeModelsUseTheAgreedCurrentNonTreatyRates(String country, String dividendRate, String interestRate)
      throws Exception {
    String yaml = seededYaml().get(country);

    assertRate(yaml, country, EventKind.DIVIDEND, dividendRate, "FIXED_INCOME");
    assertRate(yaml, country, EventKind.SECURITY_INTEREST, interestRate, "FIXED_INCOME");
  }

  @Test
  void germanConvertibleBondUsesTheExceptionalWithholdingRate() throws Exception {
    assertRate(seededYaml().get("DE"), "DE", EventKind.SECURITY_INTEREST, "0.26375", "CONVERTIBLE_BOND");
  }

  @Test
  void allCountryModelsAddOnlyTheApplicableIssuerContribution() throws Exception {
    Map<String, TaxModelConfig> models = new LinkedHashMap<>();
    seededYaml().forEach((country, yaml) -> models.put(country, ESTIMATOR.parse(yaml)));
    TaxEstimateRequest request = request(EventKind.DIVIDEND, "US");

    var result = ESTIMATOR.evaluate(models, request);

    assertThat(result.complete()).isTrue();
    assertThat(result.estimatedTax()).isEqualTo(3000);
    assertThat(result.matchedRules()).hasSize(13);
    assertThat(result.matchedRules()).filteredOn(match -> match.amount() > 0).extracting(match -> match.country())
        .containsExactly("US");
  }

  @Test
  void ukSdrtIsLimitedToPurchasesOfDirectUkEquities() throws Exception {
    String yaml = seededYaml().get("GB");
    TaxEstimateRequest request = request(EventKind.BUY, "GB");
    request.cleanValue = 10000;
    request.instrument = "DIRECT_INVESTMENT";
    request.assetclass = "EQUITIES";

    assertThat(ESTIMATOR.estimate(withYaml(request, "GB", yaml)).estimatedTax()).isEqualTo(50);
    request.eventKind = EventKind.SELL;
    assertThat(ESTIMATOR.estimate(request).estimatedTax()).isEqualTo(0);
    request.eventKind = EventKind.BUY;
    request.issuerCountry = "US";
    assertThat(ESTIMATOR.estimate(request).estimatedTax()).isEqualTo(0);
  }

  @Test
  void swissStampDutyUsesDealerExemptionAndIssuerClassification() throws Exception {
    String yaml = seededYaml().get("CH");
    TaxEstimateRequest request = request(EventKind.BUY, "CH");
    request.cleanValue = 10000;
    request.instrument = "DIRECT_INVESTMENT";
    request.assetclass = "EQUITIES";
    request.dealerCountry = "CH";
    request.exemptInvestor = false;

    assertThat(ESTIMATOR.estimate(withYaml(request, "CH", yaml)).estimatedTax()).isEqualTo(7.5);
    request.issuerCountry = "US";
    assertThat(ESTIMATOR.estimate(request).estimatedTax()).isEqualTo(15);
    request.exemptInvestor = true;
    assertThat(ESTIMATOR.estimate(request).estimatedTax()).isEqualTo(0);
    request.exemptInvestor = false;
    request.dealerCountry = "GB";
    assertThat(ESTIMATOR.estimate(request).estimatedTax()).isEqualTo(0);
  }

  private static Stream<org.junit.jupiter.params.provider.Arguments> incomeRates() {
    return Stream.of(org.junit.jupiter.params.provider.Arguments.of("US", "0.30", "0.30"),
        org.junit.jupiter.params.provider.Arguments.of("CN", "0.20", "0.20"),
        org.junit.jupiter.params.provider.Arguments.of("DE", "0.26375", "0"),
        org.junit.jupiter.params.provider.Arguments.of("JP", "0.15315", "0.15315"),
        org.junit.jupiter.params.provider.Arguments.of("IN", "0.20", "0.20"),
        org.junit.jupiter.params.provider.Arguments.of("GB", "0", "0.20"),
        org.junit.jupiter.params.provider.Arguments.of("FR", "0.128", "0"),
        org.junit.jupiter.params.provider.Arguments.of("IT", "0.26", "0.26"),
        org.junit.jupiter.params.provider.Arguments.of("CA", "0.25", "0"),
        org.junit.jupiter.params.provider.Arguments.of("BR", "0.10", "0.15"),
        org.junit.jupiter.params.provider.Arguments.of("CH", "0.35", "0.35"),
        org.junit.jupiter.params.provider.Arguments.of("AT", "0.275", "0.275"),
        org.junit.jupiter.params.provider.Arguments.of("PL", "0.19", "0.20"));
  }

  private static void assertRate(String yaml, String country, EventKind kind, String rate, String assetclass) {
    TaxEstimateRequest request = request(kind, country);
    request.assetclass = assetclass;
    var result = ESTIMATOR.estimate(withYaml(request, country, yaml));
    double expected = DataBusinessHelper.roundStandard(GROSS * Double.parseDouble(rate));
    assertThat(result.complete()).as(country + " " + kind).isTrue();
    assertThat(result.estimatedTax()).isEqualTo(expected);
  }

  private static TaxEstimateRequest request(EventKind kind, String issuerCountry) {
    TaxEstimateRequest request = new TaxEstimateRequest();
    request.eventKind = kind;
    request.eventDate = LocalDate.of(2026, 9, 13);
    request.currency = "CHF";
    request.grossIncome = GROSS;
    request.issuerCountry = issuerCountry;
    return request;
  }

  private static TaxEstimateRequest withYaml(TaxEstimateRequest request, String country, String yaml) {
    request.countryCode = country;
    request.yaml = yaml;
    return request;
  }

  private static Map<String, String> seededYaml() throws Exception {
    String sql = Files.readString(Path.of(MIGRATION));
    int insert = sql.indexOf("INSERT INTO tax_country (country_code, tax_model_yaml) VALUES");
    int upsert = sql.indexOf("ON DUPLICATE KEY UPDATE tax_model_yaml", insert);
    assertThat(insert).isGreaterThanOrEqualTo(0);
    assertThat(upsert).isGreaterThan(insert);
    String values = sql.substring(insert, upsert);
    var tupleMatcher = TUPLE_START.matcher(values);
    List<Integer> starts = new java.util.ArrayList<>();
    List<Integer> contentStarts = new java.util.ArrayList<>();
    List<String> countries = new java.util.ArrayList<>();
    while (tupleMatcher.find()) {
      starts.add(tupleMatcher.start());
      contentStarts.add(tupleMatcher.end());
      countries.add(tupleMatcher.group(1));
    }
    Map<String, String> models = new LinkedHashMap<>();
    for (int i = 0; i < countries.size(); i++) {
      int end = i + 1 < starts.size() ? starts.get(i + 1) : values.length();
      var stringMatcher = SQL_STRING.matcher(values.substring(contentStarts.get(i), end));
      List<String> lines = new java.util.ArrayList<>();
      while (stringMatcher.find())
        lines.add(stringMatcher.group(1).replace("''", "'"));
      models.put(countries.get(i), String.join("\n", lines));
    }
    return models;
  }
}
