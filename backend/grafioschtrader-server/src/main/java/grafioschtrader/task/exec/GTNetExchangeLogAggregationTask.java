package grafioschtrader.task.exec;

import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import grafiosch.BaseConstants;
import grafiosch.entities.TaskDataChange;
import grafiosch.exceptions.TaskBackgroundException;
import grafiosch.repository.TaskDataChangeJpaRepository;
import grafiosch.task.ITask;
import grafiosch.types.ITaskType;
import grafiosch.types.TaskDataExecPriority;
import grafioschtrader.gtnet.GTNetExchangeLogPeriodType;
import grafioschtrader.repository.GTNetExchangeLogJpaRepository;
import grafioschtrader.service.GlobalparametersService;
import grafioschtrader.types.TaskTypeExtended;

/**
 * Scheduled task that aggregates GTNet exchange log entries from shorter to longer periods.
 *
 * Runs daily at configurable time (default 3 AM) and performs rolling aggregation:
 * - INDIVIDUAL -> DAILY (after 1 day)
 * - DAILY -> WEEKLY (after 7 days)
 * - WEEKLY -> MONTHLY (after 30 days)
 * - MONTHLY -> YEARLY (after 365 days)
 */
@Component
public class GTNetExchangeLogAggregationTask implements ITask {

  private static final Logger log = LoggerFactory.getLogger(GTNetExchangeLogAggregationTask.class);

  @Autowired
  private GTNetExchangeLogJpaRepository gtNetExchangeLogJpaRepository;

  @Autowired
  private GlobalparametersService globalparametersService;

  @Autowired
  private TaskDataChangeJpaRepository taskDataChangeRepository;

  @Value("${gt.gtnet.log.aggregate.daily.after:1}")
  private int daysBeforeDaily;

  @Value("${gt.gtnet.log.aggregate.weekly.after:7}")
  private int daysBeforeWeekly;

  @Value("${gt.gtnet.log.aggregate.monthly.after:30}")
  private int daysBeforeMonthly;

  @Value("${gt.gtnet.log.aggregate.yearly.after:365}")
  private int daysBeforeYearly;

  @Override
  public ITaskType getTaskType() {
    return TaskTypeExtended.GTNET_EXCHANGE_LOG_AGGREGATION;
  }

  /**
   * Scheduled method that creates the aggregation task.
   * Runs at the configured cron expression (default: 3 AM daily).
   */
  @Scheduled(cron = "${gt.gtnet.log.aggregation.cron:0 0 3 * * ?}", zone = BaseConstants.TIME_ZONE)
  public void createAggregationTask() {
    if (!globalparametersService.isGTNetEnabled()) {
      log.debug("GTNet is disabled, skipping log aggregation");
      return;
    }
    log.info("Scheduling GTNet exchange log aggregation task");
    TaskDataChange taskDataChange = new TaskDataChange(getTaskType(), TaskDataExecPriority.PRIO_NORMAL);
    taskDataChangeRepository.save(taskDataChange);
  }

  @Override
  public void doWork(TaskDataChange taskDataChange) throws TaskBackgroundException {
    if (!globalparametersService.isGTNetEnabled()) {
      log.debug("GTNet is disabled, skipping log aggregation");
      return;
    }

    LocalDate today = LocalDate.now();
    int totalAggregated = 0;

    // Step 1: Aggregate INDIVIDUAL -> DAILY
    int count = gtNetExchangeLogJpaRepository.aggregateLogs(
        GTNetExchangeLogPeriodType.INDIVIDUAL,
        GTNetExchangeLogPeriodType.DAILY,
        today.minusDays(daysBeforeDaily));
    if (count > 0) {
      log.info("Aggregated {} INDIVIDUAL entries to DAILY", count);
      totalAggregated += count;
    }

    // Step 2: Aggregate DAILY -> WEEKLY
    count = gtNetExchangeLogJpaRepository.aggregateLogs(
        GTNetExchangeLogPeriodType.DAILY,
        GTNetExchangeLogPeriodType.WEEKLY,
        today.minusDays(daysBeforeWeekly));
    if (count > 0) {
      log.info("Aggregated {} DAILY entries to WEEKLY", count);
      totalAggregated += count;
    }

    // Step 3: Aggregate WEEKLY -> MONTHLY
    count = gtNetExchangeLogJpaRepository.aggregateLogs(
        GTNetExchangeLogPeriodType.WEEKLY,
        GTNetExchangeLogPeriodType.MONTHLY,
        today.minusDays(daysBeforeMonthly));
    if (count > 0) {
      log.info("Aggregated {} WEEKLY entries to MONTHLY", count);
      totalAggregated += count;
    }

    // Step 4: Aggregate MONTHLY -> YEARLY
    count = gtNetExchangeLogJpaRepository.aggregateLogs(
        GTNetExchangeLogPeriodType.MONTHLY,
        GTNetExchangeLogPeriodType.YEARLY,
        today.minusDays(daysBeforeYearly));
    if (count > 0) {
      log.info("Aggregated {} MONTHLY entries to YEARLY", count);
      totalAggregated += count;
    }

    log.info("GTNet exchange log aggregation completed. Total entries aggregated: {}", totalAggregated);
  }

  @Override
  public boolean removeAllOtherPendingJobsOfSameTask() {
    return true;
  }
}
