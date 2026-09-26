package grafioschtrader.service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import com.ezylang.evalex.Expression;
import com.ezylang.evalex.config.ExpressionConfiguration;
import com.ezylang.evalex.data.EvaluationValue;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import com.networknt.schema.serialization.JsonMapperFactory;

import grafiosch.BaseConstants;
import grafiosch.common.DataHelper;
import grafioschtrader.dto.TaxEstimateRequest;
import grafioschtrader.dto.TaxEstimateResult;
import grafioschtrader.dto.TaxEstimateResult.*;
import grafioschtrader.dto.TaxModelConfig;
import grafioschtrader.dto.TaxModelConfig.*;

/** Stateless country evaluator. Errors are returned per country so valid contributions remain usable. */
@Service
public class TaxEvalExEstimator {
  /**
   * One configuration for every expression. {@code new Expression(text)} builds the default configuration anew on each
   * call, including an instance of every built-in function whose parameters are read by reflection, which a replay
   * evaluating these formulas thousands of times paid for every time. The configuration is never modified here.
   */
  private static final ExpressionConfiguration EVALEX = ExpressionConfiguration.defaultConfiguration();
  public static final int MAX_YAML_BYTES = 65536;
  private static final Set<String> VARIABLES = Set.of("EVENTKIND", "UNITS", "PRICE", "CLEANVALUE", "ACCRUEDINTEREST",
      "TRADEVALUE", "GROSSINCOME", "CURRENCY", "INSTRUMENT", "ASSETCLASS", "MIC", "ISSUERCOUNTRY", "DEALERCOUNTRY",
      "EXCHANGECOUNTRY", "EXEMPTINVESTOR");
  private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
  private final Schema schema;

