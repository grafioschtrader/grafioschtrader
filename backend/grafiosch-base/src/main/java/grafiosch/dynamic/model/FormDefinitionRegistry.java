package grafiosch.dynamic.model;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Allow-list of entity classes whose dynamic form definition may be requested over REST. The
 * generic form-definition endpoint resolves a client supplied entity name against this registry
 * instead of reflecting an arbitrary class name, which would be a security risk.
 *
 * <p>The registry lives in the reusable {@code grafiosch-base} layer; the concrete entity classes
 * are registered by the application layer at startup (mirroring {@code UDFData.UDF_GENERAL_ENTITIES}).
 * Only entities that annotate their input fields with {@code @DynamicFormField} should be
 * registered.</p>
 */
public abstract class FormDefinitionRegistry {

  private static final Map<String, Class<?>> ENTITIES = new ConcurrentHashMap<>();

  /**
   * Registers an entity class under its simple name so its form definition can be served.
   *
   * @param entityClass the entity exposing {@code @DynamicFormField} annotated input fields
   */
  public static void register(Class<?> entityClass) {
    ENTITIES.put(entityClass.getSimpleName(), entityClass);
  }

  /**
   * Resolves a registered entity name to its class.
   *
   * @param entityName the simple name of the entity
   * @return the registered class, or null when the name is not allow-listed
   */
  public static Class<?> resolve(String entityName) {
    return ENTITIES.get(entityName);
  }
}
