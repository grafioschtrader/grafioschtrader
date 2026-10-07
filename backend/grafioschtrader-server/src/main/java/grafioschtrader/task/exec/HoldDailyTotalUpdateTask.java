package grafioschtrader.task.exec;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
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
import grafioschtrader.entities.Tenant;
import grafioschtrader.service.HoldDailyTotalService;
import grafioschtrader.types.TaskTypeExtended;

/**
 * Brings the daily total value of the tenants and their portfolios in {@code hold_daily_total} up to date, see
 * {@link HoldDailyTotalService}.
 *
 * <p>
 * With a tenant as entity only that tenant is updated; this is what a booking queues through
 * {@code HoldDailyTotalMarker}. Without an entity every main tenant is updated, simulation copies are excluded. The
 * cron {@code gt.hold.daily.total.update} queues the latter twice a day: in the morning after the end-of-day price
 * chain and in the late evening after the US close, so that nearly every exchange has its closing price. A normal run
 * computes one day per tenant; more when quotes were missing or a transaction was booked back-dated.
 * </p>
 *
 * <p>
 * Each tenant is updated in a transaction of its own. A tenant that fails does not stop the others; the task reports
 * the failure once all of them have been processed.
 * </p>
 */
@Component
public class HoldDailyTotalUpdateTask implements ITask {

  private static final Logger log = LoggerFactory.getLogger(HoldDailyTotalUpdateTask.class);

  @Autowired
  private HoldDailyTotalService holdDailyTotalService;

  @Autowired
  private TaskDataChangeJpaRepository taskDataChangeJpaRepository;

  @Override
  public ITaskType getTaskType() {
    return TaskTypeExtended.HOLD_DAILY_TOTAL_UPDATE;
  }

  @Override
  public List<String> getAllowedEntities() {
    return Arrays.asList("", Tenant.class.getSimpleName());
  }

  @Scheduled(cron = "${gt.hold.daily.total.update}", zone = BaseConstants.TIME_ZONE)
  public void updateHoldDailyTotal() {
    taskDataChangeJpaRepository.save(new TaskDataChange(getTaskType(), TaskDataExecPriority.PRIO_LOW));
  }

  @Override
  public void doWork(TaskDataChange taskDataChange) throws TaskBackgroundException {
    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    List<Integer> idTenants = taskDataChange.getIdEntity() == null ? holdDailyTotalService.getIdTenantsToUpdate()
        : List.of(taskDataChange.getIdEntity());
    List<String> failures = new ArrayList<>();
    for (Integer idTenant : idTenants) {
      try {
        holdDailyTotalService.updateTenant(idTenant, today);
      } catch (Exception e) {
        log.error("Update of hold_daily_total failed for tenant {}", idTenant, e);
        failures.add(idTenant + ": " + e.getMessage());
      }
    }
    if (!failures.isEmpty()) {
      throw new TaskBackgroundException("gt.hold.daily.total.failed", failures, false);
    }
  }
}
