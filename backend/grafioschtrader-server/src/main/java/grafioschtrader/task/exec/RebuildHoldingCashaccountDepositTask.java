package grafioschtrader.task.exec;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import grafiosch.entities.TaskDataChange;
import grafiosch.task.ITask;
import grafiosch.types.ITaskType;
import grafioschtrader.repository.HoldCashaccountDepositJpaRepository;
import grafioschtrader.types.TaskTypeExtended;

@Component
public class RebuildHoldingCashaccountDepositTask implements ITask {

  @Autowired
  private HoldCashaccountDepositJpaRepository holdCashaccountDepositJpaRepository;

  @Override
  public ITaskType getTaskType() {
    return TaskTypeExtended.REBUILD_HOLDING_CASHACCOUNT_DEPOSIT_OUT_DATED_CURRENCY_PAIR_PRICE;
  }

  @Override
  public boolean removeAllOtherPendingJobsOfSameTask() {
    return true;
  }

  @Override
  public void doWork(TaskDataChange taskDataChange) {
    holdCashaccountDepositJpaRepository.adjustBecauseOfHistoryquotePriceChanges();
  }
}
