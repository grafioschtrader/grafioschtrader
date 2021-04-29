package grafioschtrader.repository;

import grafioschtrader.entities.Transaction;

public interface HoldCashaccountBalanceJpaRepositoryCustom {

  void createCashaccountBalanceEntireForAllTenants();

  void createCashaccountBalanceEntireByTenant(Integer idTenant);

  void adjustCashaccountBalanceByIdCashaccountAndFromDate(Transaction transaction);
}
