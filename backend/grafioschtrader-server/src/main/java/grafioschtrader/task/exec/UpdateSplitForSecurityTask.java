package grafioschtrader.task.exec;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.GlobalConstants;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.TaskDataChange;
import grafioschtrader.exceptions.TaskBackgroundException;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.repository.SecuritysplitJpaRepository;
import grafioschtrader.task.ITask;
import grafioschtrader.types.TaskType;

/**
 * Normally called when the split data connector is changed or thru split
 * calendar a possible update was detected. It reloads all splits for a
 * specified security.
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
  public void doWork(TaskDataChange taskDataChange) throws TaskBackgroundException {
    Security security = securityJpaRepository.getReferenceById(taskDataChange.getIdEntity());
    if (security.getIdConnectorSplit() != null) {
      try {
        Date requestSplitDate = taskDataChange.getOldValueString() == null ? null
            : new SimpleDateFormat(GlobalConstants.SHORT_STANDARD_DATE_FORMAT)
                .parse(taskDataChange.getOldValueString());
        List<String> errorMessages = securitysplitJpaRepository.loadAllSplitDataFromConnector(security,
            requestSplitDate);
        if (!errorMessages.isEmpty()) {
          throw new TaskBackgroundException("gt.split.connector.failure", errorMessages, false);
        }
      } catch (ParseException e) {
        throw new TaskBackgroundException("gt.split.date.missing.parse", false);
      }
    } else {
      throw new TaskBackgroundException("gt.split.connector.notfound", false);
    }
  }

}
