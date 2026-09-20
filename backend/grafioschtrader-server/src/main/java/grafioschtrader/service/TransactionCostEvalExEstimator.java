package grafioschtrader.service;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.ezylang.evalex.Expression;
import com.ezylang.evalex.data.EvaluationValue;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import com.networknt.schema.serialization.JsonMapperFactory;

import grafioschtrader.dto.FeeModelConfig;
import grafioschtrader.dto.FeeModelPeriod;
import grafioschtrader.dto.FeeRule;
import grafioschtrader.dto.TransactionCostEstimateRequest;
import grafioschtrader.dto.TransactionCostEstimateResult;
import grafioschtrader.entities.Assetclass;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.TradingPlatformPlan;
import grafioschtrader.repository.TradingPlatformPlanJpaRepository;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.SpecialInvestmentInstruments;
import grafioschtrader.types.TransactionType;

/**
 * Evaluates rule-based fee models configured as YAML with EvalEx expressions on TradingPlatformPlan. Supports two
 * formats: flat rules (always applicable) or time-based periods with nested rules. Rules within a format are evaluated
 * top-to-bottom; the first rule whose condition is true determines the fee.
 */
@Service
public class TransactionCostEvalExEstimator {

  private static final Logger log = LoggerFactory.getLogger(TransactionCostEvalExEstimator.class);

  private static final String SCHEMA_RESOURCE = "/schemas/fee-model-schema.json";

  @Autowired
  private TradingPlatformPlanJpaRepository tradingPlatformPlanJpaRepository;

  private final YAMLMapper yamlMapper = new YAMLMapper();
  private final ObjectMapper jsonMapper = new ObjectMapper();
  private volatile Schema cachedSchema;

  /**
   * Estimates transaction costs using inline YAML if provided, otherwise falls back to the DB-based plan lookup.
   *
   * @param request the estimation request, optionally containing inline YAML
   * @return the estimation result with the matched rule and calculated cost
   */
  public TransactionCostEstimateResult estimateWithOptionalYaml(TransactionCostEstimateRequest request) {
    if (request.getYaml() != null && !request.getYaml().isBlank()) {
      return evaluateYaml(request.getYaml(), request);
    }
    return estimate(request);
  }

  /**
   * Estimates transaction costs by evaluating the YAML fee model on the given TradingPlatformPlan.
   *
   * @param request the estimation request containing trade parameters and the plan ID
   * @return the estimation result with the matched rule and calculated cost
   */
  public TransactionCostEstimateResult estimate(TransactionCostEstimateRequest request) {
    try {
      TradingPlatformPlan plan = tradingPlatformPlanJpaRepository.findById(request.getIdTradingPlatformPlan())
          .orElse(null);
      if (plan == null) {
        return TransactionCostEstimateResult
            .error("TradingPlatformPlan not found: " + request.getIdTradingPlatformPlan());
      }
      if (plan.getFeeModelYaml() == null || plan.getFeeModelYaml().isBlank()) {
        return TransactionCostEstimateResult.error("No fee model YAML configured on this plan");
      }
      return evaluateYaml(plan.getFeeModelYaml(), request);
    } catch (Exception e) {
      log.error("Fee estimation failed", e);
      return TransactionCostEstimateResult.error("Estimation failed: " + e.getMessage());
    }
  }

  /**
   * Evaluates a fee model YAML string directly (without loading from DB), useful for testing. Supports both flat rules
   * and time-based periods format.
   *
   * @param yaml    the YAML fee model string
   * @param request the estimation request with trade parameters and optional transactionDate
   * @return the estimation result
   */
  public TransactionCostEstimateResult evaluateYaml(String yaml, TransactionCostEstimateRequest request) {
    try {
      FeeModelConfig config = yamlMapper.readValue(yaml, FeeModelConfig.class);

      List<FeeRule> rules;
      if (config.getPeriods() != null && !config.getPeriods().isEmpty()) {
        rules = resolveRulesFromPeriods(config.getPeriods(), request.getTransactionDate());
        if (rules == null) {
          LocalDate txDate = parseTransactionDate(request.getTransactionDate());
          return TransactionCostEstimateResult.error("No fee period applies for date " + txDate);
        }
      } else if (config.getRules() != null && !config.getRules().isEmpty()) {
        rules = config.getRules();
      } else {
        return TransactionCostEstimateResult.error("Fee model has neither rules nor periods");
      }

      return evaluateRules(rules, request);
    } catch (Exception e) {
      log.error("Fee model evaluation failed", e);
      return TransactionCostEstimateResult.error("Evaluation error: " + e.getMessage());
    }
  }

