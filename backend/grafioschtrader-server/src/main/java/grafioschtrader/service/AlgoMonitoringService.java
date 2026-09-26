package grafioschtrader.service;

import java.util.Objects;

import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.BaseConstants;
import grafiosch.entities.User;
import grafiosch.exceptions.DataViolationException;
import grafioschtrader.entities.AlgoStrategy;
import grafioschtrader.entities.Tenant;
import grafioschtrader.repository.AlgoAssetclassJpaRepository;
import grafioschtrader.repository.AlgoMessageAlertJpaRepository;
import grafioschtrader.repository.AlgoRecommendationJpaRepository;
import grafioschtrader.repository.AlgoSecurityJpaRepository;
import grafioschtrader.repository.AlgoStrategyJpaRepository;
import grafioschtrader.repository.AlgoTopJpaRepository;
import grafioschtrader.repository.TenantJpaRepository;
import grafioschtrader.types.TenantKindType;

/**
 * Main-tenant monitoring preferences: the assigned monitoring hierarchy and the per-strategy live alert switch of that
 * hierarchy and of the standalone alerts. This policy must never gate historical replay or allocation calculations.
 * It does decide which hierarchy has a stored live plan: recommendations exist only for the assigned one.
 */
@Service
public class AlgoMonitoringService {
  private final TenantJpaRepository tenants;
  private final AlgoTopJpaRepository tops;
  private final AlgoAssetclassJpaRepository buckets;
  private final AlgoSecurityJpaRepository securities;
  private final AlgoStrategyJpaRepository strategies;
  private final AlgoMessageAlertJpaRepository alarms;
  private final AlgoRecommendationJpaRepository recommendations;
  private final AlgoAlertScopeLifecycle lifecycle;

  public AlgoMonitoringService(TenantJpaRepository tenants, AlgoTopJpaRepository tops,
      AlgoAssetclassJpaRepository buckets, AlgoSecurityJpaRepository securities, AlgoStrategyJpaRepository strategies,
      AlgoMessageAlertJpaRepository alarms, AlgoRecommendationJpaRepository recommendations,
      @Lazy AlgoAlertScopeLifecycle lifecycle) {
    this.tenants = tenants;
    this.tops = tops;
    this.buckets = buckets;
    this.securities = securities;
    this.strategies = strategies;
    this.alarms = alarms;
    this.recommendations = recommendations;
    this.lifecycle = lifecycle;
  }

  /** Null top denotes a verified standalone security alert, never a missing or foreign node. */
  private record Scope(Integer top, boolean standalone) {
  }

  private Scope scope(AlgoStrategy strategy) {
    Integer node = strategy.getIdAlgoAssetclassSecurity();
    Integer tenant = strategy.getIdTenant();
    var top = tops.findByIdTenantAndIdAlgoAssetclassSecurity(tenant, node);
    if (top != null)
      return new Scope(top.getId(), false);
    var bucket = buckets.findById(node).filter(b -> tenant.equals(b.getIdTenant())).orElse(null);
    if (bucket != null)
      return new Scope(bucket.getIdAlgoAssetclassParent(), false);
    var security = securities.findById(node).filter(s -> tenant.equals(s.getIdTenant())).orElse(null);
    if (security == null)
      return new Scope(null, false);
    if (security.getIdAlgoSecurityParent() == null)
      return new Scope(null, true);
    return buckets.findById(security.getIdAlgoSecurityParent()).filter(b -> tenant.equals(b.getIdTenant()))
        .map(b -> new Scope(b.getIdAlgoAssetclassParent(), false)).orElse(new Scope(null, false));
  }

  /**
   * Checks the persisted preference and assignment before recording or delivering a live notification. A standalone
   * alert only needs its preference; a hierarchy alert additionally needs its hierarchy to be the assigned one.
   */
  @Transactional(readOnly = true)
  public boolean permitsAlert(Integer tenant, Integer strategyId) {
    AlgoStrategy strategy = strategies.findById(strategyId).filter(s -> tenant.equals(s.getIdTenant())).orElse(null);
    if (strategy == null || !strategy.isAlertEnabled())
      return false;
    Scope scope = scope(strategy);
    return scope.standalone() || (scope.top() != null && isAssigned(tenant, scope.top()));
  }

