package grafioschtrader.task.exec;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.connector.calendar.SplitCalendarAppender;
import grafioschtrader.entities.TaskDataChange;
import grafioschtrader.repository.CurrencypairJpaRepository;
import grafioschtrader.repository.HistoryquotePeriodJpaRepository;
import grafioschtrader.repository.HoldCashaccountDepositJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.repository.TaskDataChangeJpaRepository;
import grafioschtrader.task.ITask;
import grafioschtrader.types.TaskType;

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

  @Scheduled(cron = "${gt.eod.cron.quotation}", zone = "UTC")
  public void catchAllUpSecuritycurrencyHistoryquote() {
    TaskDataChange taskDataChange = new TaskDataChange(TaskType.PRICE_AND_SPLIT_DIV_CALENDAR_UPDATE_THRU, (short) 5);
    taskDataChangeRepository.save(taskDataChange);
  }

  @Override
  public void doWork(TaskDataChange taskDataChange) {
    currencypairJpaRepository.catchAllUpCurrencypairHistoryquote();
    currencypairJpaRepository.allCurrenciesFillEmptyDaysInHistoryquote();
    securityJpaRepository.catchAllUpSecurityHistoryquote();
    historyquotePeriodJpaRepository.updatLastPrice();
    securityJpaRepository.deleteUpdateHistoryQuality();
    splitCalendarAppender.appendSecuritySplitsUntilToday();
    holdCashaccountDepositJpaRepository.adjustBecauseOfHistoryquotePriceChanges();
    currencypairJpaRepository.createTaskDataChangeOfEmptyHistoryqoute();
  }

  @Override
  public TaskType getTaskType() {
    return TaskType.PRICE_AND_SPLIT_DIV_CALENDAR_UPDATE_THRU;
  }

}
