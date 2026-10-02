package grafioschtrader.service;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.exceptions.DataViolationException;
import grafioschtrader.GlobalParamKeyDefault;
import grafioschtrader.dto.AlgoAlertRetention;
import grafioschtrader.entities.AlgoMessageAlert;
import grafioschtrader.repository.AlgoMessageAlertJpaRepository;

/**
 * Bounds the recorded alert notifications, which would otherwise grow without limit per tenant. The user deletes
 * selected notifications, and once per server day the alert background task
 * ({@link grafioschtrader.task.exec.AlgoAlarmIndicatorEvaluationTask}) applies the retention of
 * {@code gt.algo.alert.retention} as its last step, after the delivery of its notifications.
 *
 * <p>
 * A notification is not only history: its row, unique per signal identity and alert day, is the only guard against the
 * same alarm being raised and sent again. Neither path therefore removes a notification whose alert day lies within the
 * last {@link GlobalParamKeyDefault#ALGO_ALERT_PROTECTED_DAYS} days, nor one whose delivery has not finished, since
 * that would lose the message silently or race the delivery that holds its lease.
 * </p>
 */
@Service
public class AlgoMessageAlertRetentionService {
  private static final Logger log = LoggerFactory.getLogger(AlgoMessageAlertRetentionService.class);

  /** Delivery states after which the delivery job no longer touches a notification. */
  private static final Set<String> FINISHED_STATES = Set.of("DELIVERED", "CANCELLED", "FAILED", "REVIEW_REQUIRED");

  private final AlgoMessageAlertJpaRepository alarms;
  private final GlobalparametersService globalparametersService;

  /**
   * Server day of the last completed retention run. Held in memory only: after a restart the retention runs once more
   * on the same day, which does no harm because each of its statements is idempotent.
   */
  private volatile LocalDate lastPurgeDay;

  public AlgoMessageAlertRetentionService(AlgoMessageAlertJpaRepository alarms,
      GlobalparametersService globalparametersService) {
    this.alarms = alarms;
    this.globalparametersService = globalparametersService;
  }

  /**
   * Whether the user may delete the notification today. The same rule the delete statements apply, offered to the
   * client so it can disable the action instead of letting it fail.
   *
   * @param alert the recorded notification
   * @param today the current server day
   * @return true when its delivery has finished and its alert day lies before the protected window
   */
  public static boolean isDeletable(AlgoMessageAlert alert, LocalDate today) {
    return FINISHED_STATES.contains(alert.getDeliveryStatus()) && alert.getAlertDay().isBefore(protectedFrom(today));
  }

  /**
   * Deletes the notifications the user has selected. The selection is deleted completely or not at all: when one of
   * them is protected or belongs to another tenant, nothing is deleted and the user learns why.
   *
   * @param idTenant the home tenant of the user
   * @param ids      the selected notifications
   * @return the number of deleted notifications
   */
  @Transactional
  public int deleteByUser(Integer idTenant, List<Integer> ids) {
    Set<Integer> distinctIds = new LinkedHashSet<>(ids);
    if (distinctIds.isEmpty()) {
      return 0;
    }
    int deleted = alarms.deleteFinishedByIds(idTenant, distinctIds, protectedFrom(LocalDate.now()));
    if (deleted != distinctIds.size()) {
      throw new DataViolationException("delivery.status", "gt.algo.alert.not.deletable",
          new Object[] { GlobalParamKeyDefault.ALGO_ALERT_PROTECTED_DAYS });
    }
    return deleted;
  }

  /**
   * Whether the retention has not yet run on the current server day. Lets the alert background task be scheduled once a
   * day for the retention alone, even when no alert, rebalancing or delivery is due.
   *
   * @return true until {@link #purge()} has completed today
   */
  public boolean isPurgeDue() {
    return !LocalDate.now().equals(lastPurgeDay);
  }

  /**
   * Applies the retention: first the age limit, then the record limit per tenant, and finally removes the notifications
   * of deleted strategies. Called by the alert background task when {@link #isPurgeDue()}; a run that fails leaves the
   * day unmarked, so the next run of the task tries again.
   */
  @Transactional
  public void purge() {
    AlgoAlertRetention retention = globalparametersService.getAlgoAlertRetention();
    LocalDate today = LocalDate.now();
    int byAge = alarms.purgeFinishedBefore(today.minusDays(retention.days()));
    int byCount = alarms.purgeFinishedBeyondMaxRecords(retention.maxRecords(), protectedFrom(today));
    int orphaned = alarms.purgeOfDeletedStrategies();
    if (byAge + byCount + orphaned > 0) {
      log.info("Purged alert notifications: {} beyond {} days, {} beyond {} records, {} of deleted strategies", byAge,
          retention.days(), byCount, retention.maxRecords(), orphaned);
    }
    lastPurgeDay = today;
  }

  /** First alert day of the protected window; a notification of this day or later is kept. */
  private static LocalDate protectedFrom(LocalDate today) {
    return today.minusDays(GlobalParamKeyDefault.ALGO_ALERT_PROTECTED_DAYS - 1);
  }
}
