package grafioschtrader.task.exec;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import grafiosch.entities.TaskDataChange;
import grafiosch.task.ITask;
import grafiosch.types.ITaskType;
import grafioschtrader.repository.HoldDailyTotalJpaRepository;
import grafioschtrader.repository.HoldDailyTotalJpaRepository.ITenantFirstHoldDate;
import grafioschtrader.repository.HoldSecurityaccountSecurityJpaRepository;
import grafioschtrader.service.HoldDailyTotalMarker;
import grafioschtrader.types.TaskTypeExtended;

/**
 * When a security split happens it influences the calculation of the performance. History quotes prices are adjusted
 * and holdings in transaction must respect this changed. The entity security holding gets out dated.
 *
 * <p>
 * The daily total value of every tenant holding the security is marked as outdated from the first day it held it. The
 * task does not know which split changed, and the rebuild may change every day of the hold periods of the security.
 * </p>
 */
@Component
public class HoldingSecurityRebuildTask implements ITask {

  @Autowired
  private HoldSecurityaccountSecurityJpaRepository holdSecurityaccountSecurityJpaRepository;

  @Autowired
  private HoldDailyTotalJpaRepository holdDailyTotalJpaRepository;

  @Autowired
  private HoldDailyTotalMarker holdDailyTotalMarker;

  @Override
  public ITaskType getTaskType() {
    return TaskTypeExtended.HOLDINGS_SECURITY_REBUILD;
  }

  @Override
  public void doWork(TaskDataChange taskDataChange) {
    holdSecurityaccountSecurityJpaRepository.rebuildHoldingsForSecurity(taskDataChange.getIdEntity());
    for (ITenantFirstHoldDate tenantFirstHoldDate : holdDailyTotalJpaRepository
        .getFirstHoldDatePerTenantBySecurity(taskDataChange.getIdEntity())) {
      holdDailyTotalMarker.markDirty(tenantFirstHoldDate.getIdTenant(), tenantFirstHoldDate.getFirstHoldDate());
    }
  }

}
