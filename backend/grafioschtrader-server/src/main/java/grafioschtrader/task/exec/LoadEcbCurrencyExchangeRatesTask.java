package grafioschtrader.task.exec;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import grafioschtrader.GlobalConstants;
import grafioschtrader.connector.instrument.ecb.EcbLoader;
import grafioschtrader.entities.TaskDataChange;
import grafioschtrader.exceptions.TaskBackgroundException;
import grafioschtrader.repository.EcbExchangeRatesRepository;
import grafioschtrader.repository.TaskDataChangeJpaRepository;
import grafioschtrader.task.ITask;
import grafioschtrader.types.TaskDataExecPriority;
import grafioschtrader.types.TaskType;

@Component
public class LoadEcbCurrencyExchangeRatesTask implements ITask {

  @Autowired
  private EcbExchangeRatesRepository ecbExchangeRatesRepository;

  @Autowired
  private TaskDataChangeJpaRepository taskDataChangeRepository;

  @Override
  public TaskType getTaskType() {
    return TaskType.LOAD_ECB_CURRENCY_EXCHANGE_RATES;
  }

  @Scheduled(cron = "${gt.load.ecb.data}", zone = GlobalConstants.TIME_ZONE)
  public void loadEcbCurrencyHistoryquotes() {
    TaskDataChange taskDataChange = new TaskDataChange(getTaskType(), TaskDataExecPriority.PRIO_VERY_LOW);
    taskDataChangeRepository.save(taskDataChange);
  }

  @Override
  public void doWork(TaskDataChange taskDataChange) throws TaskBackgroundException {
    EcbLoader el = new EcbLoader();
    el.update(ecbExchangeRatesRepository);
  }

}
