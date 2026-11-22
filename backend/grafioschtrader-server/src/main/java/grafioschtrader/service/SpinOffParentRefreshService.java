package grafioschtrader.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import grafiosch.entities.TaskDataChange;
import grafiosch.exceptions.DataViolationException;
import grafiosch.repository.TaskDataChangeJpaRepository;
import grafiosch.types.ProgressStateType;
import grafiosch.types.TaskDataExecPriority;
import grafioschtrader.entities.Security;
import grafioschtrader.types.TaskTypeExtended;

/**
 * Enqueues the refresh of a spin-off parent and tells whether one is still outstanding. While it waits or runs, the
 * parent's splits and history may not yet reflect its recorded spin-offs, so their legs must not be priced.
 */
@Service
public class SpinOffParentRefreshService {

  private final TaskDataChangeJpaRepository taskDataChangeJpaRepository;

  public SpinOffParentRefreshService(TaskDataChangeJpaRepository taskDataChangeJpaRepository) {
    this.taskDataChangeJpaRepository = taskDataChangeJpaRepository;
  }

  /**
   * Enqueues {@link TaskTypeExtended#SPIN_OFF_PARENT_REFRESH} for the parent unless one is already waiting.
   *
   * @param idParent the parent security
   */
  public void enqueue(Integer idParent) {
    if (!taskDataChangeJpaRepository.existsByIdTaskAndIdEntityAndProgressStateType(
        TaskTypeExtended.SPIN_OFF_PARENT_REFRESH.getValue(), idParent, ProgressStateType.PROG_WAITING.getValue())) {
      taskDataChangeJpaRepository.save(new TaskDataChange(TaskTypeExtended.SPIN_OFF_PARENT_REFRESH,
          TaskDataExecPriority.PRIO_NORMAL, LocalDateTime.now(), idParent, Security.class.getSimpleName()));
    }
  }

  /**
   * Tells whether a refresh of the parent is waiting or running.
   *
   * @param idParent the parent security
   * @return true while the refresh is outstanding
   */
  public boolean isPending(Integer idParent) {
    byte task = TaskTypeExtended.SPIN_OFF_PARENT_REFRESH.getValue();
    return taskDataChangeJpaRepository.existsByIdTaskAndIdEntityAndProgressStateType(task, idParent,
        ProgressStateType.PROG_WAITING.getValue())
        || taskDataChangeJpaRepository.existsByIdTaskAndIdEntityAndProgressStateType(task, idParent,
            ProgressStateType.PROG_RUNNING.getValue());
  }

  /**
   * Refuses pricing the legs of a spin-off of this parent while its refresh is outstanding.
   *
   * @param idParent the parent security
   */
  public void requireNotPending(Integer idParent) {
    if (isPending(idParent)) {
      throw new DataViolationException("action.date", "gt.security.action.spinoff.refresh.pending", null);
    }
  }
}
