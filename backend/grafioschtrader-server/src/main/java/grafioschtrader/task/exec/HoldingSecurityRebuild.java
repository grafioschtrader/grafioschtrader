package grafioschtrader.task.exec;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import grafioschtrader.repository.HoldSecurityaccountSecurityJpaRepository;
import grafioschtrader.task.ITask;
import grafioschtrader.types.TaskType;

/**
 * When a security split happens it influences the calculation of the performance. History quotes prices
 * are adjusted and holdings in transaction must respect this changed. The entity security holding gets
 * out dated. 
 *
 */
@Component
public class HoldingSecurityRebuild implements ITask {

  @Autowired
  HoldSecurityaccountSecurityJpaRepository holdSecurityaccountSecurityJpaRepository;
  
  @Override
  public TaskType getTaskType() {
    return TaskType.HOLDINGS_SECURITY_REBUILD;
  }

  @Override
  public void doWork(Integer idEntity, String entity) {
    holdSecurityaccountSecurityJpaRepository.rebuildHoldingsForSecurity(idEntity);
  }

}
