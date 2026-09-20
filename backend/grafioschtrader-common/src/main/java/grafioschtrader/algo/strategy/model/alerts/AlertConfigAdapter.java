package grafioschtrader.algo.strategy.model.alerts;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import grafiosch.common.ValueFormatConverter;
import grafiosch.entities.BaseParam;
import grafioschtrader.algo.strategy.model.StrategyClassBindingDefinition;
import grafioschtrader.algo.strategy.model.StrategyHelper;
import grafioschtrader.entities.AlgoRuleStrategy;
import grafioschtrader.entities.AlgoStrategy;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.ValidationException;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import tools.jackson.databind.ObjectMapper;

/**
 * Turns the stored configuration of a simple or indicator alert into its typed model, whichever of the two persisted
 * forms it happens to be in.
 *
 * <p>
 * There are two, and the evaluation used to read only one of them. The edit dialog writes the flat typed parameters of
 * {@code algoRuleStrategyParamMap} for every alert whose form is generated from the model class, and leaves
 * {@code strategyConfig} null; only a complex strategy edited as YAML writes JSON. The alarm service read
 * {@code strategyConfig} exclusively and returned early when it was null, so no alert created through the normal form
 * was ever evaluated. This adapter is the single place that resolves the question, and both evaluation tiers as well as
 * the save path go through it, so the configuration that is validated is the configuration that is evaluated.
 * </p>
 *
 * <p>
 * The flat map is authoritative whenever it holds anything: it is what the form last wrote. JSON is the fallback, for a
 * complex strategy and for an alert saved before the form existed. When both are present the flat values win and the
 * JSON is reported through {@link #describeAmbiguity(AlgoStrategy)} rather than silently evaluated.
 * </p>
 */
public abstract class AlertConfigAdapter {

  private static final ValidatorFactory VALIDATOR_FACTORY = Validation.buildDefaultValidatorFactory();

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  /**
   * Numbers arrive from the browser as JSON, so their decimal separator is always a point and they carry no grouping
   * separator. The no-argument converter would parse them with the server's default locale, where a German or Swiss
   * server reads 120.5 as one thousand two hundred and five without raising anything. The separators are therefore
   * pinned rather than inherited.
   *
   * <p>
   * A fresh converter per call rather than a shared one: it wraps a {@link java.text.NumberFormat}, which is not thread
   * safe, and the two evaluation tiers can run at the same time.
   * </p>
   *
   * @return a converter that reads the canonical JSON number format
   */
  private static ValueFormatConverter numberConverter() {
    return new ValueFormatConverter('.', ',');
  }

  /**
   * Reads the configuration of a strategy into the model class bound to its implementation type.
   *
   * @param strategy the strategy whose configuration is wanted
   * @param <T>      the bound model type
   * @return the populated model, or null when the strategy carries no configuration at all or its implementation type
   *         has no model class bound to the security level
   * @throws ValidationException if the stored values do not satisfy the model's Bean Validation constraints, or a value
   *                             cannot be converted into its field type
   */
  public static <T> T read(AlgoStrategy strategy) {
    @SuppressWarnings("unchecked")
    Class<T> modelClass = (Class<T>) securityLevelModelClass(strategy);
    return modelClass == null ? null : read(strategy, modelClass);
  }

  /**
   * Reads the configuration of a strategy into an explicitly given model class, for a caller that already knows the
   * type it wants.
   *
   * @param strategy   the strategy whose configuration is wanted
   * @param modelClass the model to populate
   * @param <T>        the model type
   * @return the populated model, or null when the strategy carries no configuration at all
   * @throws ValidationException if the stored values are invalid or unconvertible
   */
  public static <T> T read(AlgoStrategy strategy, Class<T> modelClass) {
    Map<String, AlgoRuleStrategy.AlgoRuleStrategyParam> params = strategy.getAlgoRuleStrategyParamMap();
    T model;
    if (hasAnyValue(params)) {
      model = fromFlatParams(params, modelClass);
    } else if (strategy.getStrategyConfig() != null && !strategy.getStrategyConfig().isBlank()) {
      model = fromJson(strategy.getStrategyConfig(), modelClass);
    } else {
      return null;
    }
    validate(model);
    return model;
  }

