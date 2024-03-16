package grafioschtrader.task.exec;

import java.time.LocalDate;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import grafioschtrader.GlobalConstants;
import grafioschtrader.alert.AlertEvent;
import grafioschtrader.alert.AlertType;
import grafioschtrader.connector.ConnectorHelper;
import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.connector.instrument.IFeedConnector.FeedSupport;
import grafioschtrader.entities.TaskDataChange;
import grafioschtrader.exceptions.TaskBackgroundException;
import grafioschtrader.repository.GlobalparametersJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository.FailedHistoricalConnector;
import grafioschtrader.repository.TaskDataChangeJpaRepository;
import grafioschtrader.task.ITask;
import grafioschtrader.types.TaskDataExecPriority;
import grafioschtrader.types.TaskType;

/**
 * Checks whether a connector for historical price data may no longer be
 * working. Certain parameters of the check can be adjusted via the global
 * settings. In the event of a possible malfunction of a connector, the main
 * administrator receives a message. This job should be carried out daily.
 */
@Component
public class MonitorHistoricalPriceDataTask implements ITask {

  @Autowired
  private SecurityJpaRepository securityJpaRepository;

  @Autowired
  private GlobalparametersJpaRepository globalparametersJpaRepository;

  @Autowired
  private TaskDataChangeJpaRepository taskDataChangeRepository;

  @Autowired
  private ApplicationEventPublisher applicationEventPublisher;

  @Scheduled(cron = "${gt.eod.cron.monitor.quotation}", zone = GlobalConstants.TIME_ZONE)
  public void monitorHistoricalPriceDataConnectors() {
    TaskDataChange taskDataChange = new TaskDataChange(getTaskType(), TaskDataExecPriority.PRIO_NORMAL);
    taskDataChangeRepository.save(taskDataChange);
  }

  @Override
  public TaskType getTaskType() {
    return TaskType.MONITOR_HISTORICAL_PRICE_DATA;
  }

  @Override
  public void doWork(TaskDataChange taskDataChange) throws TaskBackgroundException {
    List<FailedHistoricalConnector> failedHistoricalConnectors = securityJpaRepository.getFailedHistoryConnector(
        LocalDate.now().minusDays(globalparametersJpaRepository.getHistoryObeservationDaysBack()),
        globalparametersJpaRepository.getMaxHistoryRetry()
            - globalparametersJpaRepository.getHistoryObeservationRetryMinus(),
        globalparametersJpaRepository.getHistoryObeservationFallingPercentage());
    if (!failedHistoricalConnectors.isEmpty()) {
      generateMessageAndPublishAlert(failedHistoricalConnectors);
    }
  }

  private void generateMessageAndPublishAlert(List<FailedHistoricalConnector> failedHistoricalConnectors) {
    String messageArg = String.format(
        GlobalConstants.RETURN_AND_NEW_LINE + "%-30s %6s %6s %3s" + GlobalConstants.RETURN_AND_NEW_LINE, "Connector",
        "Total", "Error", "%");
    for (FailedHistoricalConnector fhc : failedHistoricalConnectors) {
      IFeedConnector ifeedConnector = ConnectorHelper.getConnectorByConnectorId(
          securityJpaRepository.getFeedConnectors(), fhc.getConnector(), FeedSupport.FS_HISTORY);
      messageArg += String.format("%-30s %6d %6d %3d" + GlobalConstants.RETURN_AND_NEW_LINE,
          ifeedConnector.getReadableName(), fhc.getTotal(), fhc.getFailed(), fhc.getPercentageFailed());
    }
    applicationEventPublisher
        .publishEvent(new AlertEvent(this, AlertType.ALERT_CONNECTOR_EOD_MAY_NOT_WORK_ANYMODE, messageArg));
  }

}
