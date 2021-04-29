package grafioschtrader.dto;

import java.util.List;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import org.springframework.validation.annotation.Validated;

import grafioschtrader.common.DataHelper;
import grafioschtrader.entities.Transaction;
import grafioschtrader.entities.Transaction.CashTransaction;
import grafioschtrader.exceptions.DataViolationException;
import grafioschtrader.types.TransactionType;

@Validated
public class CashAccountTransfer {

  @NotNull
  @Valid
  private Transaction withdrawalTransaction;

  @NotNull
  @Valid
  private Transaction depositTransaction;

  public CashAccountTransfer() {
  }

  public CashAccountTransfer(Transaction withdrawalTransaction, Transaction depositTransaction) {
    this.withdrawalTransaction = withdrawalTransaction;
    this.depositTransaction = depositTransaction;
  }

  public CashAccountTransfer(List<Transaction> transactions) {
    this.withdrawalTransaction = transactions.get(0);
    this.depositTransaction = transactions.get(1);
  }

  public CashAccountTransfer(Transaction[] transactions) {
    int i = (transactions[0].getTransactionType() == TransactionType.WITHDRAWAL) ? 0 : 1;
    this.withdrawalTransaction = transactions[i];
    this.depositTransaction = transactions[(i + 1) % 2];
  }

  public Transaction[] getTransactionsAsArray() {
    return new Transaction[] { withdrawalTransaction, depositTransaction };
  }

  public Transaction getWithdrawalTransaction() {
    return withdrawalTransaction;
  }

  public void makeAbsToatalAmount() {
    // Afterwards it will be changed
    withdrawalTransaction.setCashaccountAmount(Math.abs(withdrawalTransaction.getCashaccountAmount()));
    depositTransaction.setCashaccountAmount(Math.abs(depositTransaction.getCashaccountAmount()));
    depositTransaction.setTransactionCost(null);
  }

  public void connectTransactions() {
    withdrawalTransaction.setConnectedIdTransaction(depositTransaction.getIdTransaction());
    depositTransaction.setConnectedIdTransaction(withdrawalTransaction.getIdTransaction());
  }

  public void setWithdrawalTransaction(Transaction withdrawalTransaction) {
    this.withdrawalTransaction = withdrawalTransaction;
  }

  public Transaction getDepositTransaction() {
    return depositTransaction;
  }

  @Validated(CashTransaction.class)
  public void setDepositTransaction(Transaction depositTransaction) {
    this.depositTransaction = depositTransaction;
  }

  public List<Transaction> getTransactionAsList() {
    return List.of(withdrawalTransaction, depositTransaction);
  }

  public void validateWithdrawalCashaccountAmount() {
    double transCost = withdrawalTransaction.getTransactionCost() != null ? withdrawalTransaction.getTransactionCost()
        : 0.0;
    double currencyExRate = withdrawalTransaction.getCurrencyExRate() != null
        ? withdrawalTransaction.getCurrencyExRate()
        : 1.0;

    // Calculate the withdrawal amount
    double calcWithCashaccountAmount = (DataHelper.divideMultiplyExchangeRate(depositTransaction.getCashaccountAmount(),
        currencyExRate, withdrawalTransaction.getCashaccount().getCurrency(),
        depositTransaction.getCashaccount().getCurrency(), true) + transCost) * -1;

    calcWithCashaccountAmount = DataHelper.round(calcWithCashaccountAmount, 2);

    double withCashaccountAmount = DataHelper.round(withdrawalTransaction.getCashaccountAmount(), 2);

    if (withCashaccountAmount == calcWithCashaccountAmount) {
      withdrawalTransaction.setCashaccountAmount(calcWithCashaccountAmount);
    } else {
      throw new DataViolationException("debit.amount", "gt.cashaccount.amount.calc", new Object[] {
          calcWithCashaccountAmount, withCashaccountAmount, withdrawalTransaction.getCashaccountAmount() });
    }
  }

  public void setToMinus() {
    this.withdrawalTransaction.setCashaccountAmount(Math.abs(withdrawalTransaction.getCashaccountAmount()) * -1);
  }

}
