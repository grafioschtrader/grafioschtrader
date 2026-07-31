package grafioschtrader.repository;

import grafioschtrader.entities.Transaction;
import grafioschtrader.repository.helper.TransactionPreImage;

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
   * Adjusts cash account deposits or withdrawals for one transaction, or for both sides of a cash account transfer. Used
   * to reconcile the deposit hold rows when a DEPOSIT or WITHDRAWAL transaction is created, modified or deleted.
   *
   * <p>
   * Each transaction is accompanied by its pre-image, because the hold rows are cumulative running totals per cash
   * account: an update that moves a transaction to another account or to a later date invalidates rows that the new
   * coordinates alone can never reach. Pass {@code null} for a create or a delete.
   * </p>
   *
   * @param transaction1 The first transaction involved in the adjustment.
   * @param preImage1    Where {@code transaction1} was booked before the update, or {@code null}.
   * @param transaction2 The second transaction involved in the adjustment, {@code null} when there is only one.
   * @param preImage2    Where {@code transaction2} was booked before the update, or {@code null}.
   */
  void adjustCashaccountDepositOrWithdrawal(Transaction transaction1, TransactionPreImage preImage1,
      Transaction transaction2, TransactionPreImage preImage2);

  /**
   * Adjusts the amounts of {@link grafioschtrader.entities.HoldCashaccountDeposit} due to changes in historical prices.
   * This method is called when history quote prices change, which affects the valuation of cash account holdings but
   * not the transaction dates themselves. Only the amounts within the HoldCashaccountDeposit records are updated.
   */
  void adjustBecauseOfHistoryquotePriceChanges();

}
