package grafioschtrader.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import grafiosch.entities.User;
import grafiosch.exceptions.DataViolationException;
import grafioschtrader.entities.Tenant;
import grafioschtrader.repository.TenantJpaRepository;
import grafioschtrader.types.TenantKindType;

/**
 * Keeps the strategy hierarchy read only while a request operates in a simulation environment.
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
 * The guard sits in the repository implementations rather than in the resources, so that the bulk creation endpoints,
 * the activation toggle and every internal caller pass through it as well.
 * </p>
 */
@Service
public class AlgoHierarchyWriteGuard {

  @Autowired
  private TenantJpaRepository tenantJpaRepository;

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
}
