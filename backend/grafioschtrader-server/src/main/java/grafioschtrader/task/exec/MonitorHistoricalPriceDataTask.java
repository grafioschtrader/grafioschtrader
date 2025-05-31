package grafioschtrader.task.exec;

import java.time.LocalDate;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import grafiosch.BaseConstants;
import grafiosch.entities.TaskDataChange;
import grafiosch.exceptions.TaskBackgroundException;
import grafiosch.task.ITask;
import grafiosch.types.ITaskType;
import grafiosch.types.TaskDataExecPriority;
import grafioschtrader.alert.AlertGTType;
import grafioschtrader.connector.instrument.IFeedConnector.FeedSupport;
import grafioschtrader.repository.SecurityJpaRepository.MonitorFailedConnector;
import grafioschtrader.types.TaskTypeExtended;

/**
 * Checks whether a connector for historical price data may no longer be working. Certain parameters of the check can be
 * adjusted via the global settings. In the event of a possible malfunction of a connector, the main administrator
 * receives a message. This job should be carried out daily.
 */
@Component
public class MonitorHistoricalPriceDataTask extends MonitorPriceData implements ITask {

  @Scheduled(cron = "${gt.eod.cron.monitor.quotation}", zone = BaseConstants.TIME_ZONE)
  public void monitorHistoricalPriceDataConnectors() {
    TaskDataChange taskDataChange = new TaskDataChange(getTaskType(), TaskDataExecPriority.PRIO_NORMAL);
    taskDataChangeRepository.save(taskDataChange);
  }

  @Override
  public ITaskType getTaskType() {
    return TaskTypeExtended.MONITOR_HISTORICAL_PRICE_DATA;
  }

  @Override
  public void doWork(TaskDataChange taskDataChange) throws TaskBackgroundException {
    List<MonitorFailedConnector> monitorFaliedConnectors = securityJpaRepository.getFailedHistoryConnector(
        LocalDate.now().minusDays(globalparametersService.getHistoryObservationDaysBack()),
        globalparametersService.getMaxHistoryRetry() - globalparametersService.getHistoryObservationRetryMinus(),
        globalparametersService.getHistoryObservationFallingPercentage());
    if (!monitorFaliedConnectors.isEmpty()) {
      generateMessageAndPublishAlert(AlertGTType.ALERT_CONNECTOR_EOD_MAY_NOT_WORK_ANYMORE, monitorFaliedConnectors,
          FeedSupport.FS_HISTORY);
    }
  }

}
