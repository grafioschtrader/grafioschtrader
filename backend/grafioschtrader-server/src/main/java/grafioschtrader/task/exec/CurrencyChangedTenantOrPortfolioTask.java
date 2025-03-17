package grafioschtrader.task.exec;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.entities.TaskDataChange;
import grafiosch.task.ITask;
import grafiosch.types.ITaskType;
import grafioschtrader.entities.Portfolio;
import grafioschtrader.entities.Tenant;
import grafioschtrader.repository.HoldCashaccountBalanceJpaRepository;
import grafioschtrader.repository.HoldCashaccountDepositJpaRepository;
import grafioschtrader.repository.HoldSecurityaccountSecurityJpaRepository;
import grafioschtrader.repository.PortfolioJpaRepository;
import grafioschtrader.repository.TenantJpaRepository;
import grafioschtrader.types.TaskTypeExtended;

/**
 * If the main currency of a tenant or one of their portfolios changes, any
 * missing currency pairs must be created for this main currency. The position
 * tables must also be reconstructed.
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
  public ITaskType getTaskType() {
    return TaskTypeExtended.CURRENCY_CHANGED_ON_TENANT_OR_PORTFOLIO;
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
