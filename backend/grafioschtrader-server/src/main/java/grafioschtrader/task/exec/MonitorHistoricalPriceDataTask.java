package grafioschtrader.task.exec;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import grafioschtrader.entities.TaskDataChange;
import grafioschtrader.exceptions.TaskBackgroundException;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.task.ITask;
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

  @Override
  public TaskType getTaskType() {
    return TaskType.MONITOR_HISTORICAL_PRICE_DATA;
  }

  @Override
  public void doWork(TaskDataChange taskDataChange) throws TaskBackgroundException {
    // TODO Auto-generated method stub

  }

}
