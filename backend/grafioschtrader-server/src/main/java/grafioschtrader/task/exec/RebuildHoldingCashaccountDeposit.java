package grafioschtrader.task.exec;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import grafioschtrader.entities.TaskDataChange;
import grafioschtrader.repository.HoldCashaccountDepositJpaRepository;
import grafioschtrader.task.ITask;
import grafioschtrader.types.TaskType;

@Component
public class RebuildHoldingCashaccountDeposit implements ITask {

  @Autowired
  private HoldCashaccountDepositJpaRepository holdCashaccountDepositJpaRepository;

  @Override
  public TaskType getTaskType() {
    return TaskType.REBUILD_HOLDING_CASHACCOUNT_DEPOSIT_OUT_DATED_CURRENCY_PAIR_PRICE;
  }

  @Override
  public boolean removeAllOtherJobsOfSameTask() {
    return true;
  }

  @Override
  public void doWork(TaskDataChange taskDataChange) {
    holdCashaccountDepositJpaRepository.adjustBecauseOfHistoryquotePriceChanges();
  }
}
