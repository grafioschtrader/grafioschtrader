package grafioschtrader.task.exec;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import grafiosch.entities.TaskDataChange;
import grafiosch.task.ITask;
import grafiosch.types.ITaskType;
import grafioschtrader.entities.Tenant;
import grafioschtrader.repository.HoldCashaccountBalanceJpaRepository;
import grafioschtrader.repository.HoldCashaccountDepositJpaRepository;
import grafioschtrader.repository.HoldSecurityaccountSecurityJpaRepository;
import grafioschtrader.types.TaskTypeExtended;

/**
 * The inventory tables are only updated if the transactions are processed in
 * the usual way. This may not be the case when importing data or copying demo
 * user accounts. Therefore, the holding tables can be updated for one or all
 * tenants with this task.
 */
@Component
public class RebuildHolingAllTenantOrSingleTask implements ITask {

  @Autowired
  private HoldSecurityaccountSecurityJpaRepository holdSecurityaccountSecurityRepository;

  @Autowired
  private HoldCashaccountDepositJpaRepository holdCashaccountDepositJpaRepository;

  @Autowired
  private HoldCashaccountBalanceJpaRepository holdCashaccountBalanceJpaRepository;

  @Override
  public ITaskType getTaskType() {
    return TaskTypeExtended.REBUILD_HOLDINGS_ALL_OR_SINGLE_TENANT;
  }

  @Override
  public List<String> getAllowedEntities() {
    return Arrays.asList("", Tenant.class.getSimpleName());
  }

  @Override
  public void doWork(TaskDataChange taskDataChange) {
    Integer idTenant = taskDataChange.getIdEntity();
    if (idTenant == null) {
      holdSecurityaccountSecurityRepository.createSecurityHoldingsEntireForAllTenant();
      holdCashaccountDepositJpaRepository.createCashaccountDepositTimeFrameForAllTenant();
      holdCashaccountBalanceJpaRepository.createCashaccountBalanceEntireForAllTenants();
    } else {
      holdSecurityaccountSecurityRepository.createSecurityHoldingsEntireByTenant(idTenant);
      holdCashaccountDepositJpaRepository.createCashaccountDepositTimeFrameByTenant(idTenant);
      holdCashaccountBalanceJpaRepository.createCashaccountBalanceEntireByTenant(idTenant);
    }
  }

}
