package grafioschtrader.task.exec;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import grafioschtrader.entities.TaskDataChange;
import grafioschtrader.entities.Tenant;
import grafioschtrader.repository.HoldCashaccountBalanceJpaRepository;
import grafioschtrader.repository.HoldCashaccountDepositJpaRepository;
import grafioschtrader.repository.HoldSecurityaccountSecurityJpaRepository;
import grafioschtrader.task.ITask;
import grafioschtrader.types.TaskType;

@Component
public class RebuildHolingAllTenantTask implements ITask {

  @Autowired
  private HoldSecurityaccountSecurityJpaRepository holdSecurityaccountSecurityRepository;

  @Autowired
  private HoldCashaccountDepositJpaRepository holdCashaccountDepositJpaRepository;

  @Autowired
  private HoldCashaccountBalanceJpaRepository holdCashaccountBalanceJpaRepository;

  @Override
  public TaskType getTaskType() {
    return TaskType.REBUILD_HOLDINGS_ALL_OR_SINGLE_TENANT;
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
