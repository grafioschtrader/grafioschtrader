package grafioschtrader.task.exec;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
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
import grafioschtrader.repository.CurrencypairJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository.MonitorFailedConnector;
import grafioschtrader.types.TaskTypeExtended;

/**
 * Checks whether a connector for intraday price data may no longer be working. Certain parameters of the check can be
 * adjusted via the global settings. In the event of a possible malfunction of a connector, the main administrator
 * receives a message. This job should be carried out daily.
 */
@Component
public class MonitorIntradayPriceDataTask extends MonitorPriceData implements ITask {

  @Autowired
  private CurrencypairJpaRepository currencypairJpaRepository;

  @Autowired
  private SecurityJpaRepository securityJpaRepository;

  @Scheduled(cron = "${gt.intraday.cron.monitor.quotation}", zone = BaseConstants.TIME_ZONE)
  public void monitorIntradayPriceDataConnectors() {
    TaskDataChange taskDataChange = new TaskDataChange(getTaskType(), TaskDataExecPriority.PRIO_NORMAL);
    taskDataChangeRepository.save(taskDataChange);
  }

  @Override
  public ITaskType getTaskType() {
    return TaskTypeExtended.MONITOR_INTRADAY_PRICE_DATA;
  }

  @Override
  public void doWork(TaskDataChange taskDataChange) throws TaskBackgroundException {
    int leave = 0;
    List<MonitorFailedConnector> monitorFailedConnectors = Collections.emptyList();
    do {
      monitorFailedConnectors = securityJpaRepository.getFailedIntradayConnector(
          LocalDate.now().minusDays(globalparametersService.getIntradayObservationOrDaysBack()),
          globalparametersService.getMaxIntraRetry() - globalparametersService.getIntradayObservationRetryMinus(),
          globalparametersService.getIntradayObservationFallingPercentage());
      if (!monitorFailedConnectors.isEmpty()) {
        if (leave != 0) {
          generateMessageAndPublishAlert(AlertGTType.ALERT_CONNECTOR_INTRADAY_MAY_NOT_WORK_ANYMORE,
              monitorFailedConnectors, FeedSupport.FS_INTRA);
        } else {
          updateIntraday();
        }
      }
      leave++;
    } while (!monitorFailedConnectors.isEmpty() && leave == 1);
  }

  private void updateIntraday() {
    currencypairJpaRepository.updateAllLastPrices();
    securityJpaRepository.updateAllLastPrices();
  }
}
