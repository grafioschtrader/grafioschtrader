package grafioschtrader.service;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import com.networknt.schema.serialization.JsonMapperFactory;

import grafioschtrader.common.StrictYaml;
import grafioschtrader.dto.FeeModelConfig;
import grafioschtrader.dto.FeeModelPeriod;
import grafioschtrader.dto.FeeRule;

/** Repository-independent fee validation shared by saving, previews and the read-only audit. */
@Service
public class FeeModelYamlValidator {
  private static final String SCHEMA_RESOURCE = "/schemas/fee-model-schema.json";
  private final YAMLMapper yamlMapper = new YAMLMapper();
  private final ObjectMapper jsonMapper = new ObjectMapper();
  private volatile Schema cachedSchema;

  /**
   * Validates a YAML string against the fee model JSON Schema and checks EvalEx syntax for all rules (both flat rules
   * and period-nested rules).
   *
   * @param yaml the YAML fee model string to validate
   * @return list of validation error messages, empty if valid
   */
  public List<String> validate(String yaml) {
    return validate(yaml, false);
  }

  public List<String> validate(String yaml, boolean account) {
    List<String> errors = new ArrayList<>();
    if (yaml == null || yaml.isBlank())
      return errors;

    // 1. Parse YAML syntax
    JsonNode yamlNode;
    try {
      StrictYaml.validate(yaml);
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
      if (!account && config.getRules() == null && config.getPeriods() == null)
        errors.add("A plan must define commission rules or periods");
      errors.addAll(FxMarkupEngine.validate(config.getFx()));
      errors.addAll(CustodyFeeEngine.validate(config.getCustody()));
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
  static void validateRulesSyntax(List<FeeRule> rules, String prefix, List<String> errors) {
    for (int i = 0; i < rules.size(); i++) {
      FeeRule rule = rules.get(i);
      String rulePrefix = prefix + "Rule[" + i + "] '" + rule.getName() + "': ";
      if (rule.getCondition() != null) {
        try {
          FeeRuleEvaluator.expression(rule.getCondition()).validate();
        } catch (Exception e) {
          errors.add(rulePrefix + "condition syntax error: " + e.getMessage());
        }
      }
      if (rule.getExpression() != null) {
        try {
          FeeRuleEvaluator.expression(rule.getExpression()).validate();
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
