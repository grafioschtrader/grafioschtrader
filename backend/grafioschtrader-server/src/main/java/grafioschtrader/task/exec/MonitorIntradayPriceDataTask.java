package grafioschtrader.task.exec;

import java.time.LocalDate;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import grafioschtrader.GlobalConstants;
import grafioschtrader.alert.AlertType;
import grafioschtrader.entities.TaskDataChange;
import grafioschtrader.exceptions.TaskBackgroundException;
import grafioschtrader.repository.SecurityJpaRepository.MonitorFailedConnector;
import grafioschtrader.task.ITask;
import grafioschtrader.types.TaskDataExecPriority;
import grafioschtrader.types.TaskType;

/**
 * Checks whether a connector for intraday price data may no longer be
 * working. Certain parameters of the check can be adjusted via the global
 * settings. In the event of a possible malfunction of a connector, the main
 * administrator receives a message. This job should be carried out daily.
 */
@Component
public class MonitorIntradayPriceDataTask extends MonitorPriceData implements ITask {

  @Scheduled(cron = "${gt.intraday.cron.monitor.quotation}", zone = GlobalConstants.TIME_ZONE)
  public void monitorIntradayPriceDataConnectors() {
    TaskDataChange taskDataChange = new TaskDataChange(getTaskType(), TaskDataExecPriority.PRIO_NORMAL);
    taskDataChangeRepository.save(taskDataChange);
  }
  
  @Override
  public TaskType getTaskType() {
    return TaskType.MONITOR_INTRADAY_PRICE_DATA;
  }
  
  @Override
  public void doWork(TaskDataChange taskDataChange) throws TaskBackgroundException {
    List<MonitorFailedConnector> monitorFailedConnectors = securityJpaRepository.getFailedIntradayConnector(
        LocalDate.now().minusDays(globalparametersJpaRepository.getIntradayObservationOrDaysBack()),
        globalparametersJpaRepository.getMaxIntraRetry()
            - globalparametersJpaRepository.getIntradayObservationRetryMinus(),
        globalparametersJpaRepository.getIntradayObservationFallingPercentage());
    if (!monitorFailedConnectors.isEmpty()) {
      generateMessageAndPublishAlert(AlertType.ALERT_CONNECTOR_INTRADAY_MAY_NOT_WORK_ANYMORE, monitorFailedConnectors);
    }
  }
}