  public TaxEvalExEstimator() {
    try (var in = getClass().getResourceAsStream("/schemas/tax-model-schema.json")) {
      schema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_7).getSchema(in);
    } catch (Exception e) {
      throw new IllegalStateException("Tax schema unavailable", e);
    }
  }

  /** Parse once per run, using exactly the save-time validation contract. */
  public TaxModelConfig parse(String text) {
    try {
      if (text == null || text.isBlank() || text.getBytes(StandardCharsets.UTF_8).length > MAX_YAML_BYTES)
        throw new IllegalArgumentException("A model must contain 1–65536 UTF-8 bytes");
      grafioschtrader.common.StrictYaml.validate(text);
      LoaderOptions options = new LoaderOptions();
      options.setAllowDuplicateKeys(false);
      options.setMaxAliasesForCollections(0);
      options.setNestingDepthLimit(30);
      options.setCodePointLimit(MAX_YAML_BYTES);
      Object data = new Yaml(new SafeConstructor(options)).load(text);
      String json = mapper.writeValueAsString(data);
      var errors = schema.validate(JsonMapperFactory.getInstance().readTree(json));
      if (!errors.isEmpty())
        throw new IllegalArgumentException(errors.toString());
      // Jackson's formatted date parser may normalize invalid calendar dates; validate the literal first.
      var tree = mapper.readTree(json);
      for (String section : List.of("transactionTaxes", "incomeWithholding")) {
        for (var period : tree.path(section).path("periods")) {
          LocalDate.parse(period.path("validFrom").asText());
          if (period.has("validTo"))
            LocalDate.parse(period.path("validTo").asText());
        }
      }
      TaxModelConfig model = mapper.readValue(json, TaxModelConfig.class);
      validateSection(model.transactionTaxes());
      validateSection(model.incomeWithholding());
      return model;
    } catch (Exception e) {
      throw new IllegalArgumentException("Invalid tax model: " + e.getMessage(), e);
    }
  }

  /** Empty YAML is accepted by the write service as clearing; validation previews require a model. */
  public List<String> validate(String text) {
    try {
      parse(text);
      return List.of();
    } catch (IllegalArgumentException e) {
      return List.of(e.getMessage());
    }
  }

  private static void validateSection(Section section) throws Exception {
    if (section == null)
      return;
    if (section.rules() != null)
      validateRules(section.rules());
    if (section.periods() == null)
      return;
    List<Period> periods = new ArrayList<>(section.periods());
    periods.sort(Comparator.comparing(Period::validFrom));
    LocalDate previousEnd = null;
    boolean first = true;
    for (Period period : periods) {
      if (period.validTo() != null && period.validTo().isBefore(period.validFrom()))
        throw new IllegalArgumentException("Inverted tax period");
      if (!first && (previousEnd == null || !period.validFrom().isAfter(previousEnd)))
        throw new IllegalArgumentException("Overlapping inclusive tax periods");
      validateRules(period.rules());
      previousEnd = period.validTo();
      first = false;
    }
  }

  private static void validateRules(List<Rule> rules) throws Exception {
    for (Rule rule : rules) {
      if (rule.condition() != null)
        validateExpression(rule.condition());
      validateExpression(rule.expression());
    }
  }

  private static void validateExpression(String text) throws Exception {
    Expression expression = new Expression(text, EVALEX);
    expression.validate();
    for (String variable : expression.getUsedVariables())
      if (!VARIABLES.contains(variable.toUpperCase(Locale.ROOT)))
        throw new IllegalArgumentException("Unknown tax variable: " + variable);
  }

  /** Preview one unsaved country model without persistence. */
  public TaxEstimateResult estimate(TaxEstimateRequest request) {
    return estimate(request, BaseConstants.FID_STANDARD_FRACTION_DIGITS);
  }

  /** Preview one unsaved country model and expose monetary results at the requested currency precision. */
  public TaxEstimateResult estimate(TaxEstimateRequest request, int currencyPrecision) {
    try {
      return evaluate(Map.of(request.countryCode == null ? "PREVIEW" : request.countryCode, parse(request.yaml)),
          request, currencyPrecision);
    } catch (Exception e) {
      return result(request, List.of(),
          List.of(new Warning("TAX_MODEL_INVALID", request.countryCode, sectionName(request), e.getMessage())),
          currencyPrecision);
    }
  }

  /** All successful country contributions are additive in country-code order. */
  public TaxEstimateResult evaluate(Map<String, TaxModelConfig> models, TaxEstimateRequest request) {
    return evaluate(models, request, BaseConstants.FID_STANDARD_FRACTION_DIGITS);
  }

  /** All successful country contributions are additive in country-code order. */
  public TaxEstimateResult evaluate(Map<String, TaxModelConfig> models, TaxEstimateRequest request,
      int currencyPrecision) {
    List<Match> matches = new ArrayList<>();
    List<Warning> warnings = new ArrayList<>();
    Map<String, Object> inputs;
    try {
      inputs = inputs(request);
    } catch (Exception e) {
      return result(request, matches,
          List.of(new Warning("TAX_INPUT_INVALID", null, sectionName(request), e.getMessage())), currencyPrecision);
    }
    if (models.isEmpty())
      warnings.add(new Warning("TAX_NO_MODELS", null, null, null));
    for (var entry : new TreeMap<>(models).entrySet()) {
      try {
        matches.add(evaluateCountry(entry.getKey(), entry.getValue(), request, inputs));
      } catch (TaxFailure e) {
        warnings.add(new Warning(e.code, entry.getKey(), sectionName(request), e.getMessage()));
      } catch (Exception e) {
        warnings.add(new Warning("TAX_EVALUATION_ERROR", entry.getKey(), sectionName(request), e.getMessage()));
      }
    }
    return result(request, matches, warnings, currencyPrecision);
  }

  private static Match evaluateCountry(String country, TaxModelConfig model, TaxEstimateRequest request,
      Map<String, Object> inputs) throws Exception {
    Section section = income(request) ? model.incomeWithholding() : model.transactionTaxes();
    if (section == null)
      throw new TaxFailure("TAX_SECTION_MISSING", "No section for event");
    if (section.requiredInputs() != null)
      for (String required : section.requiredInputs())
        require(inputs, required);
    List<Rule> rules = section.rules();
    Period selected = null;
    if (section.periods() != null) {
      selected = section.periods().stream()
          .filter(p -> !request.eventDate.isBefore(p.validFrom())
              && (p.validTo() == null || !request.eventDate.isAfter(p.validTo())))
          .findFirst().orElseThrow(() -> new TaxFailure("TAX_PERIOD_MISSING", "Uncovered event date"));
      rules = selected.rules();
    }
    for (Rule rule : rules) {
      if (rule.condition() != null) {
        EvaluationValue condition = expression(rule.condition(), inputs);
        if (!condition.isBooleanValue())
          throw new IllegalArgumentException("Condition must be boolean");
        if (!condition.getBooleanValue())
          continue;
      }
      EvaluationValue evaluated = expression(rule.expression(), inputs);
      if (!evaluated.isNumberValue())
        throw new IllegalArgumentException("Tax must be numeric");
      double amount = evaluated.getNumberValue().doubleValue();
      if (amount < 0 || !Double.isFinite(amount))
        throw new IllegalArgumentException("Tax must be finite and nonnegative");
      return new Match(country, sectionName(request), selected == null ? null : selected.validFrom(),
          selected == null ? null : selected.validTo(), rule.name(), amount);
    }
    throw new TaxFailure("TAX_RULE_UNMATCHED", "No matching rule");
  }

  private static EvaluationValue expression(String text, Map<String, Object> inputs) throws Exception {
    Expression expression = new Expression(text, EVALEX);
    for (String name : expression.getUsedVariables()) {
      require(inputs, name);
      expression.with(name, inputs.get(name.toUpperCase(Locale.ROOT)));
    }
    return expression.evaluate();
  }

  private static void require(Map<String, Object> inputs, String name) {
    if (!inputs.containsKey(name.toUpperCase(Locale.ROOT)))
      throw new TaxFailure("TAX_INPUT_MISSING", name);
  }

  private static Map<String, Object> inputs(TaxEstimateRequest r) {
    if (r.eventKind == null || r.eventDate == null || r.currency == null || !r.currency.matches("[A-Z]{3}"))
      throw new IllegalArgumentException("Event kind, ISO event date and currency are required");
    Map<String, Object> values = new TreeMap<>();
    values.put("EVENTKIND", r.eventKind.name());
    values.put("CURRENCY", r.currency);
    values.put("UNITS", amount(r.units));
    values.put("PRICE", amount(r.price));
    double cleanValue = income(r) ? 0 : amount(r.cleanValue);
    double accruedInterest = income(r) ? 0 : amount(r.accruedInterest);
    values.put("CLEANVALUE", cleanValue);
    values.put("ACCRUEDINTEREST", accruedInterest);
    values.put("TRADEVALUE", cleanValue + accruedInterest);
    values.put("GROSSINCOME", income(r) ? amount(r.grossIncome) : 0d);
    put(values, "INSTRUMENT", r.instrument);
    put(values, "ASSETCLASS", r.assetclass);
    put(values, "MIC", r.mic);
    put(values, "ISSUERCOUNTRY", r.issuerCountry);
    put(values, "DEALERCOUNTRY", r.dealerCountry);
    put(values, "EXCHANGECOUNTRY", r.exchangeCountry);
    if (r.exemptInvestor != null)
      values.put("EXEMPTINVESTOR", r.exemptInvestor ? 1 : 0);
    return values;
  }

  private static void put(Map<String, Object> values, String key, String value) {
    if (value != null && !value.isBlank())
      values.put(key, value);
  }

  private static double amount(double value) {
    if (value < 0 || !Double.isFinite(value))
      throw new IllegalArgumentException("Applicable amounts must be finite and nonnegative");
    return value;
  }

  private static boolean income(TaxEstimateRequest r) {
    return r.eventKind == TaxEstimateRequest.EventKind.DIVIDEND
        || r.eventKind == TaxEstimateRequest.EventKind.SECURITY_INTEREST;
  }

  private static String sectionName(TaxEstimateRequest r) {
    return income(r) ? "incomeWithholding" : "transactionTaxes";
  }

  private static TaxEstimateResult result(TaxEstimateRequest r, List<Match> matches, List<Warning> warnings,
      int currencyPrecision) {
    double total = matches.stream().mapToDouble(Match::amount).sum();
    List<Warning> resultWarnings = new ArrayList<>(warnings);
    if (income(r) && total > r.grossIncome) {
      total = 0;
      resultWarnings.add(new Warning("TAX_WITHHOLDING_EXCEEDS_GROSS", null, "incomeWithholding", null));
    }
    List<Match> roundedMatches = matches.stream().map(match -> new Match(match.country(), match.section(),
        match.validFrom(), match.validTo(), match.rule(), DataHelper.round(match.amount(), currencyPrecision)))
        .toList();
    return new TaxEstimateResult(DataHelper.round(total, currencyPrecision), r.currency, resultWarnings.isEmpty(),
        roundedMatches, List.copyOf(resultWarnings));
  }

  private static final class TaxFailure extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final String code;

    private TaxFailure(String code, String detail) {
      super(detail);
      this.code = code;
    }
  }
}
