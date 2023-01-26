package grafioschtrader.rest;

import java.util.NoSuchElementException;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.security.core.context.SecurityContextHolder;

import grafioschtrader.GlobalConstants;
import grafioschtrader.common.UserAccessHelper;
import grafioschtrader.entities.Auditable;
import grafioschtrader.entities.BaseID;
import grafioschtrader.entities.User;
import grafioschtrader.types.OperationType;

/**
 * Delete for shared resources, depends on the rights of the user for removing
 * an entity.
 *
 * @param <T>
 */
public abstract class UpdateCreateDeleteAudit<T extends BaseID> extends UpdateCreateResource<T> {

  private final Logger log = LoggerFactory.getLogger(this.getClass());

  @Autowired
  private MessageSource messageSource;

  protected Optional<T> deleteById(final Integer id) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    Optional<T> entityOpt = getUpdateCreateJpaRepository().findById(id);
    if (entityOpt.isPresent()) {
      if (hasRightsForDeleteEntity(user, entityOpt.get())) {
        log.debug("Delete by id : {}", id);
        getUpdateCreateJpaRepository().deleteById(id);
        logAddUpdDel(user.getIdUser(), entityOpt.get(), OperationType.DELETE);
      } else {
        throw new SecurityException(GlobalConstants.RIGHTS_SECURITY_BREACH);
      }
    } else {
      // Entity not found
      throw new NoSuchElementException(
          messageSource.getMessage("entity.not.found", new Object[] { id }, user.createAndGetJavaLocale()));
    }
    return entityOpt;

  }

  protected boolean hasRightsForDeleteEntity(User user, T entity) {
    return UserAccessHelper.hasRightsOrPrivilegesForEditingOrDelete(user, (Auditable) entity);
  }
}