  /**
   * Maps one trade onto the request the fee rules are evaluated against. Both the fee comparison report and the
   * historical replay use it, so that a simulated cost is produced from exactly the inputs the user calibrated the
   * model with; a second, independent mapping would drift from the report without anyone noticing.
   *
   * @param security              the traded instrument, supplying instrument type, asset class, MIC and currency
   * @param units                 number of units traded, sign ignored
   * @param quotation             price per unit in the currency of the instrument
   * @param transactionType       ACCUMULATE becomes trade direction 0 (buy), everything else 1 (sell)
   * @param date                  the day the fee applies on, selecting the matching period of a time-based model
   * @param idTradingPlatformPlan the plan whose model is evaluated when no inline YAML is supplied
   * @param fixedAssets           the account or portfolio value a tiered model grades the fee by, 0 when unknown
   * @return a request carrying every variable {@code bindVariables} can bind
   */
  public static TransactionCostEstimateRequest buildRequest(Security security, double units, double quotation,
      TransactionType transactionType, LocalDate date, Integer idTradingPlatformPlan, double fixedAssets) {
    TransactionCostEstimateRequest req = new TransactionCostEstimateRequest();
    req.setIdTradingPlatformPlan(idTradingPlatformPlan);
    req.setUnits(units);
    req.setTradeValue(units * quotation);
    Assetclass assetclass = security == null ? null : security.getAssetClass();
    req.setSpecInvestInstrument(assetclass != null && assetclass.getSpecialInvestmentInstrument() != null
        ? (int) assetclass.getSpecialInvestmentInstrument().getValue()
        : 0);
    req.setCategoryType(
        assetclass != null && assetclass.getCategoryType() != null ? (int) assetclass.getCategoryType().getValue() : 0);
    req.setMic(security != null && security.getStockexchange() != null && security.getStockexchange().getMic() != null
        ? security.getStockexchange().getMic()
        : "");
    req.setCurrency(security != null && security.getCurrency() != null ? security.getCurrency() : "");
    req.setTradeDirection(transactionType == TransactionType.ACCUMULATE ? 0 : 1);
    req.setFixedAssets(fixedAssets);
    req.setTransactionDate(date == null ? null : date.toString());
    return req;
  }

  /**
   * Finds the first period whose date range covers the transaction date and returns its rules.
   *
   * @param periods            the list of fee periods to search
   * @param transactionDateStr the transaction date as ISO string, or null for today
   * @return the matching period's rules, or null if no period matches
   */
  private List<FeeRule> resolveRulesFromPeriods(List<FeeModelPeriod> periods, String transactionDateStr) {
    LocalDate txDate = parseTransactionDate(transactionDateStr);
    for (FeeModelPeriod period : periods) {
      LocalDate from = LocalDate.parse(period.getValidFrom());
      LocalDate to = period.getValidTo() != null ? LocalDate.parse(period.getValidTo()) : null;
      if (!txDate.isBefore(from) && (to == null || !txDate.isAfter(to))) {
        return period.getRules();
      }
    }
    return null;
  }

  private LocalDate parseTransactionDate(String transactionDateStr) {
    if (transactionDateStr == null || transactionDateStr.isBlank()) {
      return LocalDate.now();
    }
    return LocalDate.parse(transactionDateStr);
  }

  /**
   * Evaluates a list of fee rules against the request, returning the first match.
   */
  private TransactionCostEstimateResult evaluateRules(List<FeeRule> rules, TransactionCostEstimateRequest request)
      throws Exception {
    for (FeeRule rule : rules) {
      Expression condExpr = new Expression(rule.getCondition());
      bindVariables(condExpr, request);
      EvaluationValue condResult = condExpr.evaluate();

      boolean matched = condResult.isBooleanValue() ? condResult.getBooleanValue()
          : condResult.getNumberValue().compareTo(BigDecimal.ZERO) != 0;

      if (matched) {
        Expression feeExpr = new Expression(rule.getExpression());
        bindVariables(feeExpr, request);
        EvaluationValue feeResult = feeExpr.evaluate();
        double cost = feeResult.getNumberValue().doubleValue();
        return TransactionCostEstimateResult.success(cost, rule.getName());
      }
    }
    return TransactionCostEstimateResult.error("No rule matched the given parameters");
  }

