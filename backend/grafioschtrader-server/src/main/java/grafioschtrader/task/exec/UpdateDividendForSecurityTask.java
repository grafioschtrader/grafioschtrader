package grafioschtrader.task.exec;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.entities.Security;
import grafioschtrader.entities.TaskDataChange;
import grafioschtrader.exceptions.TaskBackgroundException;
import grafioschtrader.repository.DividendJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.task.ITask;
import grafioschtrader.types.TaskType;

/**
 * Normally called when the dividend data connector is changed or thru dividend
 * calendar calendar a possible update was detected.
 *
 */
@Component
public class UpdateDividendForSecurityTask implements ITask {

  @Autowired
  private DividendJpaRepository dividendJpaRepository;

  @Autowired
  private SecurityJpaRepository securityJpaRepository;

  @Override
  public TaskType getTaskType() {
    return TaskType.SECURITY_DIVIDEND_UPDATE_FOR_SECURITY;
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
