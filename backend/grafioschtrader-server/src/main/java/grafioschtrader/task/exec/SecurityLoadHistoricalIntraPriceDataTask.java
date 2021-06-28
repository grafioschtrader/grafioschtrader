package grafioschtrader.task.exec;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.entities.Security;
import grafioschtrader.entities.TaskDataChange;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.task.ITask;
import grafioschtrader.types.TaskType;

@Component
public class SecurityLoadHistoricalIntraPriceDataTask implements ITask {

  @Autowired
  private SecurityJpaRepository securityJpaRepository;

  @Override
  public TaskType getTaskType() {
    return TaskType.SECURITY_LOAD_HISTORICAL_INTRA_PRICE_DATA;
  }

  @Override
  public List<String> getAllowedEntities() {
    return Arrays.asList(Security.class.getSimpleName());
  }

  @Override
  @Transactional
  public void doWork(TaskDataChange taskDataChange) {
    Optional<Security> securityOpt = securityJpaRepository.findById(taskDataChange.getIdEntity());
    if (securityOpt.isPresent()) {
      Security security = securityJpaRepository.rebuildSecurityCurrencypairHisotry(securityOpt.get());
      securityJpaRepository.updateLastPriceByList(Arrays.asList(security));
    }
  }

}