  private void bindVariables(Expression expression, TransactionCostEstimateRequest req) {
    if (req.getTradeValue() != null) {
      expression.with("tradeValue", BigDecimal.valueOf(req.getTradeValue()));
    }
    if (req.getUnits() != null) {
      expression.with("units", BigDecimal.valueOf(req.getUnits()));
    }
    if (req.getSpecInvestInstrument() != null) {
      expression.with("specInvestInstrument", BigDecimal.valueOf(req.getSpecInvestInstrument()));
      SpecialInvestmentInstruments sii = SpecialInvestmentInstruments
          .getSpecialInvestmentInstrumentsByValue(req.getSpecInvestInstrument().byteValue());
      expression.with("instrument", sii != null ? sii.name() : "");
    }
    if (req.getCategoryType() != null) {
      expression.with("categoryType", BigDecimal.valueOf(req.getCategoryType()));
      AssetclassType act = AssetclassType.getAssetClassTypeByValue(req.getCategoryType().byteValue());
      expression.with("assetclass", act != null ? act.name() : "");
    }
    if (req.getMic() != null) {
      expression.with("mic", req.getMic());
    }
    if (req.getCurrency() != null) {
      expression.with("currency", req.getCurrency());
    }
    if (req.getFixedAssets() != null) {
      expression.with("fixedAssets", BigDecimal.valueOf(req.getFixedAssets()));
    }
    if (req.getTradeDirection() != null) {
      expression.with("tradeDirection", BigDecimal.valueOf(req.getTradeDirection()));
    }
  }

  /**
   * Validates a YAML string against the fee model JSON Schema and checks EvalEx syntax for all rules (both flat rules
   * and period-nested rules).
   *
   * @param yaml the YAML fee model string to validate
   * @return list of validation error messages, empty if valid
   */
  public List<String> validate(String yaml) {
    List<String> errors = new ArrayList<>();

    // 1. Parse YAML syntax
    JsonNode yamlNode;
    try {
      yamlNode = yamlMapper.readTree(yaml);
    } catch (Exception e) {
      errors.add("YAML syntax error: " + e.getMessage());
      return errors;
    }

    // 2. Validate against JSON Schema
    try {
      Schema schema = getSchema();
      String jsonString = jsonMapper.writeValueAsString(yamlNode);
      tools.jackson.databind.JsonNode jsonNode3 = JsonMapperFactory.getInstance().readTree(jsonString);
      var validationErrors = schema.validate(jsonNode3);
      for (Error error : validationErrors) {
        errors.add("Schema: " + error.getMessage());
      }
    } catch (Exception e) {
      errors.add("Schema validation error: " + e.getMessage());
    }

    // 3. Check EvalEx syntax for each rule (flat rules or period-nested rules)
    try {
      FeeModelConfig config = yamlMapper.readValue(yaml, FeeModelConfig.class);
      if (config.getRules() != null) {
        validateRulesSyntax(config.getRules(), "", errors);
      }
      if (config.getPeriods() != null) {
        for (int p = 0; p < config.getPeriods().size(); p++) {
          FeeModelPeriod period = config.getPeriods().get(p);
          String periodPrefix = "Period[" + p + "] '" + period.getValidFrom() + "': ";
          if (period.getValidFrom() != null) {
            try {
              LocalDate.parse(period.getValidFrom());
            } catch (DateTimeParseException e) {
              errors.add(periodPrefix + "invalid validFrom date: " + e.getMessage());
            }
          }
          if (period.getValidTo() != null) {
            try {
              LocalDate.parse(period.getValidTo());
            } catch (DateTimeParseException e) {
              errors.add(periodPrefix + "invalid validTo date: " + e.getMessage());
            }
          }
          if (period.getRules() != null) {
            validateRulesSyntax(period.getRules(), periodPrefix, errors);
          }
        }
      }
    } catch (Exception e) {
      errors.add("Rule parsing error: " + e.getMessage());
    }

    return errors;
  }

  /**
   * Validates EvalEx syntax for a list of rules, adding errors with the given prefix.
   */
  private void validateRulesSyntax(List<FeeRule> rules, String prefix, List<String> errors) {
    for (int i = 0; i < rules.size(); i++) {
      FeeRule rule = rules.get(i);
      String rulePrefix = prefix + "Rule[" + i + "] '" + rule.getName() + "': ";
      if (rule.getCondition() != null) {
        try {
          new Expression(rule.getCondition()).validate();
        } catch (Exception e) {
          errors.add(rulePrefix + "condition syntax error: " + e.getMessage());
        }
      }
      if (rule.getExpression() != null) {
        try {
          new Expression(rule.getExpression()).validate();
        } catch (Exception e) {
          errors.add(rulePrefix + "expression syntax error: " + e.getMessage());
        }
      }
    }
  }

  private Schema getSchema() {
    if (cachedSchema == null) {
      synchronized (this) {
        if (cachedSchema == null) {
          try (InputStream is = getClass().getResourceAsStream(SCHEMA_RESOURCE)) {
            if (is == null) {
              throw new IllegalStateException("Schema resource not found: " + SCHEMA_RESOURCE);
            }
            SchemaRegistry registry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_7);
            cachedSchema = registry.getSchema(is);
          } catch (Exception e) {
            throw new IllegalStateException("Failed to load fee model schema", e);
          }
        }
      }
    }
    return cachedSchema;
  }
}
