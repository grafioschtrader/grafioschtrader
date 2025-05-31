package grafioschtrader.task.exec;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import grafiosch.entities.TaskDataChange;
import grafiosch.task.ITask;
import grafiosch.types.ITaskType;
import grafioschtrader.repository.HoldSecurityaccountSecurityJpaRepository;
import grafioschtrader.types.TaskTypeExtended;

/**
 * When a security split happens it influences the calculation of the performance. History quotes prices are adjusted
 * and holdings in transaction must respect this changed. The entity security holding gets out dated.
 *
 */
@Component
public class HoldingSecurityRebuildTask implements ITask {

  @Autowired
  private HoldSecurityaccountSecurityJpaRepository holdSecurityaccountSecurityJpaRepository;

  @Override
  public ITaskType getTaskType() {
    return TaskTypeExtended.HOLDINGS_SECURITY_REBUILD;
  }

  @Override
  public void doWork(TaskDataChange taskDataChange) {
    holdSecurityaccountSecurityJpaRepository.rebuildHoldingsForSecurity(taskDataChange.getIdEntity());
  }

}
