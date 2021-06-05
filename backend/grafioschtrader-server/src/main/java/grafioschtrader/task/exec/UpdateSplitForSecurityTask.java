package grafioschtrader.task.exec;

import java.text.ParseException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.GlobalConstants;
import grafioschtrader.connector.ConnectorHelper;
import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitysplit;
import grafioschtrader.entities.TaskDataChange;
import grafioschtrader.exceptions.TaskBackgroundException;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository.SplitAdjustedHistoryquotes;
import grafioschtrader.repository.SecuritysplitJpaRepository;
import grafioschtrader.repository.TaskDataChangeJpaRepository;
import grafioschtrader.task.ITask;
import grafioschtrader.types.CreateType;
import grafioschtrader.types.TaskDataExecPriority;
import grafioschtrader.types.TaskType;

/**
 * Normally called when the split data connector is changed or thru split
 * calendar a possible update was detected.
 *
 */
@Component
public class UpdateSplitForSecurityTask extends UpdateDividendSplitForSecurity<Securitysplit> implements ITask {

  @Autowired
  private SecuritysplitJpaRepository securitysplitJpaRepository;

  @Autowired
  private TaskDataChangeJpaRepository taskDataChangeJpaRepository;

  @Autowired
  private SecurityJpaRepository securityJpaRepository;

  private final Logger log = LoggerFactory.getLogger(this.getClass());

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
    Security security = securityJpaRepository.getOne(taskDataChange.getIdEntity());
    if (security.getIdConnectorSplit() != null) {
      List<String> errorMessages = loadSplitData(security);
      if (!errorMessages.isEmpty()) {
        throw new TaskBackgroundException("gt.split.connector.failure", errorMessages);
      }

    } else {
      throw new TaskBackgroundException("gt.split.connector.notfound");
    }
  }

  private List<String> loadSplitData(Security security) {
    List<String> errorMessages = new ArrayList<>();
    short retrySplitLoad = security.getRetrySplitLoad();
    try {
      IFeedConnector connector = ConnectorHelper.getConnectorByConnectorId(feedConnectors,
          security.getIdConnectorSplit(), IFeedConnector.FeedSupport.SPLIT);
      List<Securitysplit> securitysplitsRead = connector.getSplitHistory(security,
          LocalDate.parse(GlobalConstants.OLDEST_TRADING_DAY));
      updateSplitData(security, securitysplitsRead);
      retrySplitLoad = 0;
    } catch (ParseException pe) {
      log.error(pe.getMessage() + "Offset: " + pe.getErrorOffset(), pe);
      errorMessages.add(pe.getMessage());
    } catch (final Exception ex) {
      retrySplitLoad++;
      log.error(ex.getMessage() + " " + security, ex);
      errorMessages.add(ex.getMessage());
    }
    security.setRetrySplitLoad(retrySplitLoad);
    securityJpaRepository.save(security);
    return errorMessages;
  }

  private void updateSplitData(Security security, List<Securitysplit> securitysplitsRead) throws Exception {
    securitysplitJpaRepository.deleteByIdSecuritycurrencyAndCreateType(security.getIdSecuritycurrency(),
        CreateType.CONNECTOR_CREATED.getValue());
    List<Securitysplit> existingSplits = securitysplitJpaRepository
        .findByIdSecuritycurrencyOrderBySplitDateAsc(security.getIdSecuritycurrency());
    List<Securitysplit> createdSplits = updateDividendSplitData(security, securitysplitsRead, existingSplits,
        this.securitysplitJpaRepository);
    Optional<Date> maxSplitDate = createdSplits.stream().map(Securitysplit::getSplitDate).max(Date::compareTo);

    if (securityJpaRepository.isYoungestSplitHistoryquotePossibleAdjusted(security, securitysplitsRead,
        true) == SplitAdjustedHistoryquotes.ADJUSTED) {
      if (!maxSplitDate.isEmpty() && security.getFullLoadTimestamp() != null
          && maxSplitDate.get().after(security.getFullLoadTimestamp())) {
        securityJpaRepository.reloadAsyncFullHistoryquote(security);
      }
      if (!createdSplits.isEmpty()) {
        taskDataChangeJpaRepository
            .save(new TaskDataChange(TaskType.HOLDINGS_SECURITY_REBUILD, TaskDataExecPriority.PRIO_NORMAL,
                LocalDateTime.now(), security.getIdSecuritycurrency(), Security.class.getSimpleName()));
      }
    } else {
      taskDataChangeJpaRepository.save(
          new TaskDataChange(TaskType.CHECK_RELOAD_SECURITY_ADJUSTED_HISTORICAL_PRICES, TaskDataExecPriority.PRIO_LOW,
              LocalDateTime.now().plusDays(1L), security.getIdSecuritycurrency(), Security.class.getSimpleName()));
    }

  }

}