  /**
   * Reports a strategy that carries both persisted forms. The flat parameters are the ones evaluated; the JSON is stale
   * and, when it differs, describes an alert the user is no longer looking at.
   *
   * @param strategy the strategy to inspect
   * @return a message naming the strategy and both forms, or null when only one form is present
   */
  public static String describeAmbiguity(AlgoStrategy strategy) {
    if (hasAnyValue(strategy.getAlgoRuleStrategyParamMap()) && strategy.getStrategyConfig() != null
        && !strategy.getStrategyConfig().isBlank()) {
      return "Strategy " + strategy.getIdAlgoRuleStrategy() + " carries both flat parameters "
          + flatValues(strategy.getAlgoRuleStrategyParamMap()) + " and the JSON " + strategy.getStrategyConfig()
          + ". The flat parameters are evaluated.";
    }
    return null;
  }

  /**
   * Identity of the configuration a crossing baseline was observed under. A stored baseline whose fingerprint differs
   * from the current one describes a bound that no longer exists in that form, so the next evaluation starts over
   * instead of reporting a crossing that never happened under the configuration now in force.
   *
   * <p>
   * Deactivation is deliberately not part of it. An alert that is switched off has its baselines removed rather than
   * fingerprinted differently, which is what keeps a price move during the disabled interval from being reported as a
   * crossing when the alert comes back, and which also covers a deactivated ancestor without a second mechanism.
   * </p>
   *
   * @param strategy the strategy whose configuration identifies the baseline
   * @return a hex encoded digest of 64 characters
   */
  public static String fingerprint(AlgoStrategy strategy) {
    StringBuilder material = new StringBuilder();
    material.append(strategy.getAlgoStrategyImplementations()).append('|');
    if (hasAnyValue(strategy.getAlgoRuleStrategyParamMap())) {
      material.append(flatValues(strategy.getAlgoRuleStrategyParamMap()));
    } else if (strategy.getStrategyConfig() != null) {
      material.append(strategy.getStrategyConfig());
    }
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(material.toString().getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (Exception e) {
      throw new IllegalStateException("SHA-256 is required of every Java platform", e);
    }
  }

  /**
   * The model class the security level of a strategy implementation type is bound to.
   *
   * @param strategy the strategy whose implementation type is resolved
   * @return the bound class, or null for an implementation type without a security level model
   */
  public static Class<?> securityLevelModelClass(AlgoStrategy strategy) {
    StrategyClassBindingDefinition binding = StrategyHelper.getStrategyBindingMap()
        .get(strategy.getAlgoStrategyImplementations());
    return binding == null ? null : binding.algoSecurityModel;
  }

  private static boolean hasAnyValue(Map<String, AlgoRuleStrategy.AlgoRuleStrategyParam> params) {
    return params != null && params.values().stream().anyMatch(p -> p != null && isSet(p.getParamValue()));
  }

  private static boolean isSet(String value) {
    return value != null && !value.isBlank() && !"null".equals(value);
  }

  private static String flatValues(Map<String, AlgoRuleStrategy.AlgoRuleStrategyParam> params) {
    Map<String, String> sorted = new TreeMap<>();
    params.forEach((name, param) -> {
      if (param != null && isSet(param.getParamValue())) {
        sorted.put(name, param.getParamValue());
      }
    });
    return sorted.toString();
  }

  private static <T> T fromFlatParams(Map<String, ? extends BaseParam> params, Class<T> modelClass) {
    T model = instantiate(modelClass);
    ValueFormatConverter converter = numberConverter();
    Map<String, Class<?>> dataTypes = ValueFormatConverter.getDataTypeOfPropertiesByBean(model);
    for (Map.Entry<String, ? extends BaseParam> entry : params.entrySet()) {
      String fieldName = entry.getKey();
      BaseParam param = entry.getValue();
      if (param == null || !isSet(param.getParamValue())) {
        // A field the user left empty. It stays null, which is how every optional bound is expressed.
        continue;
      }
      Class<?> dataType = dataTypes.get(fieldName);
      if (dataType == null) {
        // A parameter of an older configuration whose field the model no longer has. Skipping it is right: the
        // remaining fields still describe the alert, and the model's own constraints decide whether they describe a
        // complete one.
        continue;
      }
      try {
        validateRebalancingNumber(modelClass, fieldName, param.getParamValue());
        converter.convertAndSetValue(model, fieldName, param.getParamValue(), dataType);
      } catch (Exception e) {
        throw new ValidationException("Parameter " + fieldName + " of " + modelClass.getSimpleName()
            + " holds the unusable value " + param.getParamValue(), e);
      }
    }
    return model;
  }

  private static <T> T fromJson(String json, Class<T> modelClass) {
    try {
      if (modelClass == grafioschtrader.algo.strategy.model.rebalacing.RebalancingTop.class) {
        var tree = OBJECT_MAPPER.readTree(json);
        for (String name : new String[] { "securityDeviationPercentage", "maxTradedSecuritiesPerAssetclass" }) {
          var value = tree.get(name);
          if (value != null && !value.isNull())
            validateRebalancingNumber(modelClass, name, value.toString());
        }
      }
      return OBJECT_MAPPER.readValue(json, modelClass);
    } catch (Exception e) {
      throw new ValidationException(
          "Stored JSON configuration is not a valid " + modelClass.getSimpleName() + ": " + e.getMessage(), e);
    }
  }

  /** The generic number converter truncates integer inputs and accepts numeric prefixes; allocation limits must not. */
  private static void validateRebalancingNumber(Class<?> modelClass, String field, String value) {
    if (modelClass != grafioschtrader.algo.strategy.model.rebalacing.RebalancingTop.class)
      return;
    if ("maxTradedSecuritiesPerAssetclass".equals(field)) {
      Integer.parseInt(value);
    } else if ("securityDeviationPercentage".equals(field) && !Double.isFinite(Double.parseDouble(value))) {
      throw new ValidationException("Security allocation band must be finite");
    }
  }

  private static <T> T instantiate(Class<T> modelClass) {
    try {
      return modelClass.getDeclaredConstructor().newInstance();
    } catch (Exception e) {
      throw new IllegalStateException(modelClass.getName() + " needs a public no-argument constructor", e);
    }
  }

  private static <T> void validate(T model) {
    switch (model) {
    case AbsoluteValuePriceAlert a -> bounds(a.getLowerValue(), a.getUpperValue());
    case RsiThresholdAlert a -> bounds(a.getLowerThreshold(), a.getUpperThreshold());
    case HoldingGainLosePercentAlert a -> {
      requireAny(a.getGainPercentage(), a.getLosePercentage(), a.getLowerValue(), a.getUpperValue());
      if (a.getLowerValue() != null || a.getUpperValue() != null)
        bounds(a.getLowerValue(), a.getUpperValue());
    }
    case PeriodPriceGainLosePercentAlert a -> {
      requireAny(a.getGainPercentage(), a.getLosePercentage());
      if (a.getDaysInPeriod() == null)
        throw new ValidationException("Lookback period is required");
    }
    case ExpressionAlert a -> {
      if (a.getExpression() == null || a.getExpression().isBlank())
        throw new ValidationException("Expression is required");
    }
    default -> {
    }
    }
    Validator validator = VALIDATOR_FACTORY.getValidator();
    Set<ConstraintViolation<T>> violations = validator.validate(model);
    if (!violations.isEmpty()) {
      StringBuilder message = new StringBuilder("Invalid ").append(model.getClass().getSimpleName()).append(':');
      violations.forEach(v -> message.append(' ').append(v.getPropertyPath()).append(' ').append(v.getMessage()));
      throw new ValidationException(message.toString());
    }
  }

  private static void requireAny(Number... values) {
    if (java.util.Arrays.stream(values).allMatch(java.util.Objects::isNull))
      throw new ValidationException("At least one threshold is required");
  }

  private static void bounds(Number lower, Number upper) {
    requireAny(lower, upper);
    if (lower != null && !Double.isFinite(lower.doubleValue())
        || upper != null && !Double.isFinite(upper.doubleValue()))
      throw new ValidationException("Thresholds must be finite");
    if (lower != null && upper != null && lower.doubleValue() >= upper.doubleValue())
      throw new ValidationException("Lower threshold must be below upper threshold");
  }

}
