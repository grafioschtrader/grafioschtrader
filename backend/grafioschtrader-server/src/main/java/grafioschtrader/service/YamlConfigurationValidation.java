package grafioschtrader.service;

import java.util.List;

import grafiosch.exceptions.DataViolationException;
import grafioschtrader.algo.strategy.model.complex.StrategyConfigValidator;
import grafioschtrader.common.StrictYaml;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

/** One configuration validation entry point for editors, writes and the read-only audit. No repositories. */
public final class YamlConfigurationValidation {
  public enum Format {
    FEES, FEES_ACCOUNT, TOKENS, CALENDAR, TAXES, STRATEGY, CUSTODY
  }

  public record Diagnostic(String category, String message, String path, Integer line, Integer column) {
  }

  private static final FeeModelYamlValidator FEES = new FeeModelYamlValidator();
  private static final TradingCalendarRuleYamlValidator CALENDAR = new TradingCalendarRuleYamlValidator();
  private static final TaxEvalExEstimator TAXES = new TaxEvalExEstimator();
  private static final TokenConfigYamlValidator TOKENS = new TokenConfigYamlValidator();

  private YamlConfigurationValidation() {
  }

  public static List<Diagnostic> validate(Format format, String yaml, boolean activatable) {
    try {
      StrictYaml.validate(yaml);
      List<String> errors = switch (format) {
      case FEES -> FEES.validate(yaml);
      case FEES_ACCOUNT -> FEES.validate(yaml, true);
      case TOKENS -> TOKENS.validate(yaml);
      case CALENDAR -> CALENDAR.validate(yaml);
      case TAXES -> yaml == null || yaml.isBlank() ? List.of() : TAXES.validate(yaml);
      case CUSTODY -> CustodyOpeningYamlValidator.validate(yaml);
      case STRATEGY -> {
        if ((yaml == null || yaml.isBlank()) && !activatable)
          yield List.of();
        var json = new JsonMapper().writeValueAsString(new YAMLMapper().readTree(yaml));
        if (activatable)
          StrategyConfigValidator.executable(json);
        else
          StrategyConfigValidator.parseAndValidate(json, new JsonMapper());
        yield List.of();
      }
      };
      return errors.stream()
          .map(error -> new Diagnostic(error.startsWith("Schema:") ? "SCHEMA" : "DOMAIN", error, null, null, null))
          .toList();
    } catch (StrictYaml.InvalidYamlException e) {
      return List.of(new Diagnostic("SYNTAX", e.getMessage(), null, e.line, e.column));
    } catch (RuntimeException e) {
      return List
          .of(new Diagnostic("DOMAIN", "Invalid " + format.name().toLowerCase() + " configuration", null, null, null));
    }
  }

  public static void requireValid(Format format, String yaml, String field) {
    var errors = validate(format, yaml, true);
    if (!errors.isEmpty())
      throw new DataViolationException(field, "gt.yaml.invalid",
          new Object[] { errors.stream().map(Diagnostic::message).collect(java.util.stream.Collectors.joining("; ")) });
  }
}