  public boolean isAssigned(Integer tenant, Integer top) {
    return top != null && tenants.findById(tenant)
        .filter(t -> t.getTenantKindType() == TenantKindType.MAIN && Objects.equals(top, t.getIdAlgoTop())).isPresent();
  }

  /** Capability for the overview; write operations repeat the check against current persisted state. */
  public boolean canEdit(Integer tenant, Integer top) {
    User user = currentUser();
    return user != null && !user.isTenantAccessReadOnly() && !user.isHomeTenantReadOnly()
        && Objects.equals(tenant, user.getIdTenant()) && Objects.equals(tenant, user.getActualIdTenant())
        && isAssigned(tenant, top);
  }

  private User currentUser() {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    return authentication != null && authentication.getDetails() instanceof User user ? user : null;
  }

  private Tenant writableTenant() {
    User user = currentUser();
    if (user == null || user.isTenantAccessReadOnly() || user.isHomeTenantReadOnly()
        || !Objects.equals(user.getIdTenant(), user.getActualIdTenant()))
      throw new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH);
    Tenant tenant = tenants.lockMonitoringTenant(user.getIdTenant()).orElseThrow();
    if (tenant.getTenantKindType() != TenantKindType.MAIN)
      throw new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH);
    return tenant;
  }

  /**
   * Replaces or clears the main tenant's assignment without changing any simulation association. The live plan of the
   * previously assigned hierarchy is removed, so a hierarchy assigned later starts with a fresh checkpoint.
   */
  @Transactional
  public Tenant assign(Integer idAlgoTop) {
    Tenant tenant = writableTenant();
    if (idAlgoTop != null && tops.findByIdTenantAndIdAlgoAssetclassSecurity(tenant.getId(), idAlgoTop) == null)
      throw new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH);
    if (!Objects.equals(tenant.getIdAlgoTop(), idAlgoTop)) {
      var before = lifecycle.snapshot(tenant.getId());
      if (tenant.getIdAlgoTop() != null)
        recommendations.deleteAllKindsByIdTenantAndIdAlgoTop(tenant.getId(), tenant.getIdAlgoTop());
      tenant.setIdAlgoTop(idAlgoTop);
      tenants.saveAndFlush(tenant);
      lifecycle.changed(tenant.getId(), before);
      cancelIneligible(tenant.getId());
    }
    return tenant;
  }

  /**
   * Updates only the notification preference, without applying strategy configuration/draft validation. Allowed for a
   * standalone alert and for a strategy of the assigned monitoring hierarchy.
   */
  @Transactional
  public AlgoStrategy setAlertEnabled(Integer idStrategy, boolean enabled) {
    Tenant tenant = writableTenant();
    AlgoStrategy strategy = strategies.findById(idStrategy).filter(s -> tenant.getId().equals(s.getIdTenant()))
        .orElseThrow(() -> new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH));
    Scope scope = scope(strategy);
    if (!scope.standalone() && (scope.top() == null || !Objects.equals(scope.top(), tenant.getIdAlgoTop())))
      throw new DataViolationException("alert.enabled", "algo.monitoring.not.assigned", null);
    if (strategy.isAlertEnabled() != enabled) {
      var before = lifecycle.snapshot(tenant.getId());
      strategy.setAlertEnabled(enabled);
      strategies.saveAndFlush(strategy);
      lifecycle.changed(tenant.getId(), before);
      cancelIneligible(tenant.getId());
    }
    return strategy;
  }

  private void cancelIneligible(Integer tenant) {
    for (AlgoStrategy strategy : strategies.findByIdTenant(tenant)) {
      if (!permitsAlert(tenant, strategy.getId()))
        alarms.cancelPendingForStrategy(tenant, strategy.getId());
    }
  }
}
