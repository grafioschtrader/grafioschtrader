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

public abstract class TenantLimitsHelper {

  public static final Map<String, Class<?>> globalLimitKeyToEntityMap = new HashMap<>();
  

  public static <T extends BaseID<Integer>> boolean canAddWhenCheckedAgainstMayBeExistingTenantLimit(
      final EntityManager entityManager, final T entityClass) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    Optional<String> keyOptional = globalLimitKeyToEntityMap.entrySet().stream()
        .filter(map -> map.getValue() == entityClass.getClass()).map(map -> map.getKey()).findFirst();
    if (keyOptional.isPresent()) {
      return (countUsersEntityLimit(entityManager, keyOptional.get(),
          user) < getMaxValueByKey(entityManager, keyOptional.get()));
    } else {
      return true;
    }

  }

  public static TenantLimit getMaxTenantLimitsByKey(EntityManager entityManager,
      String key) {

    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    Class<?> entityClass = globalLimitKeyToEntityMap.get(key);
    return new TenantLimit(getMaxValueByKey(entityManager, key),
        countUsersEntityLimit(entityManager, key, user), key,
        entityClass != null ? entityClass.getSimpleName() : null);
  }

  public static List<TenantLimit> getMaxTenantLimitsByKeys(EntityManager entityManager,
      List<String> keys) {
    List<TenantLimit> tenantLimits = new ArrayList<>();
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    keys.forEach(key -> {
      String className = globalLimitKeyToEntityMap.get(key).getSimpleName();
      tenantLimits.add(new TenantLimit(getMaxValueByKey(entityManager, key),
          countUsersEntityLimit(entityManager, key, user), key, className));
    });
    return tenantLimits;
  }

  public static List<TenantLimit> getMaxTenantLimitsByMsgKeys(
      EntityManager entityManager, List<String> msgKeys) {
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

  private static int countUsersEntityLimit(EntityManager entityManager, String key, User user) {
    String className = globalLimitKeyToEntityMap.get(key).getSimpleName();

    final TypedQuery<Long> query = entityManager
        .createQuery("SELECT count(t) FROM " + className + " t WHERE t.idTenant = ?1", Long.class)
        .setParameter(1, user.getIdTenant());
    return query.getSingleResult().intValue();
  }

  public static int getMaxValueByKey(EntityManager entityManager, String key) {
    return getMaxValueByMaxDefaultDBValueWithKey(entityManager,
        Globalparameters.getMaxDefaultDBValueByKey(key));
  }

  private static int getMaxValueByMaxDefaultDBValueWithKey(EntityManager entityManager,
      MaxDefaultDBValueWithKey maxDefaultDBValueWithKey) {
    if (maxDefaultDBValueWithKey.maxDefaultDBValue.getDbValue() == null) {
      Globalparameters globalParameter = entityManager.find(Globalparameters.class, maxDefaultDBValueWithKey.key);
      int value = (globalParameter != null)
          ? globalParameter.getPropertyInt()
          : maxDefaultDBValueWithKey.maxDefaultDBValue.getDefaultValue();
      maxDefaultDBValueWithKey.maxDefaultDBValue.setDbValue(value);
    }
    return maxDefaultDBValueWithKey.maxDefaultDBValue.getDbValue();
  }

  public static int getMaxValueByMsgKey(EntityManager entityManager, String msgKey) {
    return getMaxValueByMaxDefaultDBValueWithKey(entityManager,
        Globalparameters.getMaxDefaultDBValueByMsgKey(BaseConstants.GT_PREFIX + msgKey));
  }

}
