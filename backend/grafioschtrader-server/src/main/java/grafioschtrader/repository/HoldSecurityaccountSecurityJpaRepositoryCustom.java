package grafioschtrader.repository;

import java.util.concurrent.ExecutionException;

import grafioschtrader.dto.MissingQuotesWithSecurities;
import grafioschtrader.entities.Securityaccount;
import grafioschtrader.entities.Transaction;

public interface HoldSecurityaccountSecurityJpaRepositoryCustom {

  void createSecurityHoldingsEntireForAllTenant();

  /**
   * Security holdings are completely rebuild for a tenant.
   *
   * @param idTenant
   */
  void createSecurityHoldingsEntireByTenant(Integer idTenant);

  /**
   * Adjust security holding for a single transaction.
   *
   * @param securityaccount
   * @param transaction
   */
  void adjustSecurityHoldingForSecurityaccountAndSecurity(Securityaccount securityaccount, Transaction transaction, boolean isAdded);

  void rebuildHoldingsForSecurity(Integer idSecuritycurrency);

  MissingQuotesWithSecurities getMissingQuotesWithSecurities(Integer year)
      throws InterruptedException, ExecutionException;
}
