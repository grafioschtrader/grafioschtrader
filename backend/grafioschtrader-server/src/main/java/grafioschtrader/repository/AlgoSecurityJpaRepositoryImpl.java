package grafioschtrader.repository;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.context.SecurityContextHolder;

import grafiosch.BaseConstants;
import grafiosch.common.PropertyAlwaysUpdatable;
import grafiosch.common.PropertyOnlyCreation;
import grafiosch.entities.User;
import grafiosch.repository.BaseRepositoryImpl;
import grafioschtrader.algo.AlgoSecurityStrategyImplType;
import grafioschtrader.algo.strategy.model.AlgoLevelType;
import grafioschtrader.algo.strategy.model.AlgoStrategyImplementationType;
import grafioschtrader.algo.strategy.model.StrategyHelper;
import grafioschtrader.config.LimitKeyConfig;
import grafioschtrader.entities.AlgoSecurity;

public class AlgoSecurityJpaRepositoryImpl extends BaseRepositoryImpl<AlgoSecurity>
    implements AlgoSecurityJpaRepositoryCustom {

  @Autowired
  private AlgoSecurityJpaRepository algoSecurityJpaRepository;

  @Autowired
  private AlgoStrategyJpaRepository algoStrategyJpaRepository;

  @Autowired
  private SecurityJpaRepository securityJpaRepository;

  @Autowired
  private grafioschtrader.service.AlgoAlertScopeLifecycle alertScopeLifecycle;

  @Autowired
  private grafioschtrader.service.AlgoHierarchyWriteGuard hierarchyWriteGuard;

  @Autowired
  @Lazy // The eligibility service resolves AlgoTop, whose repository also depends on AlgoSecurity.
  private grafioschtrader.service.AlgoSecurityEligibility securityEligibility;

  @Autowired
  @Lazy // The priority service reads security accounts and securities, whose repositories reach back to the algo tree.
  private grafioschtrader.service.AlgoAccountPriorityService accountPriority;

  @Override
  public AlgoSecurity saveOnlyAttributes(AlgoSecurity algoSecurity, AlgoSecurity existingEntity,
      Set<Class<? extends Annotation>> updatePropertyLevelClasses) {
    hierarchyWriteGuard.assertHierarchyWritable(algoSecurity.getIdAlgoSecurityParent());
    if (existingEntity == null) {
      hierarchyWriteGuard.assertCreateWithinLimit(LimitKeyConfig.KEY_ALGO_SECURITY, 1);
    }
    securityEligibility.validateAssignment(algoSecurity, existingEntity);
    if (algoSecurity.getIdSecurityaccount1() != null || algoSecurity.getIdSecurityaccount2() != null) {
      accountPriority.validate(algoSecurity, accountPriority.assetclassOf(algoSecurity));
    }
    var before = alertScopeLifecycle.snapshot(algoSecurity.getIdTenant());
    AlgoSecurity saved = algoSecurityJpaRepository.save(algoSecurity);
    alertScopeLifecycle.changed(saved.getIdTenant(), before);
    return saved;
  }

  @Autowired
  private AlgoTradingRepository tradingRepository;

  public int delEntityWithTenant(Integer idAlgoAssetclassSecurity, Integer idTenant) {
    hierarchyWriteGuard.assertHierarchyWritable(idAlgoAssetclassSecurity);
    int deleted = algoSecurityJpaRepository.deleteByIdAlgoAssetclassSecurityAndIdTenant(idAlgoAssetclassSecurity,
        idTenant);
    tradingRepository.clearRemovedAssignments(idTenant);
    return deleted;
  }

  /**
   * The standalone alert node of one instrument, created on the spot if the user is adding their first alert for it.
   *
   * <p>
   * Two things used to be wrong here, and both only showed on the second visit. The set of still available strategy
   * types was read through {@code assit.algoSecurity}, which is not assigned until after the branch, so asking for an
   * instrument that already had an alert threw a NullPointerException rather than opening the dialog. And the lookup
   * did not restrict itself to nodes without a parent, so an instrument sitting inside an AlgoTop hierarchy came back
   * as if it were a standalone alert, and a new alert was then attached to the hierarchy node.
   * </p>
   *
   * <p>
   * Being a GET this endpoint is seen by no write-blocking filter, yet its first visit persists a row. It therefore
   * carries the two write checks itself: it is refused in a simulation environment, where the hierarchy is read only,
   * and a read-only user may look an existing node up but never create one.
   * </p>
   *
   * @param idSecuritycurrency the instrument the user asked to add an alert for
   * @return the node together with the strategy types still available on it, and whether it had to be created
   */
  @Override
  public AlgoSecurityStrategyImplType getAlgoSecurityStrategyImplTypeByIdSecuritycurrency(Integer idSecuritycurrency) {
    AlgoSecurityStrategyImplType assit = new AlgoSecurityStrategyImplType(
        StrategyHelper.getUnusedStrategiesForManualAdding(Collections.<AlgoStrategyImplementationType>emptySet(),
            AlgoLevelType.SECURITY_LEVEL));
    if (idSecuritycurrency != null) {
      hierarchyWriteGuard.assertHierarchyWritable();
      final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
      AlgoSecurity algoSecurity = algoSecurityJpaRepository
          .findBySecurity_idSecuritycurrencyAndIdTenantAndIdAlgoSecurityParentIsNull(idSecuritycurrency,
              user.getActualIdTenant());
      if (algoSecurity == null) {
        if (user.isTenantAccessReadOnly()) {
          throw new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH);
        }
        algoSecurity = new AlgoSecurity();
        algoSecurity.setSecurity(securityJpaRepository.findByIdSecuritycurrency(idSecuritycurrency));
        algoSecurity.setIdTenant(user.getActualIdTenant());
        algoSecurity = saveOnlyAttributes(algoSecurity, null,
            Set.of(PropertyAlwaysUpdatable.class, PropertyOnlyCreation.class));
        assit.wasCreated = true;
      } else {
        assit.possibleStrategyImplSet = algoStrategyJpaRepository
            .getUnusedStrategiesForManualAdding(algoSecurity.getIdAlgoAssetclassSecurity());
      }
      assit.algoSecurity = algoSecurity;
    }
    return assit;
  }

}
