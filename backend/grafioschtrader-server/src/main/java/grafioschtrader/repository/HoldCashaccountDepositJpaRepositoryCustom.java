package grafioschtrader.repository;

import grafioschtrader.entities.Transaction;

/**
 * This interface defines methods for managing cash account deposits and adjustments, particularly for creating
 * time-framed deposit records and handling transaction-related or history quote price changes.
 */
public interface HoldCashaccountDepositJpaRepositoryCustom {

  /**
   * Creates cash account deposit time frames for all tenants in the system. This method is responsible for generating
   * periodic snapshots or records of cash account deposits across all tenant accounts.
   */
  void createCashaccountDepositTimeFrameForAllTenant();

  /**
   * Creates cash account deposit time frames for a specific tenant. This method generates periodic snapshots or records
   * of cash account deposits for the tenant identified by the given ID.
   *
   * @param idTenant The ID of the tenant for whom to create the cash account deposit time frames.
   */
  void createCashaccountDepositTimeFrameByTenant(Integer idTenant);

  /**
   * Adjusts cash account deposits or withdrawals based on two transactions. This method is likely used to reconcile or
   * update cash account deposit records when new transactions occur or existing ones are modified.
   *
   * @param transaction1 The first transaction involved in the adjustment.
   * @param transaction2 The second transaction involved in the adjustment.
   */
  void adjustCashaccountDepositOrWithdrawal(Transaction transaction1, Transaction transaction2);

  /**
   * Adjusts the amounts of {@link grafioschtrader.entities.HoldCashaccountDeposit} due to changes in historical prices.
   * This method is called when history quote prices change, which affects the valuation of cash account holdings but
   * not the transaction dates themselves. Only the amounts within the HoldCashaccountDeposit records are updated.
   */
  void adjustBecauseOfHistoryquotePriceChanges();

}
