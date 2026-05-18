package grafioschtrader.repository;

import java.lang.annotation.Annotation;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;

import grafiosch.exceptions.DataViolationException;
import grafiosch.repository.BaseRepositoryCustom;
import grafiosch.repository.BaseRepositoryImpl;
import grafiosch.repository.RepositoryHelper;
import grafioschtrader.entities.RiskFreeRateMapping;

/**
 * Custom-impl side of {@link RiskFreeRateMappingJpaRepository}. Required by Spring Data because the parent
 * {@code UpdateCreateJpaRepository} extends {@link BaseRepositoryCustom} which declares
 * {@code saveOnlyAttributes(...)}. Enforces {@code validateCurrencyMatch} before delegating to
 * {@link RepositoryHelper#saveOnlyAttributes} so every CUD entry point (REST, programmatic, future bulk paths)
 * is protected by the same rule.
 */
public class RiskFreeRateMappingJpaRepositoryImpl extends BaseRepositoryImpl<RiskFreeRateMapping>
    implements BaseRepositoryCustom<RiskFreeRateMapping> {

  @Autowired
  private RiskFreeRateMappingJpaRepository riskFreeRateMappingJpaRepository;

  @Override
  public RiskFreeRateMapping saveOnlyAttributes(RiskFreeRateMapping newEntity, RiskFreeRateMapping existingEntity,
      Set<Class<? extends Annotation>> updatePropertyLevelClasses) throws Exception {
    validateCurrencyMatch(newEntity);
    return RepositoryHelper.saveOnlyAttributes(riskFreeRateMappingJpaRepository, newEntity, existingEntity,
        updatePropertyLevelClasses);
  }

  /**
   * Ensures that the currency declared on the mapping row matches the ISO currency of the selected risk-free
   * {@code Security}. This mirrors the per-row client-side dropdown filter and protects against direct API calls or
   * stale UI state that would otherwise persist an inconsistent mapping (e.g. {@code currency=USD} paired with an
   * EUR-denominated instrument). The message arguments are {0} = the posted mapping currency, {1} = the underlying
   * security's currency (or {@code null} if the security id does not exist).
   */
  private void validateCurrencyMatch(RiskFreeRateMapping entity) {
    String secCurrency = riskFreeRateMappingJpaRepository
        .findCurrencyByIdSecuritycurrency(entity.getIdSecuritycurrency());
    if (secCurrency == null || !secCurrency.equals(entity.getCurrency())) {
      throw new DataViolationException("currency", "gt.riskfree.currency.mismatch",
          new Object[] { entity.getCurrency(), secCurrency });
    }
  }
}
