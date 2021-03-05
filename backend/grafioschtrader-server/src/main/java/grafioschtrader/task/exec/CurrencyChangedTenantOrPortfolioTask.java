package grafioschtrader.task.exec;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
  @Transactional
  public void doWork(Integer idEntity, String entityName) {
    if (Tenant.TABNAME.equals(entityName)) {
      tenantJpaRepository.createNotExistingCurrencypairs(idEntity);
    } else  {
      idEntity = portfolioJpaRepository.createNotExistingCurrencypairs(idEntity);
    }
    holdSecurityaccountSecurityRepository.createSecurityHoldingsEntireByTenant(idEntity);
    holdCashaccountDepositJpaRepository.createCashaccountDepositTimeFrameByTenant(idEntity);
    holdCashaccountBalanceJpaRepository.createCashaccountBalanceEntireByTenant(idEntity);

  }

}
