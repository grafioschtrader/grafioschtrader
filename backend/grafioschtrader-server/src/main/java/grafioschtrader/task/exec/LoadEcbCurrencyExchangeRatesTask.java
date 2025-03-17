package grafioschtrader.task.exec;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import grafiosch.BaseConstants;
import grafiosch.entities.TaskDataChange;
import grafiosch.exceptions.TaskBackgroundException;
import grafiosch.repository.TaskDataChangeJpaRepository;
import grafiosch.task.ITask;
import grafiosch.types.ITaskType;
import grafiosch.types.TaskDataExecPriority;
import grafioschtrader.connector.instrument.ecb.EcbLoader;
import grafioschtrader.repository.EcbExchangeRatesRepository;
import grafioschtrader.types.TaskTypeExtended;

/**
 * Updating a currency pair such as USD/CHF takes place in two steps. First, the
 * ECB exchange rate data must be downloaded with this background job. This is
 * then written to the Grafioschtrader database. The second processing step
 * takes place together with the updating of the other historical price data of
 * the currency pairs. This does not access an external data source, but the
 * rates previously stored in the database. A cross rate is calculated for
 * currency pairs that do not contain the EUR.
 */
@Component
public class LoadEcbCurrencyExchangeRatesTask implements ITask {

  @Autowired
  private EcbExchangeRatesRepository ecbExchangeRatesRepository;

  @Autowired
  private TaskDataChangeJpaRepository taskDataChangeRepository;

  @Override
  public ITaskType getTaskType() {
    return TaskTypeExtended.LOAD_ECB_CURRENCY_EXCHANGE_RATES;
  }

  @Scheduled(cron = "${gt.load.ecb.data}", zone = BaseConstants.TIME_ZONE)
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
