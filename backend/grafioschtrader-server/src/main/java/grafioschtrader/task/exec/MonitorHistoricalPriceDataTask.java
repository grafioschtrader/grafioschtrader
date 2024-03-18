package grafioschtrader.task.exec;

import java.time.LocalDate;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import grafioschtrader.GlobalConstants;
import grafioschtrader.alert.AlertType;
import grafioschtrader.connector.instrument.IFeedConnector.FeedSupport;
import grafioschtrader.entities.TaskDataChange;
import grafioschtrader.exceptions.TaskBackgroundException;
import grafioschtrader.repository.SecurityJpaRepository.MonitorFailedConnector;
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
public class MonitorHistoricalPriceDataTask extends MonitorPriceData implements ITask {

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
    List<MonitorFailedConnector> monitorFaliedConnectors = securityJpaRepository.getFailedHistoryConnector(
        LocalDate.now().minusDays(globalparametersJpaRepository.getHistoryObservationDaysBack()),
        globalparametersJpaRepository.getMaxHistoryRetry()
            - globalparametersJpaRepository.getHistoryObservationRetryMinus(),
        globalparametersJpaRepository.getHistoryObservationFallingPercentage());
    if (!monitorFaliedConnectors.isEmpty()) {
      generateMessageAndPublishAlert(AlertType.ALERT_CONNECTOR_EOD_MAY_NOT_WORK_ANYMORE, monitorFaliedConnectors,
          FeedSupport.FS_HISTORY);
    }
  }

}
