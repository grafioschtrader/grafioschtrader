package grafioschtrader.service;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.entities.TaskDataChange;
import grafiosch.repository.TaskDataChangeJpaRepository;
import grafiosch.types.ProgressStateType;
import grafiosch.types.TaskDataExecPriority;
import grafioschtrader.entities.Tenant;
import grafioschtrader.repository.HoldDailyTotalStateJpaRepository;
import grafioschtrader.repository.TenantJpaRepository;
import grafioschtrader.types.TaskTypeExtended;
import grafioschtrader.types.TenantKindType;

/**
 * Marks the daily total value of a tenant as outdated whenever the hold tables it is derived from change, and queues
 * the task {@code HOLD_DAILY_TOTAL_UPDATE} that brings it up to date again.
 *
 * <p>
 * It is called where the hold tables are written, which is the lowest layer, so that every path changing them is
 * covered without each caller having to know about {@code hold_daily_total}: the cash balance replay that every
 * transaction create, update, delete, cash transfer and import passes through, the full rebuilds of a tenant, the
 * rebuild of the holdings of a split security and the adjustment of the deposits to changed exchange rates.
 * </p>
 *
 * <p>
 * Every method joins the transaction of its caller. A booking and the lowering of the marker therefore commit or roll
 * back together, and a task queued for a booking that fails is rolled back with it. The task starts
 * {@value #DELAY_MINUTES} minutes later, so that a series of bookings is handled by a single run. Simulation tenants
 * are never marked; they are out of scope of the table.
 * </p>
 */
@Component
public class HoldDailyTotalMarker {

  /** Minutes the queued task waits, so that consecutive bookings are grouped into one run. */
  static final int DELAY_MINUTES = 5;

  @Autowired
  private HoldDailyTotalStateJpaRepository holdDailyTotalStateJpaRepository;

  @Autowired
  private TaskDataChangeJpaRepository taskDataChangeJpaRepository;

  @Autowired
  private TenantJpaRepository tenantJpaRepository;

  /**
   * Marks the rows of a tenant as outdated from the given day on and queues the update of that tenant.
   *
   * @param idTenant the tenant whose hold tables changed
   * @param fromDate the first day the change affects
   */
  @Transactional
  public void markDirty(Integer idTenant, LocalDate fromDate) {
    // A tenant with a marker is a main tenant, only those are computed; without one it may still be a new main tenant.
    if (holdDailyTotalStateJpaRepository.lowerRecalcFromDate(idTenant, fromDate) > 0 || isMainTenant(idTenant)) {
      queueUpdate(idTenant);
    }
  }

  /**
   * Has the whole history of a tenant computed again, after its hold tables were rebuilt from scratch, for example
   * because of a change of the tenant or portfolio currency.
   *
   * @param idTenant the tenant whose hold tables were rebuilt
   */
  @Transactional
  public void markDirtyInFull(Integer idTenant) {
    holdDailyTotalStateJpaRepository.removeByIdTenant(idTenant);
    if (isMainTenant(idTenant)) {
      queueUpdate(idTenant);
    }
  }

  /** Has the whole history of every tenant computed again, after the hold tables of all tenants were rebuilt. */
  @Transactional
  public void markAllDirtyInFull() {
    holdDailyTotalStateJpaRepository.removeAllStates();
    queueUpdate(null);
  }

  private boolean isMainTenant(Integer idTenant) {
    return tenantJpaRepository.findById(idTenant).map(t -> t.getTenantKindType() == TenantKindType.MAIN).orElse(false);
  }

  /**
   * Queues the update of one tenant or, with a null tenant, of all of them, unless such a task is already waiting.
   *
   * @param idTenant the tenant, or null for all main tenants
   */
  private void queueUpdate(Integer idTenant) {
    if (!taskDataChangeJpaRepository.existsByIdTaskAndIdEntityAndProgressStateType(
        TaskTypeExtended.HOLD_DAILY_TOTAL_UPDATE.getValue(), idTenant, ProgressStateType.PROG_WAITING.getValue())) {
      taskDataChangeJpaRepository.save(new TaskDataChange(TaskTypeExtended.HOLD_DAILY_TOTAL_UPDATE,
          TaskDataExecPriority.PRIO_LOW, LocalDateTime.now().plusMinutes(DELAY_MINUTES), idTenant,
          idTenant == null ? null : Tenant.class.getSimpleName()));
    }
  }
}
