package grafioschtrader.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import grafiosch.dto.LimitKey;
import grafiosch.entities.User;
import grafiosch.exceptions.DataViolationException;
import grafiosch.service.EntityLimitService;
import grafioschtrader.entities.AlgoAssetclass;
import grafioschtrader.entities.AlgoSecurity;
import grafioschtrader.entities.AlgoStrategy;
import grafioschtrader.entities.AlgoTop;
import grafioschtrader.entities.AlgoTopAssetSecurity;
import grafioschtrader.entities.Tenant;
import grafioschtrader.repository.TenantJpaRepository;
import grafioschtrader.types.TenantKindType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Keeps the strategy hierarchy read only while a request operates in a simulation environment, and keeps a single
 * strategy read only while one of its environments is being replayed.
 *
 * <p>
 * The hierarchy belongs to the home tenant and a replay reads it from there, which is why {@code AlgoBaseResource}
 * re-points every algo write at {@code getActualIdTenant()}. Without this guard an edit made inside an environment
 * would therefore silently land in the home tenant, while the limit counters counted the environment - the
 * {@code MAX_ALGO_*} caps would be bypassable. Changes are made in the user's own portfolio and the simulation is
 * replayed afterwards.
 * </p>
 *
 * <p>
 * The guard sits in the repository implementations rather than in the resources, so that the bulk creation endpoints
 * and every internal caller pass through it as well.
 * </p>
 */
@Service
public class AlgoHierarchyWriteGuard {

  @Autowired
  private TenantJpaRepository tenantJpaRepository;

  @Autowired
  private AlgoReplayRunRegistry registry;

  @Autowired
  private EntityLimitService entityLimitService;

  @PersistenceContext
  private EntityManager em;

  /**
   * Refuses a mutation of the strategy hierarchy that was issued from a simulation environment.
   *
   * @throws DataViolationException if the request operates in a simulation environment of the user's home tenant
   */
  public void assertHierarchyWritable() {
    if (isSimulationContext()) {
      throw new DataViolationException("id.algo.top", "gt.simulation.algo.read.only", null);
    }
  }

  /**
   * Reports whether the current request operates in a verified simulation environment of the user's own home tenant.
   *
   * <p>
   * The token is not trusted on its own: the tenant row is loaded and has to be of kind
   * {@link TenantKindType#SIMULATION_COPY} with the user's persisted home tenant as its parent. Any other mismatch
   * between token and database is not this guard's business - the tenant context filter has already rejected such a
   * request - so it answers false rather than producing a second, misleading refusal.
   * </p>
   *
   * @return true when the current tenant is a simulation environment below the user's home tenant
   */
  public boolean isSimulationContext() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !(authentication.getDetails() instanceof User user)) {
      // Background work such as the replay executor or a scheduled task; no interactive tenant context exists.
      return false;
    }
    Integer currentIdTenant = user.getIdTenant();
    Integer homeIdTenant = user.getActualIdTenant();
    if (currentIdTenant == null || currentIdTenant.equals(homeIdTenant)) {
      return false;
    }
    Tenant tenant = tenantJpaRepository.findById(currentIdTenant).orElse(null);
    return tenant != null && tenant.getTenantKindType() == TenantKindType.SIMULATION_COPY
        && homeIdTenant.equals(tenant.getIdParentTenant());
  }

  /**
   * Refuses a mutation of the strategy hierarchy issued from a simulation environment, and a mutation of a strategy
   * that one of the user's environments is replaying right now.
   *
   * <p>
   * A replay freezes the weightings when it starts but reads the strategies and their activation afresh on every
   * trading day. An edit during the run would therefore apply to the remaining days only, and the run would no longer
   * match the snapshot it records. Changes are made before or after the run, and the environment is replayed again.
   * </p>
   *
   * @param idNode any node of the hierarchy - top, asset class or security - or null for a node not yet created
   * @throws DataViolationException if the request operates in a simulation environment, or the strategy the node
   *                                belongs to is being replayed
   */
  public void assertHierarchyWritable(Integer idNode) {
    assertHierarchyWritable();
    Integer idAlgoTop = idNode == null ? null : topOf(idNode);
    if (idAlgoTop != null && isBeingReplayed(idAlgoTop)) {
      throw new DataViolationException("id.algo.top", "gt.simulation.algo.locked.running", null);
    }
  }

  /**
   * Same as {@link #assertHierarchyWritable(Integer)} for a strategy that is identified by its own id.
   *
   * @param idAlgoStrategy the strategy to be changed or removed
   */
  public void assertStrategyWritable(Integer idAlgoStrategy) {
    AlgoStrategy strategy = idAlgoStrategy == null ? null : em.find(AlgoStrategy.class, idAlgoStrategy);
    assertHierarchyWritable(strategy == null ? null : strategy.getIdAlgoAssetclassSecurity());
  }

  /**
   * Refuses the creation of hierarchy nodes or strategies beyond the tenant's lifetime cap.
   *
   * <p>
   * The hierarchy entities are tenant private, so the generic create path checks neither a daily budget nor -
   * because their keys are registered with {@code checkedOnGenericCreate = false} - their cap. Every repository
   * implementation therefore calls this for a new row, which bounds the plain REST create as well as every internal
   * caller. It must run after {@link #assertHierarchyWritable(Integer)}: the counter counts the tenant the request
   * operates in, which is the home tenant only once the simulation context has been refused.
   * </p>
   *
   * @param limitKey   the {@code MAX} key of the entity about to be created
   * @param additional how many rows of that entity the write creates
   * @throws DataViolationException if the rows would exceed the configured cap
   */
  public void assertCreateWithinLimit(LimitKey limitKey, int additional) {
    User user = entityLimitService.getCurrentUserOrNull();
    if (!entityLimitService.fitsWithinLimit(user, limitKey, null, additional)) {
      throw new DataViolationException("id.algo.top", "algo.limit.exceeded",
          new Object[] { entityLimitService.resolve(user, limitKey).orElse(0) });
    }
  }

  /** Walks up from any node to the top it belongs to; a security outside any hierarchy belongs to none. */
  private Integer topOf(Integer idNode) {
    AlgoTopAssetSecurity node = em.find(AlgoTopAssetSecurity.class, idNode);
    if (node instanceof AlgoTop top) {
      return top.getIdAlgoAssetclassSecurity();
    }
    if (node instanceof AlgoAssetclass assetclass) {
      return assetclass.getIdAlgoAssetclassParent();
    }
    if (node instanceof AlgoSecurity security && security.getIdAlgoSecurityParent() != null) {
      AlgoTopAssetSecurity parent = em.find(AlgoTopAssetSecurity.class, security.getIdAlgoSecurityParent());
      return parent instanceof AlgoAssetclass assetclass ? assetclass.getIdAlgoAssetclassParent() : null;
    }
    return null;
  }

  /** Only reservations are consulted, so an idle environment of the strategy never blocks an edit. */
  private boolean isBeingReplayed(Integer idAlgoTop) {
    return registry.reservedEnvironments().stream().map(id -> tenantJpaRepository.findById(id).orElse(null))
        .anyMatch(env -> env != null && idAlgoTop.equals(env.getIdAlgoTop()));
  }
}
