package grafiosch.repository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.security.core.context.SecurityContextHolder;

import grafiosch.BaseConstants;
import grafiosch.dto.MaxDefaultDBValueWithKey;
import grafiosch.dto.TenantLimit;
import grafiosch.entities.BaseID;
import grafiosch.entities.Globalparameters;
import grafiosch.entities.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

/**
 * Utility class for managing and checking tenant-specific entity limits.
 */
public abstract class TenantLimitsHelper {

  /**
   * Maps global parameter keys to their corresponding entity classes.
   * 
   * <p>
   * Parameter keys follow the pattern "gt.limit.day.{EntityClassName}" where:
   * <ul>
   * <li>"gt." is the global prefix (BaseConstants.GT_PREFIX)</li>
   * <li>"limit.day." indicates daily entity creation limits</li>
   * <li>"{EntityClassName}" is the simple name of the entity class (e.g., "MailSendRecv", "MailSettingForward",
   * "UDFMetadataGeneral")</li>
   * </ul>
   * 
   * <p>
   * Examples:
   * <ul>
   * <li>"gt.limit.day.MailSendRecv" - limits daily mail send/receive operations</li>
   * </ul>
   */
  public static final Map<String, Class<?>> globalLimitKeyToEntityMap = new HashMap<>();

  /**
   * Checks whether a new entity can be added based on existing tenant limits.
   * 
   * @param <T>           the entity type extending BaseID with Integer primary key
   * @param entityManager the JPA EntityManager for database operations
   * @param entityClass   the entity instance to check limits for
   * @return true if the entity can be added without exceeding limits
   */
  public static <T extends BaseID<Integer>> boolean canAddWhenCheckedAgainstMayBeExistingTenantLimit(
      final EntityManager entityManager, final T entityClass) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    Optional<String> keyOptional = globalLimitKeyToEntityMap.entrySet().stream()
        .filter(map -> map.getValue() == entityClass.getClass()).map(map -> map.getKey()).findFirst();
    if (keyOptional.isPresent()) {
      return (countUsersEntityLimit(entityManager, keyOptional.get(), user) < getMaxValueByKey(entityManager,
          keyOptional.get()));
    } else {
      return true;
    }

  }

  /**
   * Retrieves tenant limit information for a specific limit key.
   * 
   * @param entityManager the JPA EntityManager for database operations
   * @param key           the global parameter key identifying the limit (e.g., "gt.limit.day.MailSendRecv",
   *                      "gt.limit.day.MailSettingForward")
   * @return a TenantLimit object containing limit and usage information
   */
  public static TenantLimit getMaxTenantLimitsByKey(EntityManager entityManager, String key) {

    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    Class<?> entityClass = globalLimitKeyToEntityMap.get(key);
    return new TenantLimit(getMaxValueByKey(entityManager, key), countUsersEntityLimit(entityManager, key, user), key,
        entityClass != null ? entityClass.getSimpleName() : null);
  }

  /**
   * Retrieves tenant limit information for multiple limit keys.
   * 
   * @param entityManager the JPA EntityManager for database operations
   * @param keys          list of global parameter keys to retrieve limits for (format:
   *                      "gt.limit.day.{EntityClassName}")
   * @return a list of TenantLimit objects, one for each provided key
   */
  public static List<TenantLimit> getMaxTenantLimitsByKeys(EntityManager entityManager, List<String> keys) {
    List<TenantLimit> tenantLimits = new ArrayList<>();
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    keys.forEach(key -> {
      String className = globalLimitKeyToEntityMap.get(key).getSimpleName();
      tenantLimits.add(new TenantLimit(getMaxValueByKey(entityManager, key),
          countUsersEntityLimit(entityManager, key, user), key, className));
    });
    return tenantLimits;
  }

  /**
   * Retrieves tenant limit information using message keys instead of direct parameter keys.
   * 
   * <p>
   * Message keys are converted to parameter keys by:
   * <ol>
   * <li>Adding GT_PREFIX ("gt.") as prefix</li>
   * <li>Converting to lowercase</li>
   * <li>Replacing underscores with dots</li>
   * </ol>
   * 
   * <p>
   * Example: "LIMIT_DAY_MAILSENDRECV" becomes "gt.limit.day.mailsendrecv"
   * 
   * @param entityManager the JPA EntityManager for database operations
   * @param msgKeys       list of message keys to retrieve limits for (format: "LIMIT_DAY_{ENTITYNAME}")
   * @return a list of TenantLimit objects with message keys preserved in the response
   */
  public static List<TenantLimit> getMaxTenantLimitsByMsgKeys(EntityManager entityManager, List<String> msgKeys) {
    List<TenantLimit> tenantLimits = new ArrayList<>();
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    msgKeys.forEach(msgKey -> {
      String key = Globalparameters.getKeyFromMsgKey(BaseConstants.GT_PREFIX + msgKey);
      String className = globalLimitKeyToEntityMap.get(key).getSimpleName();
      tenantLimits.add(new TenantLimit(msgKey, getMaxValueByKey(entityManager, key),
          countUsersEntityLimit(entityManager, key, user), className));
    });
    return tenantLimits;
  }

  /**
   * Counts the number of entities of a specific type that exist for the user's tenant.
   * 
   * @param entityManager the JPA EntityManager for database operations
   * @param key           the global parameter key identifying the entity type (format:
   *                      "gt.limit.day.{EntityClassName}")
   * @param user          the user whose tenant's entities should be counted
   * @return the count of entities for the tenant
   */
  private static int countUsersEntityLimit(EntityManager entityManager, String key, User user) {
    String className = globalLimitKeyToEntityMap.get(key).getSimpleName();

    final TypedQuery<Long> query = entityManager
        .createQuery("SELECT count(t) FROM " + className + " t WHERE t.idTenant = ?1", Long.class)
        .setParameter(1, user.getIdTenant());
    return query.getSingleResult().intValue();
  }

  /**
   * Retrieves the maximum allowed value for a specific limit key.
   * 
   * @param entityManager the JPA EntityManager for database operations
   * @param key           the global parameter key to retrieve the maximum value for (format:
   *                      "gt.limit.day.{EntityClassName}")
   * @return the maximum allowed value for the limit
   */
  public static int getMaxValueByKey(EntityManager entityManager, String key) {
    return getMaxValueByMaxDefaultDBValueWithKey(entityManager, Globalparameters.getMaxDefaultDBValueByKey(key));
  }

  /**
   * Retrieves the actual maximum value, checking database overrides and falling back to defaults.
   * 
   * @param entityManager            the JPA EntityManager for database operations
   * @param maxDefaultDBValueWithKey wrapper containing the key and default/cached value
   * @return the effective maximum value (either from database override or default)
   */
  private static int getMaxValueByMaxDefaultDBValueWithKey(EntityManager entityManager,
      MaxDefaultDBValueWithKey maxDefaultDBValueWithKey) {
    if (maxDefaultDBValueWithKey.maxDefaultDBValue.getDbValue() == null) {
      Globalparameters globalParameter = entityManager.find(Globalparameters.class, maxDefaultDBValueWithKey.key);
      int value = (globalParameter != null) ? globalParameter.getPropertyInt()
          : maxDefaultDBValueWithKey.maxDefaultDBValue.getDefaultValue();
      maxDefaultDBValueWithKey.maxDefaultDBValue.setDbValue(value);
    }
    return maxDefaultDBValueWithKey.maxDefaultDBValue.getDbValue();
  }

  /**
   * Retrieves the maximum allowed value using a message key.
   * 
   * @param entityManager the JPA EntityManager for database operations
   * @param msgKey        the message key to convert and lookup
   * @return the maximum allowed value for the limit
   */
  public static int getMaxValueByMsgKey(EntityManager entityManager, String msgKey) {
    return getMaxValueByMaxDefaultDBValueWithKey(entityManager,
        Globalparameters.getMaxDefaultDBValueByMsgKey(BaseConstants.GT_PREFIX + msgKey));
  }

}
