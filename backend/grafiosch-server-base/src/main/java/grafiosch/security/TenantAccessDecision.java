package grafiosch.security;

import grafiosch.types.TenantAccessLevel;

/**
 * The outcome of resolving what a user may do in a tenant, together with the reason when the answer is no.
 *
 * <p>
 * A refusal has more than one cause - an unknown or revoked target, or an application-specific state such as a
 * simulation environment that is busy replaying - and the user is entitled to be told which. The reason travels as a
 * plain message key so that the application can name its own texts without any of its types reaching this library.
 * </p>
 *
 * @param level             the access level on the target, or null when access is refused
 * @param refusalMessageKey the message key explaining a refusal, or null to use the generic one
 */
public record TenantAccessDecision(TenantAccessLevel level, String refusalMessageKey) {

  /**
   * @param level the level the user holds; must not be null
   * @return an allowing decision
   */
  public static TenantAccessDecision allow(TenantAccessLevel level) {
    return new TenantAccessDecision(level, null);
  }

  /**
   * @param messageKey the message key explaining the refusal, or null for the generic text
   * @return a refusing decision
   */
  public static TenantAccessDecision refuse(String messageKey) {
    return new TenantAccessDecision(null, messageKey);
  }

  /** @return a refusing decision without a specific reason */
  public static TenantAccessDecision refuse() {
    return refuse(null);
  }

  /** @return true when the target may be used */
  public boolean isAllowed() {
    return level != null;
  }
}
