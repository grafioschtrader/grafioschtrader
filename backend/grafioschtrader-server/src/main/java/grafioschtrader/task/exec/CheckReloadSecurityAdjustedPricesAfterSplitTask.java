package grafioschtrader.task.exec;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.entities.TaskDataChange;
import grafiosch.exceptions.TaskBackgroundException;
import grafiosch.task.ITask;
import grafiosch.types.ITaskType;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitysplit;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.repository.SecuritysplitJpaRepository;
import grafioschtrader.types.TaskTypeExtended;

/**
 * If a split is added for a security, it may take a few days for the adjusted
 * historical price data to be available from the data provider. This task is
 * therefore repeated until the historical price data for the security has been
 * successfully imported.
 */
@Component
public class CheckReloadSecurityAdjustedPricesAfterSplitTask implements ITask {

  private final Logger log = LoggerFactory.getLogger(this.getClass());

  @Autowired
  private SecurityJpaRepository securityJpaRepository;

  @Autowired
  private SecuritysplitJpaRepository securitysplitJpaRepository;

  @Override
  public ITaskType getTaskType() {
    return TaskTypeExtended.CHECK_RELOAD_SECURITY_ADJUSTED_HISTORICAL_PRICES;
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
        securitysplitJpaRepository.historicalDataUpdateWhenAdjusted(security, securitysplits, Optional.empty(), true, true);
      }
    } catch (final Exception ex) {
      log.error(ex.getMessage() + " " + security, ex);
      throw new TaskBackgroundException("gt.historical.connector.failure", false);
    }
  }

}
