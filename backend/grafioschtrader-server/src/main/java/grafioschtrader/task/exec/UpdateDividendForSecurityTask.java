package grafioschtrader.task.exec;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.entities.TaskDataChange;
import grafiosch.exceptions.TaskBackgroundException;
import grafiosch.task.ITask;
import grafiosch.types.ITaskType;
import grafioschtrader.entities.Security;
import grafioschtrader.repository.DividendJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.types.TaskTypeExtended;

/**
 * Normally called when the dividend data connector is changed.
 */
@Component
public class UpdateDividendForSecurityTask implements ITask {

  @Autowired
  private DividendJpaRepository dividendJpaRepository;

  @Autowired
  private SecurityJpaRepository securityJpaRepository;

  @Override
  public ITaskType getTaskType() {
    return TaskTypeExtended.SECURITY_DIVIDEND_UPDATE_FOR_SECURITY;
  }

  @Override
  public List<String> getAllowedEntities() {
    return Arrays.asList(Security.class.getSimpleName());
  }

  @Override
  @Transactional
  public void doWork(TaskDataChange taskDataChange) throws TaskBackgroundException {
    Security security = securityJpaRepository.getReferenceById(taskDataChange.getIdEntity());
    if (security.getIdConnectorDividend() != null) {
      List<String> errorMessages = dividendJpaRepository.loadAllDividendDataFromConnector(security);
      if (!errorMessages.isEmpty()) {
        throw new TaskBackgroundException("gt.dividend.connector.failure", errorMessages, false);
      }
    } else {
      throw new TaskBackgroundException("gt.dividend.connector.notfound", false);
    }
  }

}
