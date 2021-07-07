package grafioschtrader.task.exec;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.entities.Portfolio;
import grafioschtrader.entities.TaskDataChange;
import grafioschtrader.entities.Tenant;
import grafioschtrader.repository.HoldCashaccountBalanceJpaRepository;
import grafioschtrader.repository.HoldCashaccountDepositJpaRepository;
import grafioschtrader.repository.HoldSecurityaccountSecurityJpaRepository;
import grafioschtrader.repository.PortfolioJpaRepository;
import grafioschtrader.repository.TenantJpaRepository;
import grafioschtrader.task.ITask;
import grafioschtrader.types.TaskType;

/**
 * Creation of currencies and possible recreation of holing tables
 *
 * @author Hugo Graf
 *
 */
@Component
public class CurrencyChangedTenantOrPortfolioTask implements ITask {

  @Autowired
  private TenantJpaRepository tenantJpaRepository;

  @Autowired
  private PortfolioJpaRepository portfolioJpaRepository;

  @Autowired
  private HoldSecurityaccountSecurityJpaRepository holdSecurityaccountSecurityRepository;

  @Autowired
  private HoldCashaccountDepositJpaRepository holdCashaccountDepositJpaRepository;

  @Autowired
  private HoldCashaccountBalanceJpaRepository holdCashaccountBalanceJpaRepository;

  @Override
  public TaskType getTaskType() {
    return TaskType.CURRENCY_CHANGED_ON_TENANT_OR_PORTFOLIO;
  }

  @Override
  public List<String> getAllowedEntities() {
    return Arrays.asList(Portfolio.class.getSimpleName(), Tenant.class.getSimpleName());
  }

  @Override
  @Transactional
  public void doWork(TaskDataChange taskDataChange) {
    Integer idEntity = taskDataChange.getIdEntity();
    if (Tenant.class.getSimpleName().equals(taskDataChange.getEntity())) {
      tenantJpaRepository.createNotExistingCurrencypairs(idEntity);
    } else {
      idEntity = portfolioJpaRepository.createNotExistingCurrencypairs(idEntity);
    }
    holdSecurityaccountSecurityRepository.createSecurityHoldingsEntireByTenant(idEntity);
    holdCashaccountDepositJpaRepository.createCashaccountDepositTimeFrameByTenant(idEntity);
    holdCashaccountBalanceJpaRepository.createCashaccountBalanceEntireByTenant(idEntity);

  }

}
