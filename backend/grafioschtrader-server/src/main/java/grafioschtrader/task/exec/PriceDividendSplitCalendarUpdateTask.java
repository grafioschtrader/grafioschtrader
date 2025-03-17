package grafioschtrader.task.exec;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.BaseConstants;
import grafiosch.entities.TaskDataChange;
import grafiosch.repository.TaskDataChangeJpaRepository;
import grafiosch.task.ITask;
import grafiosch.types.ITaskType;
import grafiosch.types.TaskDataExecPriority;
import grafioschtrader.connector.calendar.SplitCalendarAppender;
import grafioschtrader.repository.CurrencypairJpaRepository;
import grafioschtrader.repository.HistoryquotePeriodJpaRepository;
import grafioschtrader.repository.HoldCashaccountDepositJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.service.GlobalparametersService;
import grafioschtrader.types.TaskTypeExtended;

/**
 * It reads the EOD day from external resources and the dividend, split
 * calendars.
 *
 * Should run on every day but Sunday is not required.
 *
 */
@Component
@Transactional
public class PriceDividendSplitCalendarUpdateTask implements ITask {

  private static long TIMEOUT_IN_SECONDS = 3600;

  @Autowired
  private TaskDataChangeJpaRepository taskDataChangeRepository;

  @Autowired
  private CurrencypairJpaRepository currencypairJpaRepository;

  @Autowired
  private SecurityJpaRepository securityJpaRepository;

  @Autowired
  private SplitCalendarAppender splitCalendarAppender;

  @Autowired
  private HistoryquotePeriodJpaRepository historyquotePeriodJpaRepository;

  @Autowired
  private HoldCashaccountDepositJpaRepository holdCashaccountDepositJpaRepository;

  @Autowired
  private GlobalparametersService globalparametersService;

  @Scheduled(cron = "${gt.eod.cron.quotation}", zone = BaseConstants.TIME_ZONE)
  public void createPriceDividendSplitCalendarUpdateTask() {
    TaskDataChange taskDataChange = new TaskDataChange(getTaskType(), TaskDataExecPriority.PRIO_VERY_HIGH);
    taskDataChangeRepository.save(taskDataChange);
  }

  @Override
  public void doWork(TaskDataChange taskDataChange) {
    currencypairJpaRepository.catchAllUpCurrencypairHistoryquote();
    currencypairJpaRepository.allCurrenciesFillEmptyDaysInHistoryquote();

    if (globalparametersService.getUpdatePriceByStockexchange() == 0) {
      securityJpaRepository.catchAllUpSecurityHistoryquote(null);
    }
    historyquotePeriodJpaRepository.updatLastPriceFromHistoricalPeriod();
    securityJpaRepository.deleteUpdateHistoryQuality();
    splitCalendarAppender.appendSecuritySplitsUntilToday();
    holdCashaccountDepositJpaRepository.adjustBecauseOfHistoryquotePriceChanges();
    currencypairJpaRepository.createTaskDataChangeOfEmptyHistoryqoute();
  }

  @Override
  public ITaskType getTaskType() {
    return TaskTypeExtended.PRICE_AND_SPLIT_DIV_CALENDAR_UPDATE_THRU;
  }

  @Override
  public boolean canBeInterrupted() {
    return true;
  }

  @Override
  public long getTimeoutInSeconds() {
    return TIMEOUT_IN_SECONDS;
  }

}
