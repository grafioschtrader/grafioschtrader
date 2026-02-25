package grafioschtrader.algo.strategy.model.complex;

import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.ValidationException;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

/**
 * Utility for parsing and validating a {@link StrategyConfig} from JSON. Uses Jakarta Bean Validation to cascade
 * through the entire nested configuration tree.
 */
public class StrategyConfigValidator {

  private static final ValidatorFactory factory = Validation.buildDefaultValidatorFactory();

  /**
   * Validates a {@link StrategyConfig} instance against all Jakarta Bean Validation annotations.
   *
   * @param config the strategy configuration to validate
   * @return set of constraint violations, empty if valid
   */
  public static Set<ConstraintViolation<StrategyConfig>> validate(StrategyConfig config) {
    Validator validator = factory.getValidator();
    return validator.validate(config);
  }

  /**
   * Parses a JSON string into a {@link StrategyConfig} and validates it. Throws a {@link ValidationException} if any
   * constraint violations are found.
   *
   * @param json   the JSON string to parse
   * @param mapper the Jackson ObjectMapper to use for deserialization
   * @return the validated StrategyConfig
   * @throws Exception if JSON parsing fails or validation violations are found
   */
  public static StrategyConfig parseAndValidate(String json, ObjectMapper mapper) throws Exception {
    StrategyConfig config = mapper.readValue(json, StrategyConfig.class);
    Set<ConstraintViolation<StrategyConfig>> violations = validate(config);
    if (!violations.isEmpty()) {
      String messages = violations.stream()
          .map(v -> v.getPropertyPath() + ": " + v.getMessage())
          .collect(Collectors.joining("; "));
      throw new ValidationException("Invalid strategy config: " + messages);
    }
    return config;
  }
}
