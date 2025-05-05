package grafiosch.common;

import java.util.Collection;

import grafiosch.entities.AdditionalRights;
import grafiosch.entities.Auditable;
import grafiosch.entities.Role;
import grafiosch.entities.User;

public class UserAccessHelper {

  /**
   * Return true when user has ROLE_ADMIN or is the owner of the entity.
   */
  public static boolean isAdminOrOwnerOfEntity(User user, Auditable entity) {
    return isAdmin(user) || hasRightsForEditingOrDeleteOnEntity(user, entity);
  }

  /**
   * Return true when user has ROLE_ADMIN, ROLE_ALL_EDIT or is the owner of the
   * entity.
   */
  public static boolean hasRightsOrPrivilegesForEditingOrDelete(User user, Auditable entity) {
    return hasHigherPrivileges(user) || hasRightsForEditingOrDeleteOnEntity(user, entity);
  }

  /**
   * Return true when the user is the owner of the entity.
   */
  public static boolean hasRightsForEditingOrDeleteOnEntity(User user, Auditable entity) {
    boolean isOwner = entity.getCreatedBy().equals(user.getIdUser());
    if (entity instanceof AdditionalRights) {
        return isOwner || ((AdditionalRights) entity).hasAdditionalRights(user);
    }
    return isOwner;
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
