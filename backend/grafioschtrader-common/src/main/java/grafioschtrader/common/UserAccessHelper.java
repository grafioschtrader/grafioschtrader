package grafioschtrader.common;

import java.util.Collection;

import grafioschtrader.entities.Auditable;
import grafioschtrader.entities.Role;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.User;

public class UserAccessHelper {

  /**
   * Return true when user has ROLE_ADMIN or is the owner of the entity.
   *
   * @param user
   * @param entity
   * @return
   */
  public static boolean isAdminOrOwnerOfEntity(User user, Auditable entity) {
    return isAdmin(user) || hasRightsForEditingOrDeleteOnEntity(user, entity);
  }

  /**
   * Return true when user has ROLE_ADMIN, ROLE_ALL_EDIT or is the owner of the
   * entity.
   *
   * @param user
   * @param entity
   * @return
   */
  public static boolean hasRightsOrPrivilegesForEditingOrDelete(User user, Auditable entity) {
    return hasHigherPrivileges(user) || hasRightsForEditingOrDeleteOnEntity(user, entity);
  }

  /**
   * Return true when the user is the owner of the entity.
   *
   * @param user
   * @param entity
   * @return
   */
  public static boolean hasRightsForEditingOrDeleteOnEntity(User user, Auditable entity) {
    return entity.getCreatedBy().equals(user.getIdUser())
        || entity instanceof Security && ((Security) entity).getIdTenantPrivate() != null
            && ((Security) entity).getIdTenantPrivate().equals(user.getIdTenant());
  }

  public static boolean isAdmin(User user) {
    Collection<Role> roles = user.getRoles();
    return (roles.stream().map(Role::getRolename).anyMatch(roleName -> roleName.equals(Role.ROLE_ADMIN)));
  }

  public static boolean hasHigherPrivileges(User user) {
    Collection<Role> roles = user.getRoles();
    return (roles.stream().map(Role::getRolename)
        .anyMatch(roleName -> roleName.equals(Role.ROLE_ADMIN) || roleName.equals(Role.ROLE_ALL_EDIT)));
  }

  public static boolean isLimitedEditingUser(User user) {
    Collection<Role> roles = user.getRoles();
    return (roles.stream().map(Role::getRolename).anyMatch(roleName -> roleName.equals(Role.ROLE_LIMIT_EDIT)));
  }

}
