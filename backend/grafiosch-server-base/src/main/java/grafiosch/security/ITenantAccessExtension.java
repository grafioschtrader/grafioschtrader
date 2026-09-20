package grafiosch.security;

import grafiosch.entities.User;
import grafiosch.types.TenantAccessLevel;

/**
 * Strategy for the application-specific part of tenant access resolution.
 *
 * <p>
 * The library itself knows two kinds of target: the user's own home tenant and a tenant an explicit
 * {@link grafiosch.entities.TenantAccess} grant was issued for. An application may recognise further targets -
 * GrafioschTrader adds the simulation environments below a home tenant - and it may also refuse a target the library
 * would have accepted, because a grant alone must never establish a right the application-specific rules deny.
 * </p>
 *
 * <p>
 * The implementation is discovered as an optional Spring bean. Without one the library resolves home tenants and grants
 * exactly as before and forbids every other target.
 * </p>
 */
public interface ITenantAccessExtension {

  /**
   * Resolves the access level the user really holds on a tenant that is not their home tenant.
   *
   * @param user         the authenticated user
   * @param homeIdTenant the user's persisted home tenant
   * @param idTarget     the tenant the request operates in, or wants to switch to
   * @param grantedLevel the level of an explicit {@code tenant_access} grant on {@code idTarget}, or null when there is
   *                     no grant. An implementation may pass it through, downgrade it, or veto it.
   * @return the decision on the target: an allowed level, or a refusal that may name the message key explaining it
   */
  TenantAccessDecision resolveAccessLevel(User user, Integer homeIdTenant, Integer idTarget,
      TenantAccessLevel grantedLevel);
}
