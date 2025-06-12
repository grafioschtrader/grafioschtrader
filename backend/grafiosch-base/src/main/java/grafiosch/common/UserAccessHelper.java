package grafiosch.common;

import java.util.Collection;

import grafiosch.entities.AdditionalRights;
import grafiosch.entities.Auditable;
import grafiosch.entities.Role;
import grafiosch.entities.User;

/**
 * Helper class for checking user access rights and roles in relation to entities. This class provides static methods to
 * determine if a user has administrative privileges, ownership of an entity, or other specific roles relevant to
 * editing or viewing data.
 */
public class UserAccessHelper {

  /**
   * Checks if the given user has the 'ROLE_ADMIN' or is the owner of the specified auditable entity. Ownership is
   * determined by comparing the entity's creator ID with the user's ID.
   *
   * @param user   The {@link User} whose permissions are being checked.
   * @param entity The {@link Auditable} entity to check against.
   * @return {@code true} if the user is an administrator or the owner of the entity, {@code false} otherwise.
   */
  public static boolean isAdminOrOwnerOfEntity(User user, Auditable entity) {
    return isAdmin(user) || hasRightsForEditingOrDeleteOnEntity(user, entity);
  }

  /**
   * Checks if the user has higher privileges (ROLE_ADMIN or ROLE_ALL_EDIT) or is the owner of the entity, granting them
   * rights for editing or deleting the entity.
   *
   * @param user   The {@link User} whose permissions are being checked.
   * @param entity The {@link Auditable} entity to check against.
   * @return {@code true} if the user has ROLE_ADMIN, ROLE_ALL_EDIT, or owns the entity (including checking
   *         {@link AdditionalRights}), {@code false} otherwise.
   */
  public static boolean hasRightsOrPrivilegesForEditingOrDelete(User user, Auditable entity) {
    return hasHigherPrivileges(user) || hasRightsForEditingOrDeleteOnEntity(user, entity);
  }

  /**
   * Checks if the user is the direct owner of the auditable entity or has additional rights if the entity implements
   * the {@link AdditionalRights} interface. Ownership is determined by matching the entity's creator ID with the user's
   * ID.
   *
   * @param user   The {@link User} to check.
   * @param entity The {@link Auditable} entity (which might also be an instance of {@link AdditionalRights}).
   * @return {@code true} if the user is the owner or has been granted additional rights on the entity, {@code false}
   *         otherwise.
   */
  public static boolean hasRightsForEditingOrDeleteOnEntity(User user, Auditable entity) {
    boolean isOwner = entity.getCreatedBy().equals(user.getIdUser());
    if (entity instanceof AdditionalRights) {
      return isOwner || ((AdditionalRights) entity).hasAdditionalRights(user);
    }
    return isOwner;
  }

  /**
   * Checks if the given user has the 'ROLE_ADMIN' role.
   *
   * @param user The {@link User} to check.
   * @return {@code true} if the user has the 'ROLE_ADMIN' role, {@code false} otherwise.
   */
  public static boolean isAdmin(User user) {
    Collection<Role> roles = user.getRoles();
    return (roles.stream().map(Role::getRolename).anyMatch(roleName -> roleName.equals(Role.ROLE_ADMIN)));
  }

  /**
   * Checks if the given user has higher privileges, specifically 'ROLE_ADMIN' or 'ROLE_ALL_EDIT'. These roles typically
   * grant broader access to edit or manage data across different entities.
   *
   * @param user The {@link User} to check.
   * @return {@code true} if the user has 'ROLE_ADMIN' or 'ROLE_ALL_EDIT', {@code false} otherwise.
   */
  public static boolean hasHigherPrivileges(User user) {
    Collection<Role> roles = user.getRoles();
    return (roles.stream().map(Role::getRolename)
        .anyMatch(roleName -> roleName.equals(Role.ROLE_ADMIN) || roleName.equals(Role.ROLE_ALL_EDIT)));
  }

  /**
   * Checks if the given user has the 'ROLE_LIMIT_EDIT' role. This role typically implies that the user can edit certain
   * entities or fields but with some restrictions compared to users with higher privileges.
   *
   * @param user The {@link User} to check.
   * @return {@code true} if the user has the 'ROLE_LIMIT_EDIT' role, {@code false} otherwise.
   */
  public static boolean isLimitedEditingUser(User user) {
    Collection<Role> roles = user.getRoles();
    return (roles.stream().map(Role::getRolename).anyMatch(roleName -> roleName.equals(Role.ROLE_LIMIT_EDIT)));
  }

}
