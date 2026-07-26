package grafioschtrader.service;

import java.io.InputStream;
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

import grafioschtrader.calendar.rule.HolidayRuleSetYamlParser;

/**
 * Validates the YAML of a trading calendar rule set.
 *
 * <p>
 * The rules used to be classpath files whose mistakes surfaced at startup. They are edited by users now, so the same
 * strictness has to be applied before saving. Validation runs in three stages, each reporting all of its findings
 * before the next one is attempted, so the editor can show everything that is wrong at once: YAML syntax, the JSON
 * Schema that also drives the editor's completions, and finally binding to the rule model, which is what catches a
 * rule missing a field its type requires.
 * </p>
 */
@Service
public class TradingCalendarRuleYamlValidator {

  private static final String SCHEMA_RESOURCE = "/schemas/trading-calendar-rule-schema.json";

  private final YAMLMapper yamlMapper = new YAMLMapper();
  private final ObjectMapper jsonMapper = new ObjectMapper();
  private volatile Schema cachedSchema;

  /**
   * Validates the YAML of a rule set.
   *
   * @param ruleYaml the YAML text, may be null or blank for a set that only inherits
   * @return list of validation messages, empty when the YAML is valid
   */
  public List<String> validate(String ruleYaml) {
    List<String> errors = new ArrayList<>();
    if (ruleYaml == null || ruleYaml.isBlank()) {
      return errors;
    }

    JsonNode yamlNode;
    try {
      yamlNode = yamlMapper.readTree(ruleYaml);
    } catch (Exception e) {
      errors.add("YAML syntax error: " + e.getMessage());
      return errors;
    }

    try {
      String jsonString = jsonMapper.writeValueAsString(yamlNode);
      tools.jackson.databind.JsonNode jsonNode = JsonMapperFactory.getInstance().readTree(jsonString);
      for (Error error : getSchema().validate(jsonNode)) {
        errors.add("Schema: " + error.getMessage());
      }
    } catch (Exception e) {
      errors.add("Schema validation error: " + e.getMessage());
    }

    try {
      HolidayRuleSetYamlParser.parse(ruleYaml);
    } catch (Exception e) {
      errors.add("Rule error: " + e.getMessage());
    }
    return errors;
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
            throw new IllegalStateException("Cannot load " + SCHEMA_RESOURCE, e);
          }
        }
      }
    }
    return cachedSchema;
  }
}
