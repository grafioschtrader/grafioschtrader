package grafioschtrader.task.exec;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.entities.TaskDataChange;
import grafiosch.exceptions.TaskBackgroundException;
import grafiosch.task.ITask;
import grafiosch.types.ITaskType;
import grafioschtrader.GlobalConstants;
import grafioschtrader.entities.Security;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.repository.SecuritysplitJpaRepository;
import grafioschtrader.types.TaskTypeExtended;

/**
 * Is triggered if the data connector of the split has been changed or if a
 * possible new split has been detected in the split calendar. It reloads all
 * splits for a specific security. If the instrument's splits have been changed,
 * the historical price data is also completely reloaded.
 */
@Component
public class UpdateSplitForSecurityTask implements ITask {

  @Autowired
  private SecuritysplitJpaRepository securitysplitJpaRepository;

  @Autowired
  private SecurityJpaRepository securityJpaRepository;

  @Override
  public ITaskType getTaskType() {
    return TaskTypeExtended.SECURITY_SPLIT_UPDATE_FOR_SECURITY;
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
        // If the split connector has been changed, the split date is zero.
        Date requestSplitDate = taskDataChange.getOldValueString() == null ? null
            : new SimpleDateFormat(GlobalConstants.SHORT_STANDARD_DATE_FORMAT)
                .parse(taskDataChange.getOldValueString());
        List<String> errorMessages = securitysplitJpaRepository.loadAllSplitDataFromConnectorForSecurity(security,
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
