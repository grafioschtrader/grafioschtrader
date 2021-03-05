package grafioschtrader.task.exec;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitysplit;
import grafioschtrader.entities.TaskDataChange;
import grafioschtrader.exceptions.TaskBackgroundException;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository.SplitAdjustedHistoryquotes;
import grafioschtrader.repository.SecuritysplitJpaRepository;
import grafioschtrader.repository.TaskDataChangeJpaRepository;
import grafioschtrader.task.ITask;
import grafioschtrader.types.TaskType;

/**
 * When a split is added it may take some days until the data provider reflect
 * that in adjusted historical prices
 */
@Component
public class CheckReloadSecurityAdjustedPricesAfterSplit implements ITask {

  private final Logger log = LoggerFactory.getLogger(this.getClass());

  @Autowired
  private SecurityJpaRepository securityJpaRepository;

  @Autowired
  private SecuritysplitJpaRepository securitysplitJpaRepository;

  @Autowired
  private TaskDataChangeJpaRepository taskDataChangeJpaRepository;

  @Override
  public TaskType getTaskType() {
    return TaskType.CHECK_RELOAD_SECURITY_ADJUSTED_HISTORICAL_PRICES;
  }

  @Override
  @Transactional
  public void doWork(Integer idEntity, String entity) {
    Optional<Security> securityOpt = securityJpaRepository.findById(idEntity);
    if (securityOpt.isPresent()) {
      loadSplitsAndPossiblePrices(securityOpt.get());
    }
  }

  private void loadSplitsAndPossiblePrices(Security security) {
    try {
      List<Securitysplit> securitysplits = securitysplitJpaRepository
          .findByIdSecuritycurrencyOrderBySplitDateAsc(security.getIdSecuritycurrency());
      if (!securitysplits.isEmpty()) {
        if (securityJpaRepository.isYoungestSplitHistoryquotePossibleAdjusted(security, securitysplits,
            true) == SplitAdjustedHistoryquotes.ADJUSTED
            && securityJpaRepository.isYoungestSplitHistoryquotePossibleAdjusted(security, securitysplits,
                false) == SplitAdjustedHistoryquotes.NOT_ADJUSTED) {
          securityJpaRepository.rebuildSecurityCurrencypairHisotry(security);

        } else {
          taskDataChangeJpaRepository.save(new TaskDataChange(TaskType.CHECK_RELOAD_SECURITY_ADJUSTED_HISTORICAL_PRICES,
              (short) 30, LocalDateTime.now().plusDays(1L), security.getIdSecuritycurrency()));
        }
      }
    } catch (final Exception ex) {
      log.error(ex.getMessage() + " " + security, ex);
      throw new TaskBackgroundException("gt.historical.connector.failure");
    }
  }

}
