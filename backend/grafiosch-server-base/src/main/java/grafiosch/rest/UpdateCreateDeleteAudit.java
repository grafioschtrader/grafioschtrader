package grafiosch.rest;

import java.util.NoSuchElementException;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.security.core.context.SecurityContextHolder;

import grafiosch.BaseConstants;
import grafiosch.common.UserAccessHelper;
import grafiosch.entities.Auditable;
import grafiosch.entities.BaseID;
import grafiosch.entities.User;
import grafiosch.types.OperationType;

/**
 * Abstract REST controller that extends {@link UpdateCreateResource} to include functionality for deleting entities
 * with auditing and rights checking. It is designed for shared resources where delete operations depend on the
 * authenticated user's permissions.
 *
 * @param <T> The type of the entity to be managed, which must extend {@link BaseID} and typically should be an instance
 *            of {@link Auditable} for rights checking.
 */
public abstract class UpdateCreateDeleteAudit<T extends BaseID<Integer>> extends UpdateCreateResource<T> {

  private final Logger log = LoggerFactory.getLogger(this.getClass());

  @Autowired
  private MessageSource messageSource;

  /**
   * Deletes an entity by its ID after verifying the current user's rights. If the entity is found and the user has
   * deletion rights, it is deleted from the repository and the operation is logged.
   *
   * @param id The ID of the entity to delete.
   * @return An {@link Optional} containing the deleted entity if found and deleted, otherwise an empty Optional (though
   *         exceptions are thrown for not found or no rights).
   * @throws SecurityException      If the authenticated user does not have sufficient rights to delete the entity.
   * @throws NoSuchElementException If no entity with the given ID is found.
   */
  protected Optional<T> deleteById(final Integer id) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    Optional<T> entityOpt = getUpdateCreateJpaRepository().findById(id);
    if (entityOpt.isPresent()) {
      if (hasRightsForDeleteEntity(user, entityOpt.get())) {
        log.debug("Delete {} by id : {}", entityOpt.get().getClass().getSimpleName(), id);
        getUpdateCreateJpaRepository().deleteById(id);
        logAddUpdDel(user.getIdUser(), entityOpt.get(), OperationType.DELETE);
      } else {
        throw new SecurityException(BaseConstants.RIGHTS_SECURITY_BREACH);
      }
    } else {
      // Entity not found
      throw new NoSuchElementException(
          messageSource.getMessage("entity.not.found", new Object[] { id }, user.createAndGetJavaLocale()));
    }
    return entityOpt;

  }

  /**
   * Checks if the given user has the necessary rights to delete the specified entity. This typically involves checking
   * if the user is the owner or has administrative privileges.
   *
   * @param user   The user whose rights are to be checked.
   * @param entity The entity to be deleted.
   * @return {@code true} if the user has rights to delete the entity, {@code false} otherwise. The default
   *         implementation casts the entity to {@link Auditable} for rights checking.
   */
  protected boolean hasRightsForDeleteEntity(User user, T entity) {
    return UserAccessHelper.hasRightsOrPrivilegesForEditingOrDelete(user, (Auditable) entity);
  }

  protected void logAndAudit(Class<T> zclass, Integer id) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    log.debug("Delete {} by id : {}", zclass.getSimpleName(), id);
    logAddUpdDel(user.getIdUser(), zclass, OperationType.DELETE);
  }
}
