package grafioschtrader.task.exec;

import java.time.LocalDateTime;
import java.util.Arrays;
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
import grafioschtrader.types.TaskDataExecPriority;
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
  public List<String> getAllowedEntities() {
    return Arrays.asList(Security.class.getSimpleName());
  }

  @Override
  @Transactional
  public void doWork(TaskDataChange taskDataChange) throws TaskBackgroundException {
    Optional<Security> securityOpt = securityJpaRepository.findById(taskDataChange.getIdEntity());
    if (securityOpt.isPresent()) {
      loadSplitsAndPossiblePrices(securityOpt.get());
    }
  }

  private void loadSplitsAndPossiblePrices(Security security) throws TaskBackgroundException {
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
              TaskDataExecPriority.PRIO_LOW, LocalDateTime.now().plusDays(1L), security.getIdSecuritycurrency(),
              Security.class.getSimpleName()));
        }
      }
    } catch (final Exception ex) {
      log.error(ex.getMessage() + " " + security, ex);
      throw new TaskBackgroundException("gt.historical.connector.failure", false);
    }
  }

}
