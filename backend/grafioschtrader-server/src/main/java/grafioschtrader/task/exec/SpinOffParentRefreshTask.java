package grafioschtrader.task.exec;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import grafiosch.entities.TaskDataChange;
import grafiosch.repository.TaskDataChangeJpaRepository;
import grafiosch.task.ITask;
import grafiosch.types.ITaskType;
import grafiosch.types.TaskDataExecPriority;
import grafioschtrader.entities.Security;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.repository.SecuritysplitJpaRepository;
import grafioschtrader.types.TaskTypeExtended;

/**
 * Brings splits and history of a spin-off parent in line with its recorded spin-offs, after a spin-off was recorded or
 * deleted or its history basis was set:
 * <ol>
 * <li>reads the splits, which drops or restores the provider split of each action date and writes its factor;</li>
 * <li>reloads the history in full, which first decides the history basis of every spin-off and then stores the
 * delivered quotes in the unadjusted basis;</li>
 * <li>enqueues the rebuild of the parent's holdings.</li>
 * </ol>
 * The reload runs synchronously, so that preview and apply, which are refused while this task waits or runs, never
 * price legs against a history that is still being replaced.
 */
@Component
public class SpinOffParentRefreshTask implements ITask {

  private final SecurityJpaRepository securityJpaRepository;
  private final SecuritysplitJpaRepository securitysplitJpaRepository;
  private final TaskDataChangeJpaRepository taskDataChangeJpaRepository;
  private final PlatformTransactionManager platformTransactionManager;

  public SpinOffParentRefreshTask(SecurityJpaRepository securityJpaRepository,
      SecuritysplitJpaRepository securitysplitJpaRepository, TaskDataChangeJpaRepository taskDataChangeJpaRepository,
      PlatformTransactionManager platformTransactionManager) {
    this.securityJpaRepository = securityJpaRepository;
    this.securitysplitJpaRepository = securitysplitJpaRepository;
    this.taskDataChangeJpaRepository = taskDataChangeJpaRepository;
    this.platformTransactionManager = platformTransactionManager;
  }

  @Override
  public ITaskType getTaskType() {
    return TaskTypeExtended.SPIN_OFF_PARENT_REFRESH;
  }

  @Override
  public List<String> getAllowedEntities() {
    return Arrays.asList(Security.class.getSimpleName());
  }

  @Override
  public void doWork(TaskDataChange taskDataChange) {
    TransactionTemplate transaction = new TransactionTemplate(platformTransactionManager);
    Integer idParent = taskDataChange.getIdEntity();
    boolean exists = Boolean.TRUE.equals(transaction.execute(_ -> securityJpaRepository.findById(idParent)
        .map(parent -> {
          if (parent.getIdConnectorSplit() != null) {
            securitysplitJpaRepository.loadSplitDataOfSpinOffParent(parent);
          }
          return true;
        }).orElse(false)));
    if (!exists) {
      return;
    }
    transaction.executeWithoutResult(_ -> securityJpaRepository.findById(idParent)
        .ifPresent(securityJpaRepository::rebuildSecurityCurrencypairHisotry));
    taskDataChangeJpaRepository.save(new TaskDataChange(TaskTypeExtended.HOLDINGS_SECURITY_REBUILD,
        TaskDataExecPriority.PRIO_NORMAL, LocalDateTime.now(), idParent, Security.class.getSimpleName()));
  }
}
