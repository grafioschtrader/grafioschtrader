package grafioschtrader.task.exec;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
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
 * Is triggered if the data connector of the split has been changed or if a possible new split has been detected in the
 * split calendar. It reloads all splits for a specific security. If the instrument's splits have been changed, the
 * historical price data is also completely reloaded.
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
        LocalDate requestSplitDate = parseRequestedSplitDate(taskDataChange.getOldValueString());
        List<String> errorMessages = securitysplitJpaRepository.loadAllSplitDataFromConnectorForSecurity(security,
            requestSplitDate);
        if (!errorMessages.isEmpty()) {
          throw new TaskBackgroundException("gt.split.connector.failure", errorMessages, false);
        }
      } catch (DateTimeParseException e) {
        throw new TaskBackgroundException("gt.split.date.missing.parse", false);
      }
    } else {
      throw new TaskBackgroundException("gt.split.connector.notfound", false);
    }
  }

  /**
   * Formats the expected split date for the old value of this task. Every producer of the task must use it, because
   * {@link #parseRequestedSplitDate(String)} reads the value back.
   *
   * @param requestedSplitDate the split date announced by a split calendar
   * @return the date in the format {@link GlobalConstants#SHORT_STANDARD_DATE_FORMAT}
   */
  public static String formatRequestedSplitDate(LocalDate requestedSplitDate) {
    return requestedSplitDate.format(DateTimeFormatter.ofPattern(GlobalConstants.SHORT_STANDARD_DATE_FORMAT));
  }

  /**
   * Reads the expected split date from the old value of this task. Besides the format written by
   * {@link #formatRequestedSplitDate(LocalDate)} the ISO format is accepted, because older versions wrote it for the
   * repeated attempt and such tasks may still be waiting in the database.
   *
   * @param oldValueString the old value of the task, null when the split connector was changed
   * @return the expected split date, or null when no split date was requested
   * @throws DateTimeParseException if the value is in neither format
   */
  public static LocalDate parseRequestedSplitDate(String oldValueString) {
    if (oldValueString == null) {
      return null;
    }
    return oldValueString.indexOf('-') > 0 ? LocalDate.parse(oldValueString)
        : LocalDate.parse(oldValueString, DateTimeFormatter.ofPattern(GlobalConstants.SHORT_STANDARD_DATE_FORMAT));
  }

}
