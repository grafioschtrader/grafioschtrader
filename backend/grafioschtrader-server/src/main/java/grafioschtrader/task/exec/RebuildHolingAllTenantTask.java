package grafioschtrader.task.exec;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import grafioschtrader.repository.HoldCashaccountBalanceJpaRepository;
import grafioschtrader.repository.HoldCashaccountDepositJpaRepository;
import grafioschtrader.repository.HoldSecurityaccountSecurityJpaRepository;
import grafioschtrader.task.ITask;
import grafioschtrader.types.TaskType;

@Component
public class RebuildHolingAllTenantTask implements ITask {

  @Autowired
  HoldSecurityaccountSecurityJpaRepository holdSecurityaccountSecurityRepository;

  @Autowired
  HoldCashaccountDepositJpaRepository holdCashaccountDepositJpaRepository;

  @Autowired
  HoldCashaccountBalanceJpaRepository holdCashaccountBalanceJpaRepository;

  @Override
  public TaskType getTaskType() {
    return TaskType.REBUILD_HOLDINGS_ALL_OR_SINGLE_TENANT;
  }

  @Override
  public void doWork(Integer idTenant, String entity) {
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
