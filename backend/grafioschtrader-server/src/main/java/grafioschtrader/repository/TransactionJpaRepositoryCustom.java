package grafioschtrader.repository;

import java.util.List;

import grafioschtrader.dto.CashAccountTransfer;
import grafioschtrader.dto.ClosedMarginUnits;
import grafioschtrader.entities.Transaction;
import grafioschtrader.reportviews.currencypair.CurrencypairWithTransaction;
import grafioschtrader.reportviews.transaction.CashaccountTransactionPosition;

public interface TransactionJpaRepositoryCustom extends BaseRepositoryCustom<Transaction> {

  List<Transaction> getSecurityAccountWithFeesAndIntrerestTransactionsByTenant(Integer idTenant);

  CashaccountTransactionPosition[] getTransactionsWithSaldoForCashaccount(Integer idSecuritycashAccount);

  void deleteSingleDoubleTransaction(Integer idTransaction);

  /**
   * This may be used when a lot of transaction must be processed, e.g import
   * transactions.
   *
   * @param transaction
   * @param existingEntity
   * @return
   */
  Transaction saveOnlyAttributesFormImport(final Transaction transaction, Transaction existingEntity);

  CashAccountTransfer updateCreateCashaccountTransfer(CashAccountTransfer cashAccountTransfer,
      CashAccountTransfer cashAccountTransferExisting);

  CurrencypairWithTransaction getTransactionForCurrencyPair(Integer idTenant, Integer idCurrencypair, boolean forChart);

  List<Transaction> getTransactionsByIdPortfolio(Integer idPortfolio, Integer idTenant);

  ClosedMarginUnits getClosedMarginUnitsByIdTransaction(final Integer idTransaction);

}
