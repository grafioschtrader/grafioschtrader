package grafioschtrader.repository;

import grafioschtrader.entities.Transaction;

public interface HoldCashaccountDepositJpaRepositoryCustom {

  void createCashaccountDepositTimeFrameForAllTenant();

  void createCashaccountDepositTimeFrameByTenant(Integer idTenant);

  void adjustCashaccountDepositOrWithdrawal(Transaction transaction1, Transaction transaction2);

  /**
   * Only the amounts of HoldCashaccountDeposit get changed because when history
   * prices changes, the dates of transaction are not affected.
   */
  void adjustBecauseOfHistoryquotePriceChanges();

}
