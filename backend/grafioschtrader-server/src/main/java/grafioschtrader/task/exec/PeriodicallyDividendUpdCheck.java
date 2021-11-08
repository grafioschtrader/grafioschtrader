package grafioschtrader.task.exec;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.GlobalConstants;
import grafioschtrader.entities.TaskDataChange;
import grafioschtrader.exceptions.TaskBackgroundException;
import grafioschtrader.repository.DividendJpaRepository;
import grafioschtrader.repository.TaskDataChangeJpaRepository;
import grafioschtrader.task.ITask;
import grafioschtrader.types.TaskDataExecPriority;
import grafioschtrader.types.TaskType;

@Component
public class PeriodicallyDividendUpdCheck implements ITask {

  @Autowired
  private DividendJpaRepository dividendJpaRepository;

  @Autowired
  private TaskDataChangeJpaRepository taskDataChangeRepository;

  @Scheduled(cron = "${gt.dividend.update.data}", zone = GlobalConstants.TIME_ZONE)
  public void catchAllUpSecuritycurrencyHistoryquote() {
    TaskDataChange taskDataChange = new TaskDataChange(TaskType.PERIODICALLY_DIVIDEND_UPDATE_CHECK,
        TaskDataExecPriority.PRIO_VERY_LOW);
    taskDataChangeRepository.save(taskDataChange);
  }

  @Override
  public TaskType getTaskType() {
    return TaskType.PERIODICALLY_DIVIDEND_UPDATE_CHECK;
  }

  @Override
  @Transactional
  public void doWork(TaskDataChange taskDataChange) throws TaskBackgroundException {
    List<String> errorMessages = dividendJpaRepository.periodicallyUpdate();
    if (!errorMessages.isEmpty()) {
      throw new TaskBackgroundException("gt.dividend.connector.failure", errorMessages, false);
    }
  }
}
