package grafiosch.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import grafiosch.entities.TenantAccess;
import grafiosch.entities.User;
import grafiosch.repository.TenantAccessJpaRepository;
import grafiosch.types.TenantAccessLevel;

/**
 * The single authority on which tenant a user may operate in, and with which level.
 *
 * <p>
 * Both consumers ask the same question through this service: {@code JwtTokenHandler} on every request, for the tenant
 * the token names, and {@code TenantBaseResource} when a switch is requested. Having one implementation is what keeps
 * the two from drifting - a target that cannot be switched to can also not be used by a token that already names it.
 * </p>
 *
 * <p>
 * Nothing is cached. The resolution runs against the current database state on every request, so revoking a grant,
 * downgrading it or deleting the target tenant takes effect on the very next request.
 * </p>
 */
@Service
public class TenantAccessResolver {

  @Autowired
  private TenantAccessJpaRepository tenantAccessJpaRepository;

  /**
   * Application-specific targets and vetoes, supplied by the application layer. Optional: without it only the home
   * tenant and explicitly granted tenants are accessible.
   */
  @Autowired(required = false)
  private ITenantAccessExtension tenantAccessExtension;

  /**
   * Resolves the access level a user holds on a tenant.
   *
   * <p>
   * The home tenant resolves from the persisted {@code home_tenant_read_only} flag. For every other target an explicit
   * grant is looked up and then handed to the optional {@link ITenantAccessExtension}, which has the last word: a grant
   * that the application-specific rules do not accept - a stale one on a deleted or foreign tenant, for example - is
   * vetoed rather than honoured.
   * </p>
   *
   * @param user         the authenticated user
   * @param homeIdTenant the user's persisted home tenant, normally {@code user.getActualIdTenant()}
   * @param idTarget     the tenant to resolve
   * @return the access level on the target, or null when access there is forbidden
   */
  public TenantAccessLevel resolve(User user, Integer homeIdTenant, Integer idTarget) {
    return decide(user, homeIdTenant, idTarget).level();
  }

  /**
   * Resolves the access level and, when the answer is a refusal, the reason for it.
   *
   * <p>
   * Used where the refusal is reported to the user rather than merely acted on, so that "you have no access here" and
   * "this is busy right now" do not read the same.
   * </p>
   *
   * @param user         the authenticated user
   * @param homeIdTenant the user's persisted home tenant, normally {@code user.getActualIdTenant()}
   * @param idTarget     the tenant to resolve
   * @return the decision, never null
   */
  public TenantAccessDecision decide(User user, Integer homeIdTenant, Integer idTarget) {
    if (idTarget == null || idTarget.equals(homeIdTenant)) {
      return TenantAccessDecision
          .allow(user.isHomeTenantReadOnly() ? TenantAccessLevel.READ : TenantAccessLevel.MANAGE);
    }
    TenantAccessLevel grantedLevel = tenantAccessJpaRepository.findByIdUserAndIdTenant(user.getIdUser(), idTarget)
        .map(TenantAccess::getAccessLevel).orElse(null);
    if (tenantAccessExtension == null) {
      return grantedLevel == null ? TenantAccessDecision.refuse() : TenantAccessDecision.allow(grantedLevel);
    }
    return tenantAccessExtension.resolveAccessLevel(user, homeIdTenant, idTarget, grantedLevel);
  }
}
