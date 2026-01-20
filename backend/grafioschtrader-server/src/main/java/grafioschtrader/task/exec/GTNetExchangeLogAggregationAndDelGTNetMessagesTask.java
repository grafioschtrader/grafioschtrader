package grafioschtrader.task.exec;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import grafioschtrader.common.PropertyStringParser;
import grafioschtrader.gtnet.GTNetExchangeLogPeriodType;
import grafioschtrader.gtnet.GTNetMessageCodeType;
import grafioschtrader.repository.GTNetExchangeLogJpaRepository;
import grafioschtrader.repository.GTNetMessageJpaRepository;
import grafioschtrader.service.GlobalparametersService;
import grafioschtrader.types.TaskTypeExtended;

/**
 * Scheduled task that aggregates GTNet exchange log entries from shorter to longer periods and deletes old messages.
 *
 * <p>Runs daily at configurable time (default 3 AM) and performs:
 * <ul>
 *   <li>Rolling log aggregation: INDIVIDUAL -> DAILY -> WEEKLY -> MONTHLY -> YEARLY</li>
 *   <li>Deletion of old price exchange messages based on configurable retention periods</li>
 * </ul>
 *
 * <p>Configuration is read from global parameters:
 * <ul>
 *   <li>{@code gt.gtnet.log.aggregate.days}: Log aggregation thresholds (D=1,W=7,M=30,Y=365)</li>
 *   <li>{@code gt.gtnet.del.message.recv}: Message retention periods (LP=1,HP=5)</li>
 * </ul>
 */
@Component
public class GTNetExchangeLogAggregationAndDelGTNetMessagesTask implements ITask {

  private static final Logger log = LoggerFactory.getLogger(GTNetExchangeLogAggregationAndDelGTNetMessagesTask.class);

  /** Message codes for LastPrice exchange (GT_NET_LASTPRICE_EXCHANGE_SEL_C and GT_NET_LASTPRICE_EXCHANGE_RESPONSE_S). */
  private static final List<Byte> LASTPRICE_MESSAGE_CODES = Arrays.asList(
      GTNetMessageCodeType.GT_NET_LASTPRICE_EXCHANGE_SEL_C.getValue(),
      GTNetMessageCodeType.GT_NET_LASTPRICE_EXCHANGE_RESPONSE_S.getValue());

  /** Message codes for HistoryPrice exchange (GT_NET_HISTORYQUOTE_EXCHANGE_SEL_C and GT_NET_HISTORYQUOTE_EXCHANGE_RESPONSE_S). */
  private static final List<Byte> HISTORYQUOTE_MESSAGE_CODES = Arrays.asList(
      GTNetMessageCodeType.GT_NET_HISTORYQUOTE_EXCHANGE_SEL_C.getValue(),
      GTNetMessageCodeType.GT_NET_HISTORYQUOTE_EXCHANGE_RESPONSE_S.getValue());

  @Autowired
  private GTNetExchangeLogJpaRepository gtNetExchangeLogJpaRepository;

  @Autowired
  private GTNetMessageJpaRepository gtNetMessageJpaRepository;

  @Autowired
  private GlobalparametersService globalparametersService;

  @Autowired
  private TaskDataChangeJpaRepository taskDataChangeRepository;

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
    PropertyStringParser logConfig = globalparametersService.getGTNetLogAggregationConfig();
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
   * Deletes old GTNet price exchange messages based on retention configuration.
   * Reads configuration from global parameter {@code gt.gtnet.del.message.recv}.
   *
   * <p>Message codes deleted:
   * <ul>
   *   <li>LP (LastPrice): codes 60, 61 - older than LP days</li>
   *   <li>HP (HistoryPrice): codes 80, 81 - older than HP days</li>
   * </ul>
   */
  private void deleteOldMessages() {
    PropertyStringParser msgConfig = globalparametersService.getGTNetMessageDeletionConfig();
    LocalDate today = LocalDate.now();

    int lpDays = msgConfig.getIntValue("LP", 1);
    int hpDays = msgConfig.getIntValue("HP", 5);

    // Delete old LastPrice exchange messages (codes 60, 61)
    Date lpThreshold = Date.from(today.minusDays(lpDays).atStartOfDay(ZoneId.systemDefault()).toInstant());
    // First delete replies to avoid FK constraint violation on reply_to
    int lpRepliesDeleted = gtNetMessageJpaRepository.deleteRepliesToOldMessages(LASTPRICE_MESSAGE_CODES, lpThreshold);
    int lpDeleted = gtNetMessageJpaRepository.deleteOldMessagesByCodesAndDate(LASTPRICE_MESSAGE_CODES, lpThreshold);
    if (lpRepliesDeleted > 0 || lpDeleted > 0) {
      log.info("Deleted {} reply rows and {} LastPrice exchange message rows (codes 60, 61) older than {} days",
          lpRepliesDeleted, lpDeleted, lpDays);
    }

    // Delete old HistoryPrice exchange messages (codes 80, 81)
    Date hpThreshold = Date.from(today.minusDays(hpDays).atStartOfDay(ZoneId.systemDefault()).toInstant());
    // First delete replies to avoid FK constraint violation on reply_to
    int hpRepliesDeleted = gtNetMessageJpaRepository.deleteRepliesToOldMessages(HISTORYQUOTE_MESSAGE_CODES, hpThreshold);
    int hpDeleted = gtNetMessageJpaRepository.deleteOldMessagesByCodesAndDate(HISTORYQUOTE_MESSAGE_CODES, hpThreshold);
    if (hpRepliesDeleted > 0 || hpDeleted > 0) {
      log.info("Deleted {} reply rows and {} HistoryPrice exchange message rows (codes 80, 81) older than {} days",
          hpRepliesDeleted, hpDeleted, hpDays);
    }
  }

  @Override
  public boolean removeAllOtherPendingJobsOfSameTask() {
    return true;
  }
}
