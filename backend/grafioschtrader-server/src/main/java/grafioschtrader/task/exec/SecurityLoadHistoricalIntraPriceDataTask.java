package grafioschtrader.task.exec;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import grafiosch.entities.TaskDataChange;
import grafiosch.task.ITask;
import grafiosch.types.ITaskType;
import grafioschtrader.entities.Security;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.types.TaskTypeExtended;

/**
 * When a security is created, the intraday and historical price data is read. If the connector for the historical price
 * data is changed, the prices must also be read in again. When the security is created, this can be done synchronously
 * or asynchronously with this task.
 */
@Component
public class SecurityLoadHistoricalIntraPriceDataTask implements ITask {

  @Autowired
  private SecurityJpaRepository securityJpaRepository;

  @Autowired
  private PlatformTransactionManager platformTransactionManager;

  @Override
  public ITaskType getTaskType() {
    return TaskTypeExtended.SECURITY_LOAD_HISTORICAL_INTRA_PRICE_DATA;
  }

  @Override
  public List<String> getAllowedEntities() {
    return Arrays.asList(Security.class.getSimpleName());
  }

  /**
   * Rebuilds the history in its own transaction and updates the last price only after it has committed. The rebuild
   * saves the security and so locks its row. The intraday update must not run inside that transaction: it is designed
   * to commit every write on its own and may claim the row from another connection, which would then wait for the lock
   * of this task until the lock wait timeout. That lock also blocks every other writer of the row in the meantime, for
   * example adding the security to a watchlist.
   */
  @Override
  public void doWork(TaskDataChange taskDataChange) {
    Security security = new TransactionTemplate(platformTransactionManager)
        .execute(_ -> securityJpaRepository.findById(taskDataChange.getIdEntity())
            .map(securityJpaRepository::rebuildSecurityCurrencypairHisotry).orElse(null));
    if (security != null) {
      securityJpaRepository.updateLastPriceByList(Arrays.asList(security));
    }
  }

}
