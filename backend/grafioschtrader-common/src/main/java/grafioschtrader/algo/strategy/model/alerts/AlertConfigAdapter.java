package grafioschtrader.algo.strategy.model.alerts;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

import org.hibernate.validator.messageinterpolation.ResourceBundleMessageInterpolator;
import org.hibernate.validator.resourceloading.PlatformResourceBundleLocator;

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
import tools.jackson.databind.JsonNode;
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

  /**
   * Resolves the messages of the class-level constraints, such as {@code {at.least.one.not.null}}, from the bundle of
   * grafiosch-base; a constraint whose key is not there falls back to the texts of the validator itself.
   */
  private static final ValidatorFactory VALIDATOR_FACTORY = Validation.byDefaultProvider().configure()
      .messageInterpolator(new ResourceBundleMessageInterpolator(new PlatformResourceBundleLocator("i18n/messages")))
      .buildValidatorFactory();

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private static final Set<Class<?>> INTEGRAL_TYPES = Set.of(Integer.class, int.class, Long.class, long.class,
      Short.class, short.class);

  /** A whole number; "5.0" is still five and is accepted, only a fraction that would be cut off is not. */
  private static final Pattern INTEGER_PATTERN = Pattern.compile("-?\\d+(\\.0+)?");

  private static final Pattern DECIMAL_PATTERN = Pattern.compile("-?\\d+(\\.\\d+)?([eE][+-]?\\d+)?");

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

  /**
   * Removes the parameters of the fields the user left empty. The form sends every field of the model, an empty one
   * without a value, but the parameter table cannot hold a missing value; an absent parameter is how an optional bound
   * is expressed, and {@link #read(AlgoStrategy, Class)} reads it as null either way.
   *
   * @param strategy the strategy whose flat parameters are cleaned in place
   */
  public static void removeUnsetParams(AlgoRuleStrategy strategy) {
    Map<String, AlgoRuleStrategy.AlgoRuleStrategyParam> params = strategy.getAlgoRuleStrategyParamMap();
    if (params != null) {
      params.values().removeIf(p -> p == null || !isSet(p.getParamValue()));
    }
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
        requireCanonicalNumber(dataType, fieldName, param.getParamValue());
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
      JsonNode tree = OBJECT_MAPPER.readTree(json);
      requireWholeNumbers(tree, modelClass);
      return OBJECT_MAPPER.treeToValue(tree, modelClass);
    } catch (Exception e) {
      throw new ValidationException(
          "Stored JSON configuration is not a valid " + modelClass.getSimpleName() + ": " + e.getMessage(), e);
    }
  }

  /**
   * The JSON counterpart of {@link #requireCanonicalNumber(Class, String, String)} for integral fields. Jackson
   * truncates a fraction into an integral field, so 1.5 securities per class would be read as 1; 5.0 is whole and
   * passes, exactly as it does in the flat parameters.
   *
   * @param tree       the parsed configuration
   * @param modelClass the model the configuration is read into
   * @throws ValidationException if an integral field holds a fraction
   */
  private static void requireWholeNumbers(JsonNode tree, Class<?> modelClass) {
    for (Field field : modelClass.getDeclaredFields()) {
      JsonNode node = INTEGRAL_TYPES.contains(field.getType()) ? tree.get(field.getName()) : null;
      if (node != null && node.isFloatingPointNumber() && node.doubleValue() != Math.rint(node.doubleValue())) {
        throw new ValidationException(field.getName() + " must be a whole number, not " + node);
      }
    }
  }

  private static <T> T instantiate(Class<T> modelClass) {
    try {
      return modelClass.getDeclaredConstructor().newInstance();
    } catch (Exception e) {
      throw new IllegalStateException(modelClass.getName() + " needs a public no-argument constructor", e);
    }
  }

  /**
   * Checks the model against its Bean Validation constraints, the class-level ones included. Which thresholds are
   * required and in which order they must lie is declared on the model with {@code @AtLeastOneNotNull} and
   * {@code @NumberRange}, the same annotations the edit form is generated from, so the form and this check cannot drift
   * apart. Only what no form can produce is checked here by hand.
   */
  private static <T> void validate(T model) {
    requireFiniteNumbers(model);
    Validator validator = VALIDATOR_FACTORY.getValidator();
    Set<ConstraintViolation<T>> violations = validator.validate(model);
    if (!violations.isEmpty()) {
      StringBuilder message = new StringBuilder("Invalid ").append(model.getClass().getSimpleName()).append(':');
      violations.forEach(v -> {
        String path = v.getPropertyPath().toString();
        message.append(' ').append(path.isEmpty() ? "" : path + " ").append(v.getMessage());
      });
      throw new ValidationException(message.toString());
    }
  }

  /**
   * Rejects NaN and infinity in every floating point field of the model. Positive infinity satisfies {@code @Min}, and
   * NaN is no bound a price can be compared with. Neither can come from the form; a JSON exponent beyond the double
   * range turns into infinity on the way in.
   *
   * @param model the populated model
   */
  private static void requireFiniteNumbers(Object model) {
    for (Field field : model.getClass().getDeclaredFields()) {
      if (Modifier.isStatic(field.getModifiers())
          || field.getType() != Double.class && field.getType() != double.class) {
        continue;
      }
      try {
        field.setAccessible(true);
        Double value = (Double) field.get(model);
        if (value != null && !Double.isFinite(value)) {
          throw new ValidationException(
              field.getName() + " of " + model.getClass().getSimpleName() + " must be finite");
        }
      } catch (IllegalAccessException e) {
        throw new IllegalStateException(e);
      }
    }
  }

  /**
   * Accepts a stored number only in the form the browser writes it: digits, an optional fraction and exponent for a
   * floating point field, a whole number for an integral one. The number converter alone is lenient in two ways that
   * would otherwise go unnoticed: it parses the longest numeric prefix, so "5garbage" became 5, and it truncates, so
   * 1.5 days or securities became 1.
   *
   * @param dataType  the type of the model field
   * @param fieldName the field, for the message
   * @param value     the stored parameter value
   * @throws ValidationException if the value is not a number of that type
   */
  private static void requireCanonicalNumber(Class<?> dataType, String fieldName, String value) {
    String trimmed = value.trim();
    if (INTEGRAL_TYPES.contains(dataType) && !INTEGER_PATTERN.matcher(trimmed).matches()) {
      throw new ValidationException(fieldName + " must be a whole number, not " + value);
    }
    if ((dataType == Double.class || dataType == double.class) && !DECIMAL_PATTERN.matcher(trimmed).matches()) {
      throw new ValidationException(fieldName + " must be a number, not " + value);
    }
  }

}
