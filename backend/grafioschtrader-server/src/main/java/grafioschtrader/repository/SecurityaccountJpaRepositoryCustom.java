package grafioschtrader.repository;

import java.util.List;

import grafiosch.repository.BaseRepositoryCustom;
import grafioschtrader.dto.FeeModelComparisonResponse;
import grafioschtrader.dto.TradingPeriodTransactionSummary;
import grafioschtrader.entities.Securityaccount;

public interface SecurityaccountJpaRepositoryCustom extends BaseRepositoryCustom<Securityaccount> {

  int delEntityWithTenant(Integer id, Integer idTenant);

  /**
   * Returns transaction summaries per (specInvestInstrument, categoryType) for a security account.
   * Validates that the account belongs to the current tenant.
   */
  List<TradingPeriodTransactionSummary> getTransactionSummaries(Integer idSecuritycashAccount);

  /**
   * Compares actual transaction costs with EvalEx fee model estimates for the given security account.
   *
   * @param idSecuritycashAccount the security account ID
   * @param excludeZeroCost       if true, skip transactions with null or zero cost
   * @return comparison response with summary statistics and per-transaction detail rows
   */
  FeeModelComparisonResponse getFeeModelComparison(Integer idSecuritycashAccount, boolean excludeZeroCost);
}
