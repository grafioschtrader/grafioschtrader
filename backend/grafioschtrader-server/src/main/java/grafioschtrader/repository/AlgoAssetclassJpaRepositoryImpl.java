package grafioschtrader.repository;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.context.SecurityContextHolder;

import grafiosch.BaseConstants;
import grafiosch.common.PropertyAlwaysUpdatable;
import grafiosch.common.PropertyOnlyCreation;
import grafiosch.entities.User;
import grafiosch.exceptions.DataViolationException;
import grafiosch.repository.BaseRepositoryImpl;
import grafiosch.service.EntityLimitService;
import grafioschtrader.config.LimitKeyConfig;
import grafioschtrader.entities.AlgoAssetclass;
import grafioschtrader.entities.AlgoSecurity;
import grafioschtrader.entities.Security;
import grafioschtrader.reportviews.securitycurrency.SecuritycurrencyLists;
import grafioschtrader.search.SecuritycurrencySearch;
import grafioschtrader.service.AlgoAccountPriorityService;
import grafioschtrader.service.AlgoSecurityEligibility;

public class AlgoAssetclassJpaRepositoryImpl extends BaseRepositoryImpl<AlgoAssetclass>
    implements AlgoAssetclassJpaRepositoryCustom {

  @Autowired
  private AlgoAssetclassJpaRepository algoAssetclassJpaRepository;

  @Autowired
  private grafioschtrader.service.AlgoAlertScopeLifecycle alertScopeLifecycle;

  @Autowired
  private grafioschtrader.service.AlgoHierarchyWriteGuard hierarchyWriteGuard;

  @Autowired
  @Lazy // The priority service reads security accounts and securities, whose repositories reach back to the algo tree.
  private AlgoAccountPriorityService accountPriority;

  @Override
  public AlgoAssetclass saveOnlyAttributes(AlgoAssetclass algoAssetclass, AlgoAssetclass existingEntity,
      final Set<Class<? extends Annotation>> updatePropertyLevelClasses) {
    hierarchyWriteGuard.assertHierarchyWritable(algoAssetclass.getIdAlgoAssetclassParent());
    if (existingEntity == null) {
      hierarchyWriteGuard.assertCreateWithinLimit(LimitKeyConfig.KEY_ALGO_ASSETCLASS, 1);
    }
    validateRebalancingOverrides(algoAssetclass);
    validateMutualExclusivity(algoAssetclass);
    accountPriority.validate(algoAssetclass, algoAssetclass.getAssetclass());
    var before = alertScopeLifecycle.snapshot(algoAssetclass.getIdTenant());
    AlgoAssetclass saved = algoAssetclassJpaRepository.save(algoAssetclass);
    alertScopeLifecycle.changed(saved.getIdTenant(), before);
    return saved;
  }

  private void validateMutualExclusivity(AlgoAssetclass algoAssetclass) {
    boolean hasName = algoAssetclass.getName() != null;
    boolean hasAssetclass = algoAssetclass.getAssetclass() != null;
    if (hasName && hasAssetclass) {
      throw new DataViolationException("name", "algo.assetclass.name.or.assetclass", null);
    }
    if (!hasName && !hasAssetclass) {
      throw new DataViolationException("name", "algo.assetclass.name.or.assetclass.required", null);
    }
  }

  private void validateRebalancingOverrides(AlgoAssetclass entity) {
    Double deviation = entity.getSecurityDeviationPercentage();
    if (deviation != null && (!Double.isFinite(deviation) || deviation < 0 || deviation > 100)) {
      throw new DataViolationException("security.deviation.percentage", "algo.rebalancing.invalid.band", null);
    }
    if (entity.getMaxTradedSecuritiesPerAssetclass() != null && entity.getMaxTradedSecuritiesPerAssetclass() < 1) {
      throw new DataViolationException("max.traded.securities.per.assetclass", "algo.rebalancing.invalid.limit", null);
    }
  }

  @Autowired
  private AlgoSecurityJpaRepository algoSecurityJpaRepository;

  @Autowired
  private SecurityJpaRepository securityJpaRepository;

  @Autowired
  private EntityLimitService entityLimitService;

  @Autowired
  @Lazy // The eligibility service resolves AlgoTop, whose repository also depends on the algo asset classes.
  private AlgoSecurityEligibility securityEligibility;

  @Autowired
  @Lazy // The AlgoTop repository reaches back to this repository.
  private AlgoTopJpaRepository algoTopJpaRepository;

  @Override
  public SecuritycurrencyLists searchByCriteria(Integer idAlgoAssetclassSecurity,
      SecuritycurrencySearch securitycurrencySearch) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    Integer idTenant = user.getActualIdTenant();
    getCustomCategory(idAlgoAssetclassSecurity, idTenant);
    Set<Integer> assigned = assignedSecurityIds(idAlgoAssetclassSecurity, idTenant);
    List<Security> securities = securityJpaRepository
        .searchBuilderWithExclusion(null, null, securitycurrencySearch, idTenant).stream()
        .filter(security -> !assigned.contains(security.getIdSecuritycurrency())).toList();
    return new SecuritycurrencyLists(
        securityEligibility.filterCandidates(idTenant, idAlgoAssetclassSecurity, securities), List.of());
  }

  @Override
  public AlgoAssetclass addSecuritiesToCustomCategory(Integer idAlgoAssetclassSecurity,
      SecuritycurrencyLists securitycurrencyLists) throws Exception {
    hierarchyWriteGuard.assertHierarchyWritable(idAlgoAssetclassSecurity);
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    Integer idTenant = user.getActualIdTenant();
    getCustomCategory(idAlgoAssetclassSecurity, idTenant);
    List<AlgoSecurity> siblings = algoSecurityJpaRepository
        .findByIdAlgoSecurityParentAndIdTenant(idAlgoAssetclassSecurity, idTenant);
    Set<Integer> assigned = siblings.stream().map(s -> s.getSecurity().getIdSecuritycurrency())
        .collect(Collectors.toSet());
    List<Integer> newIds = securitycurrencyLists.securityList == null ? List.of()
        : securitycurrencyLists.securityList.stream().map(Security::getIdSecuritycurrency).filter(Objects::nonNull)
            .filter(id -> !assigned.contains(id)).distinct().toList();
    if (!newIds.isEmpty()) {
      if (!entityLimitService.fitsWithinLimit(user, LimitKeyConfig.KEY_ALGO_SECURITY, null, newIds.size())) {
        throw new SecurityException(BaseConstants.LIMIT_SECURITY_BREACH);
      }
      float weight = meanWeight(siblings);
      for (Integer idSecuritycurrency : newIds) {
        AlgoSecurity algoSecurity = new AlgoSecurity();
        algoSecurity.setIdTenant(idTenant);
        algoSecurity.setIdAlgoSecurityParent(idAlgoAssetclassSecurity);
        Security security = new Security();
        security.setIdSecuritycurrency(idSecuritycurrency);
        algoSecurity.setSecurity(security);
        algoSecurity.setPercentage(weight);
        algoSecurityJpaRepository.saveOnlyAttributes(algoSecurity, null,
            Set.of(PropertyAlwaysUpdatable.class, PropertyOnlyCreation.class));
      }
      algoTopJpaRepository.normalizeChildPercentages(idAlgoAssetclassSecurity, idTenant);
    }
    return algoAssetclassJpaRepository.findById(idAlgoAssetclassSecurity).orElseThrow();
  }

  /**
   * Loads a custom category of the given tenant. Only a custom category accepts instruments of any asset class, a node
   * of an asset class takes its instruments from that asset class alone.
   */
  private AlgoAssetclass getCustomCategory(Integer idAlgoAssetclassSecurity, Integer idTenant) {
    return algoAssetclassJpaRepository.findById(idAlgoAssetclassSecurity)
        .filter(aa -> idTenant.equals(aa.getIdTenant()) && aa.getName() != null)
        .orElseThrow(() -> new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH));
  }

  private Set<Integer> assignedSecurityIds(Integer idAlgoAssetclassSecurity, Integer idTenant) {
    return algoSecurityJpaRepository.findByIdAlgoSecurityParentAndIdTenant(idAlgoAssetclassSecurity, idTenant).stream()
        .map(s -> s.getSecurity().getIdSecuritycurrency()).collect(Collectors.toSet());
  }

  /**
   * The weight a newly added instrument starts with before the category is normalized: the mean of its siblings, so
   * that it enters with an average share. Without weighted siblings every new instrument gets the same share.
   */
  private static float meanWeight(List<AlgoSecurity> siblings) {
    double mean = siblings.stream().mapToDouble(s -> s.getPercentage() == null ? 0.0 : s.getPercentage()).average()
        .orElse(0.0);
    return mean > 0 ? (float) mean : 1f;
  }

  @Autowired
  private AlgoTradingRepository tradingRepository;

  public int delEntityWithTenant(Integer idAlgoAssetclassSecurity, Integer idTenant) {
    hierarchyWriteGuard.assertHierarchyWritable(idAlgoAssetclassSecurity);
    int deleted = algoAssetclassJpaRepository.deleteByIdAlgoAssetclassSecurityAndIdTenant(idAlgoAssetclassSecurity,
        idTenant);
    tradingRepository.clearRemovedAssignments(idTenant);
    return deleted;
  }

}
