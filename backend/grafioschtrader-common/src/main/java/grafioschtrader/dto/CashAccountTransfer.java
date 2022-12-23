package grafioschtrader.dto;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.annotation.Validated;

import grafioschtrader.GlobalConstants;
import grafioschtrader.common.DataHelper;
import grafioschtrader.entities.Transaction;
import grafioschtrader.entities.Transaction.CashTransaction;
import grafioschtrader.exceptions.DataViolationException;
import grafioschtrader.types.TransactionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@Validated
public class CashAccountTransfer {

  private static final Logger log = LoggerFactory.getLogger(CashAccountTransfer.class);

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

  public void validateWithdrawalCashaccountAmount(Integer withdrawalCurrencyFraction) {

    // Calculate the withdrawal amount
    double calcWithCashaccountAmountExact = (DataHelper.divideMultiplyExchangeRate(
        depositTransaction.getCashaccountAmount(), getCurrencyExRate(),
        withdrawalTransaction.getCashaccount().getCurrency(), depositTransaction.getCashaccount().getCurrency(), true)
        + getTransactionCost()) * -1;

    double calcWithCashaccountAmount = DataHelper.round(calcWithCashaccountAmountExact, withdrawalCurrencyFraction);

    double withCashaccountAmount = DataHelper.round(withdrawalTransaction.getCashaccountAmount(),
        withdrawalCurrencyFraction);

    if (withCashaccountAmount == calcWithCashaccountAmount) {
      if (GlobalConstants.AUTO_CORRECT_TO_AMOUNT) {
        double difference = calcWithCashaccountAmount
            - DataHelper.round(calcWithCashaccountAmountExact, withdrawalCurrencyFraction + 1);
        if (difference != 0.0) {
          correctExChangeRageToMatchAmounts(withdrawalCurrencyFraction, difference);
        }
      }
      withdrawalTransaction.setCashaccountAmount(calcWithCashaccountAmount);

    } else {
      throw new DataViolationException("debit.amount", "gt.cashaccount.amount.calc", new Object[] {
          calcWithCashaccountAmount, withCashaccountAmount, withdrawalTransaction.getCashaccountAmount() });
    }
  }

  private void correctExChangeRageToMatchAmounts(Integer withdrawalCurrencyFraction, double difference) {
    double withoutTransCost = Math.abs(DataHelper
        .round(withdrawalTransaction.getCashaccountAmount() - getTransactionCost(), withdrawalCurrencyFraction));
    double exactCurrencyExRate = DataHelper.round(depositTransaction.getCashaccountAmount() / withoutTransCost,
        GlobalConstants.FID_MAX_FRACTION_DIGITS);
    withdrawalTransaction.setCurrencyExRate(exactCurrencyExRate);
    depositTransaction.setCurrencyExRate(exactCurrencyExRate);
    log.debug("Corrected currency exchange rate for difference {} from {} to {}", difference, getCurrencyExRate(),
        exactCurrencyExRate);
  }

  private double getCurrencyExRate() {
    return withdrawalTransaction.getCurrencyExRate() != null ? withdrawalTransaction.getCurrencyExRate() : 1.0;
  }

  private double getTransactionCost() {
    return withdrawalTransaction.getTransactionCost() != null ? withdrawalTransaction.getTransactionCost() : 0.0;
  }

  public void setToMinus() {
    this.withdrawalTransaction.setCashaccountAmount(Math.abs(withdrawalTransaction.getCashaccountAmount()) * -1);
  }

}
