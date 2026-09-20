package grafioschtrader.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import grafiosch.entities.User;
import grafiosch.security.ITenantAccessExtension;
import grafiosch.security.TenantAccessDecision;
import grafiosch.types.TenantAccessLevel;
import grafioschtrader.entities.Tenant;
import grafioschtrader.repository.TenantJpaRepository;
import grafioschtrader.service.SimulationRunActivityService;
import grafioschtrader.types.TenantKindType;

/**
 * Adds the simulation environments of a home tenant to the tenants a user may operate in, and refuses every target the
 * tenant table no longer backs.
 *
 * <p>
 * An environment is reachable only by the owner of its parent: the target row must exist, be of kind
 * {@link TenantKindType#SIMULATION_COPY} and name the user's persisted home tenant as its parent. Its access level is
 * inherited from home rather than granted outright - a user whose own portfolio is read-only reads the environment as
 * well, they do not gain write access by entering it.
 * </p>
 *
 * <p>
 * A target whose tenant row is gone is refused even when a grant for it still exists, which is what makes a deleted
 * environment or a deleted client unusable from an old token rather than merely empty.
 * </p>
 *
 * <p>
 * An environment that is being replayed is refused as well, for the switch and for a token that already names it alike.
 * The replay books transactions and rebuilds the holdings of the environment as it goes, so a second writer - or even a
 * reader seeing half of a rebuild - would be looking at a portfolio that does not exist at any point in time. The
 * refusal names its own reason so the user is told to wait rather than that access was withdrawn. It does not reach the
 * replay itself, which never arrives over HTTP.
 * </p>
 */
@Component
public class TenantAccessExtensionGT implements ITenantAccessExtension {

  /** The message key telling the user that the environment is busy replaying rather than closed to them. */
  public static final String SIMULATION_ACTIVE_KEY = "gt.simulation.environment.active";

  @Autowired
  private TenantJpaRepository tenantJpaRepository;

  @Autowired
  private SimulationRunActivityService runActivity;

  @Override
  public TenantAccessDecision resolveAccessLevel(User user, Integer homeIdTenant, Integer idTarget,
      TenantAccessLevel grantedLevel) {
    Tenant target = tenantJpaRepository.findById(idTarget).orElse(null);
    if (target == null) {
      return TenantAccessDecision.refuse();
    }
    if (target.getTenantKindType() == TenantKindType.SIMULATION_COPY) {
      if (!homeIdTenant.equals(target.getIdParentTenant())) {
        return TenantAccessDecision.refuse();
      }
      if (runActivity.isActive(idTarget)) {
        return TenantAccessDecision.refuse(SIMULATION_ACTIVE_KEY);
      }
      return TenantAccessDecision
          .allow(user.isHomeTenantReadOnly() ? TenantAccessLevel.READ : TenantAccessLevel.MANAGE);
    }
    return grantedLevel == null ? TenantAccessDecision.refuse() : TenantAccessDecision.allow(grantedLevel);
  }
}
