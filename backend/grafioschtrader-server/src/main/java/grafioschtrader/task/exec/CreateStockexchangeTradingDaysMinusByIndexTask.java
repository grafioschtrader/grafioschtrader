package grafioschtrader.task.exec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.GlobalConstants;
import grafioschtrader.entities.TaskDataChange;
import grafioschtrader.repository.TaskDataChangeJpaRepository;
import grafioschtrader.repository.TradingDaysMinusJpaRepository;
import grafioschtrader.task.ITask;
import grafioschtrader.types.TaskDataExecPriority;
import grafioschtrader.types.TaskType;

@Component
public class CreateStockexchangeTradingDaysMinusByIndexTask implements ITask {

  private final Logger log = LoggerFactory.getLogger(this.getClass());

  @Autowired
  private TaskDataChangeJpaRepository taskDataChangeRepository;

  @Autowired
  private TradingDaysMinusJpaRepository tradingDaysMinusJpaRepository;

  @Scheduled(cron = "${gt.calendar.update.index}", zone = GlobalConstants.TIME_ZONE)
  public void catchAllUpSecuritycurrencyHistoryquote() {
    TaskDataChange taskDataChange = new TaskDataChange(TaskType.CREATE_STOCK_EXCHANGE_CALENDAR_BY_INDEX,
        TaskDataExecPriority.PRIO_NORMAL);
    taskDataChangeRepository.save(taskDataChange);
  }

  @Override
  public TaskType getTaskType() {
    return TaskType.CREATE_STOCK_EXCHANGE_CALENDAR_BY_INDEX;
  }

  @Override
  @Transactional
  public void doWork(TaskDataChange taskDataChange) {
    tradingDaysMinusJpaRepository.updCalendarStockexchangeByIndex();
    log.info("Update stock exchange tranding calendars");
  }

}
