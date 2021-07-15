package grafioschtrader.task.exec;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.entities.Security;
import grafioschtrader.entities.TaskDataChange;
import grafioschtrader.exceptions.TaskBackgroundException;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.repository.SecuritysplitJpaRepository;
import grafioschtrader.task.ITask;
import grafioschtrader.types.TaskType;

/**
 * Normally called when the split data connector is changed or thru split
 * calendar a possible update was detected.
 *
 */
@Component
public class UpdateSplitForSecurityTask implements ITask {

  @Autowired
  private SecuritysplitJpaRepository securitysplitJpaRepository;

  @Autowired
  private SecurityJpaRepository securityJpaRepository;

  @Override
  public TaskType getTaskType() {
    return TaskType.SECURITY_SPLIT_UPDATE_FOR_SECURITY;
  }

  @Override
  public List<String> getAllowedEntities() {
    return Arrays.asList(Security.class.getSimpleName());
  }

  @Override
  @Transactional
  public void doWork(TaskDataChange taskDataChange) {
    Security security = securityJpaRepository.getById(taskDataChange.getIdEntity());
    if (security.getIdConnectorSplit() != null) {
      List<String> errorMessages = securitysplitJpaRepository.loadAllSplitDataFromConnector(security);
      if (!errorMessages.isEmpty()) {
        throw new TaskBackgroundException("gt.split.connector.failure", errorMessages);
      }

    } else {
      throw new TaskBackgroundException("gt.split.connector.notfound");
    }
  }

}
