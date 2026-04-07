package grafiosch.task.exec;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import grafiosch.BaseConstants;
import grafiosch.common.PropertyStringParser;
import grafiosch.entities.TaskDataChange;
import grafiosch.exceptions.TaskBackgroundException;
import grafiosch.gtnet.GTNetExchangeLogPeriodType;
import grafiosch.gtnet.IMessageRetentionProvider;
import grafiosch.repository.GTNetExchangeLogJpaRepository;
import grafiosch.repository.GTNetMessageJpaRepository;
import grafiosch.repository.GlobalparametersJpaRepository;
import grafiosch.repository.TaskDataChangeJpaRepository;
import grafiosch.task.ITask;
import grafiosch.types.ITaskType;
import grafiosch.types.TaskDataExecPriority;
import grafiosch.types.TaskTypeBase;

/**
 * Scheduled task that aggregates GTNet exchange log entries from shorter to longer periods and deletes old messages.
 *
 * <p>Runs daily at configurable time (default 3 AM) and performs:
 * <ul>
 *   <li>Rolling log aggregation: INDIVIDUAL -> DAILY -> WEEKLY -> MONTHLY -> YEARLY</li>
 *   <li>Deletion of old exchange messages based on configurable retention periods,
 *       driven by registered {@link IMessageRetentionProvider} beans</li>
 * </ul>
 *
 * <p>Configuration is read from global parameters:
 * <ul>
 *   <li>{@code gt.gtnet.log.aggregate.days}: Log aggregation thresholds (D=1,W=7,M=30,Y=365)</li>
 *   <li>{@code gt.gtnet.del.message.recv}: Message retention periods per provider config key</li>
 * </ul>
 */
@Component
public class GTNetExchangeLogAggregationAndDelGTNetMessagesTask implements ITask {

  private static final Logger log = LoggerFactory.getLogger(GTNetExchangeLogAggregationAndDelGTNetMessagesTask.class);

  @Autowired
  private GTNetExchangeLogJpaRepository gtNetExchangeLogJpaRepository;

  @Autowired
  private GTNetMessageJpaRepository gtNetMessageJpaRepository;

  @Autowired
  private GlobalparametersJpaRepository globalparametersJpaRepository;

  @Autowired
  private TaskDataChangeJpaRepository taskDataChangeRepository;

  @Autowired(required = false)
  private List<IMessageRetentionProvider> messageRetentionProviders;

  @Override
  public ITaskType getTaskType() {
    return TaskTypeBase.GTNET_EXCHANGE_LOG_AGGREGATION;
  }

  /**
   * Scheduled method that creates the aggregation task.
   * Runs at the configured cron expression (default: 3 AM daily).
   */
  @Scheduled(cron = "${gt.gtnet.log.aggregation.cron:0 0 3 * * ?}", zone = BaseConstants.TIME_ZONE)
  public void createAggregationTask() {
    if (!globalparametersJpaRepository.isGTNetEnabled()) {
      log.debug("GTNet is disabled, skipping log aggregation");
      return;
    }
    log.info("Scheduling GTNet exchange log aggregation task");
    TaskDataChange taskDataChange = new TaskDataChange(getTaskType(), TaskDataExecPriority.PRIO_NORMAL);
    taskDataChangeRepository.save(taskDataChange);
  }

  @Override
  public void doWork(TaskDataChange taskDataChange) throws TaskBackgroundException {
    if (!globalparametersJpaRepository.isGTNetEnabled()) {
      log.debug("GTNet is disabled, skipping log aggregation and message cleanup");
      return;
    }

    aggregateLogs();
    deleteOldMessages();
  }

  /**
   * Aggregates GTNet exchange log entries from shorter to longer periods.
   * Reads configuration from global parameter {@code gt.gtnet.log.aggregate.days}.
   */
  private void aggregateLogs() {
    PropertyStringParser logConfig = globalparametersJpaRepository.getGTNetLogAggregationConfig();
    LocalDate today = LocalDate.now();
    int totalAggregated = 0;

    int daysBeforeDaily = logConfig.getIntValue("D", 1);
    int daysBeforeWeekly = logConfig.getIntValue("W", 7);
    int daysBeforeMonthly = logConfig.getIntValue("M", 30);
    int daysBeforeYearly = logConfig.getIntValue("Y", 365);

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

  /**
   * Deletes old GTNet exchange messages based on retention configuration.
   * Iterates over all registered {@link IMessageRetentionProvider} beans and deletes
   * messages older than the configured retention period for each provider's message codes.
   */
  private void deleteOldMessages() {
    if (messageRetentionProviders == null || messageRetentionProviders.isEmpty()) {
      log.debug("No message retention providers registered, skipping message deletion");
      return;
    }

    PropertyStringParser msgConfig = globalparametersJpaRepository.getGTNetMessageDeletionConfig();
    LocalDate today = LocalDate.now();

    for (IMessageRetentionProvider provider : messageRetentionProviders) {
      int retentionDays = msgConfig.getIntValue(provider.getConfigKey(), provider.getDefaultRetentionDays());
      LocalDateTime threshold = today.minusDays(retentionDays).atStartOfDay();
      List<Byte> codes = provider.getMessageCodes();

      // First delete replies to avoid FK constraint violation on reply_to
      int repliesDeleted = gtNetMessageJpaRepository.deleteRepliesToOldMessages(codes, threshold);
      int deleted = gtNetMessageJpaRepository.deleteOldMessagesByCodesAndDate(codes, threshold);
      if (repliesDeleted > 0 || deleted > 0) {
        log.info("Deleted {} reply rows and {} message rows for '{}' (codes {}) older than {} days",
            repliesDeleted, deleted, provider.getConfigKey(), codes, retentionDays);
      }
    }
  }

  @Override
  public boolean removeAllOtherPendingJobsOfSameTask() {
    return true;
  }
}
