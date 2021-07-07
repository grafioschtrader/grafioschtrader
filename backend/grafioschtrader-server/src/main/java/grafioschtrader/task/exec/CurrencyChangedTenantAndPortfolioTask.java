package grafioschtrader.task.exec;

import java.util.Optional;

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
 * Changed currency of tenant and portfolio it needs a creation of currencies
 * and possible recreation of holing tables
 *
 * @author Hugo Graf
 *
 */

@Component
public class CurrencyChangedTenantAndPortfolioTask implements ITask {
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
    return TaskType.CURRENCY_CHANGED_ON_TENANT_AND_PORTFOLIO;
  }

  @Override
  @Transactional
  public void doWork(TaskDataChange taskDataChange) {
    Integer idTenant = taskDataChange.getIdEntity();
    Optional<Tenant> tenantOpt = tenantJpaRepository.createNotExistingCurrencypairs(idTenant);
    if (tenantOpt.isPresent()) {
      for (Portfolio portfolio : tenantOpt.get().getPortfolioList()) {
        portfolioJpaRepository.createNotExistingCurrencypairs(portfolio.getIdPortfolio());
      }
      holdSecurityaccountSecurityRepository.createSecurityHoldingsEntireByTenant(idTenant);
      holdCashaccountDepositJpaRepository.createCashaccountDepositTimeFrameByTenant(idTenant);
      holdCashaccountBalanceJpaRepository.createCashaccountBalanceEntireByTenant(idTenant);
    }
  }

}
