package grafioschtrader.repository;

import java.lang.annotation.Annotation;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;

import grafiosch.entities.TaskDataChange;
import grafiosch.entities.User;
import grafiosch.repository.BaseRepositoryImpl;
import grafiosch.repository.TaskDataChangeJpaRepository;
import grafiosch.types.TaskDataExecPriority;
import grafiosch.types.ProgressStateType;
import grafioschtrader.entities.GTNetSecurityImpHead;
import grafioschtrader.types.TaskTypeExtended;
import jakarta.transaction.Transactional;

/**
 * Implementation of custom repository operations for GTNetSecurityImpHead.
 */
public class GTNetSecurityImpHeadJpaRepositoryImpl extends BaseRepositoryImpl<GTNetSecurityImpHead>
    implements GTNetSecurityImpHeadJpaRepositoryCustom {

  @Autowired
  private GTNetSecurityImpHeadJpaRepository gtNetSecurityImpHeadJpaRepository;

  @Autowired
  private TaskDataChangeJpaRepository taskDataChangeJpaRepository;

  @Override
  public GTNetSecurityImpHead saveOnlyAttributes(GTNetSecurityImpHead entity, GTNetSecurityImpHead existingEntity,
      Set<Class<? extends Annotation>> updatePropertyLevelClasses) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();

    GTNetSecurityImpHead saveEntity = entity;
    if (existingEntity != null) {
      // Update existing entity
      saveEntity = existingEntity;
      saveEntity.setName(entity.getName());
      saveEntity.setNote(entity.getNote());
    } else {
      // New entity - set tenant
      entity.setIdTenant(user.getIdTenant());
    }
    return gtNetSecurityImpHeadJpaRepository.save(saveEntity);
  }

  @Override
  @Transactional
  public int delEntityWithTenant(Integer id, Integer idTenant) {
    // The cascade delete in DB will handle positions automatically
    return gtNetSecurityImpHeadJpaRepository.deleteByIdGtNetSecurityImpHeadAndIdTenant(id, idTenant);
  }

  @Override
  @Transactional
  public boolean queueImportJobIfNotExists(Integer idGtNetSecurityImpHead, Integer idTenant, Integer idUser,
      Integer idTransactionHead) {
    // Verify the header belongs to the tenant
    GTNetSecurityImpHead head = gtNetSecurityImpHeadJpaRepository
        .findByIdGtNetSecurityImpHeadAndIdTenant(idGtNetSecurityImpHead, idTenant);
    if (head == null) {
      return false;
    }

    // Check for existing pending job
    Optional<TaskDataChange> existingJob = taskDataChangeJpaRepository.findByIdTaskAndIdEntityAndProgressStateType(
        TaskTypeExtended.GTNET_SECURITY_IMPORT_POSITIONS.getValue(),
        idGtNetSecurityImpHead,
        ProgressStateType.PROG_WAITING.getValue());

    if (existingJob.isPresent()) {
      return false; // Job already pending
    }

    // Create new task
    TaskDataChange task = new TaskDataChange(
        TaskTypeExtended.GTNET_SECURITY_IMPORT_POSITIONS,
        TaskDataExecPriority.PRIO_NORMAL,
        LocalDateTime.now(),
        idGtNetSecurityImpHead,
        GTNetSecurityImpHead.class.getSimpleName());

    // Store user ID for created_by field on imported securities
    if (idUser != null) {
      task.setOldValueNumber(idUser.doubleValue());
    }

    // Store import transaction head ID to indicate this came from import flow
    // When set, the task will auto-assign securities to matching ImportTransactionPos entries
    if (idTransactionHead != null) {
      task.setOldValueString(idTransactionHead.toString());
    }

    taskDataChangeJpaRepository.save(task);
    return true;
  }
}
