package grafioschtrader.repository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.security.core.context.SecurityContextHolder;

import grafioschtrader.dto.MaxDefaultDBValueWithKey;
import grafioschtrader.dto.TenantLimit;
import grafioschtrader.entities.BaseID;
import grafioschtrader.entities.Cashaccount;
import grafioschtrader.entities.CorrelationSet;
import grafioschtrader.entities.Globalparameters;
import grafioschtrader.entities.Portfolio;
import grafioschtrader.entities.Securityaccount;
import grafioschtrader.entities.User;
import grafioschtrader.entities.Watchlist;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

public abstract class TenantLimitsHelper {

  private static final Map<String, Class<?>> globalLimitKeyToEntityMap = new HashMap<>();

  static {
    /**
     * Put only entities in here which can be checked when added with "SELECT
     * count() FROM ..."
     */
    globalLimitKeyToEntityMap.put(Globalparameters.GLOB_KEY_MAX_CASH_ACCOUNT, Cashaccount.class);
    globalLimitKeyToEntityMap.put(Globalparameters.GLOB_KEY_MAX_CORRELATION_SET, CorrelationSet.class);
    globalLimitKeyToEntityMap.put(Globalparameters.GLOB_KEY_MAX_PORTFOLIO, Portfolio.class);
    globalLimitKeyToEntityMap.put(Globalparameters.GLOB_KEY_MAX_SECURITY_ACCOUNT, Securityaccount.class);
    globalLimitKeyToEntityMap.put(Globalparameters.GLOB_KEY_MAX_WATCHTLIST, Watchlist.class);
  }

  public static <T extends BaseID> boolean canAddWhenCheckedAgainstMayBeExistingTenantLimit(
      final GlobalparametersJpaRepository globalparametersJpaRepository, final T entityClass) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    Optional<String> keyOptional = globalLimitKeyToEntityMap.entrySet().stream()
        .filter(map -> map.getValue() == entityClass.getClass()).map(map -> map.getKey()).findFirst();
    if (keyOptional.isPresent()) {
      return (countUsersEntityLimit(globalparametersJpaRepository.getEntityManager(), keyOptional.get(),
          user) < getMaxValueByKey(globalparametersJpaRepository, keyOptional.get()));
    } else {
      return true;
    }

  }

  public static TenantLimit getMaxTenantLimitsByKey(GlobalparametersJpaRepository globalparametersJpaRepository,
      String key) {

    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    Class<?> entityClass = globalLimitKeyToEntityMap.get(key);
    return new TenantLimit(getMaxValueByKey(globalparametersJpaRepository, key),
        countUsersEntityLimit(globalparametersJpaRepository.getEntityManager(), key, user), key,
        entityClass != null ? entityClass.getSimpleName() : null);
  }

  public static List<TenantLimit> getMaxTenantLimitsByKeys(GlobalparametersJpaRepository globalparametersJpaRepository,
      List<String> keys) {
    List<TenantLimit> tenantLimits = new ArrayList<>();
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    keys.forEach(key -> {
      String className = globalLimitKeyToEntityMap.get(key).getSimpleName();
      tenantLimits.add(new TenantLimit(getMaxValueByKey(globalparametersJpaRepository, key),
          countUsersEntityLimit(globalparametersJpaRepository.getEntityManager(), key, user), key, className));
    });
    return tenantLimits;
  }

  public static List<TenantLimit> getMaxTenantLimitsByMsgKeys(
      GlobalparametersJpaRepository globalparametersJpaRepository, List<String> msgKeys) {
    List<TenantLimit> tenantLimits = new ArrayList<>();
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    msgKeys.forEach(msgKey -> {
      String key = Globalparameters.getKeyFromMsgKey(msgKey);
      String className = globalLimitKeyToEntityMap.get(key).getSimpleName();
      tenantLimits.add(new TenantLimit(msgKey, getMaxValueByKey(globalparametersJpaRepository, key),
          countUsersEntityLimit(globalparametersJpaRepository.getEntityManager(), key, user), className));
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

  public static int getMaxValueByKey(GlobalparametersJpaRepository globalparametersJpaRepository, String key) {
    return getMaxValueByMaxDefaultDBValueWithKey(globalparametersJpaRepository,
        Globalparameters.getMaxDefaultDBValueByKey(key));
  }

  private static int getMaxValueByMaxDefaultDBValueWithKey(GlobalparametersJpaRepository globalparametersJpaRepository,
      MaxDefaultDBValueWithKey maxDefaultDBValueWithKey) {
    if (maxDefaultDBValueWithKey.maxDefaultDBValue.getDbValue() == null) {
      maxDefaultDBValueWithKey.maxDefaultDBValue.setDbValue(
          globalparametersJpaRepository.findById(maxDefaultDBValueWithKey.key).map(Globalparameters::getPropertyInt)
              .orElse(maxDefaultDBValueWithKey.maxDefaultDBValue.getDefaultValue()));
    }
    return maxDefaultDBValueWithKey.maxDefaultDBValue.getDbValue();
  }

  public static int getMaxValueByMsgKey(GlobalparametersJpaRepository globalparametersJpaRepository, String msgKey) {
    return getMaxValueByMaxDefaultDBValueWithKey(globalparametersJpaRepository,
        Globalparameters.getMaxDefaultDBValueByMsgKey(msgKey));
  }

}
