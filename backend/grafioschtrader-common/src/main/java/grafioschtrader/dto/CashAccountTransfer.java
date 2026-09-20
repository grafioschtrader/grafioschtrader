package grafioschtrader.dto;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.annotation.Validated;

import grafiosch.BaseConstants;
import grafiosch.common.DataHelper;
import grafiosch.exceptions.DataViolationException;
import grafioschtrader.GlobalConstants;
import grafioschtrader.common.DataBusinessHelper;
import grafioschtrader.entities.Transaction;
import grafioschtrader.entities.Transaction.CashTransaction;
import grafioschtrader.types.TransactionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@Schema(description = """
    Represents a transfer between two cash accounts, consisting of a withdrawal from one account and a deposit to another.
    Handles currency conversion and exchange rate validation for cross-currency transfers.""")
@Validated
public class CashAccountTransfer {

  private static final Logger log = LoggerFactory.getLogger(CashAccountTransfer.class);

  @Schema(description = "The withdrawal transaction that debits the source cash account")
  @NotNull
  @Valid
  private Transaction withdrawalTransaction;
  @Schema(description = "The deposit transaction that credits the target cash account")
  @NotNull
  @Valid
  private Transaction depositTransaction;

  public CashAccountTransfer() {
  }

  public CashAccountTransfer(Transaction withdrawalTransaction, Transaction depositTransaction) {
    this.withdrawalTransaction = withdrawalTransaction;
    this.depositTransaction = depositTransaction;
  }

  /**
   * Creates a cash account transfer from a list of exactly two transactions, assigning the sides by transaction type
   * rather than by position.
   *
   * @param transactions list holding the two sides in either order
   * @throws DataViolationException if the two do not form a withdrawal/deposit pair
   */
  public CashAccountTransfer(List<Transaction> transactions) {
    assignByType(transactions.get(0), transactions.get(1));
  }

  /**
   * Creates a cash account transfer from an array of transactions. Automatically determines which transaction is the
   * withdrawal and which is the deposit.
   *
   * @param transactions array containing exactly two transactions (one withdrawal, one deposit); one {@code null}
   *                     element is tolerated, because the existing pair of an update may carry only the persisted side
   * @throws DataViolationException if both are present and do not form a withdrawal/deposit pair
   */
  public CashAccountTransfer(Transaction[] transactions) {
    assignByType(transactions[0], transactions[1]);
  }

  /**
   * Assigns the two sides by transaction type instead of by position, so that a caller which does not know the order
   * cannot label a deposit as the withdrawal. A {@code null} element is kept on the side the present one does not
   * claim, because the existing pair of an update may carry only the side that is already persisted.
   *
   * @param first  one side of the transfer, or {@code null}
   * @param second the other side of the transfer, or {@code null}
   * @throws DataViolationException if both sides are present and do not form a withdrawal/deposit pair
   */
  private void assignByType(Transaction first, Transaction second) {
    if (first == null && second == null) {
      return;
    }
    if (first != null && second != null) {
      validatePair(first, second);
    }
    boolean firstIsWithdrawal = first == null ? second.getTransactionType() != TransactionType.WITHDRAWAL
        : first.getTransactionType() == TransactionType.WITHDRAWAL;
    this.withdrawalTransaction = firstIsWithdrawal ? first : second;
    this.depositTransaction = firstIsWithdrawal ? second : first;
  }

  /**
   * Rejects the two shapes that {@link #connectTransactions()} would turn into a corrupt {@code con_id_transaction}:
   * two sides that are in fact the same row, and a pair that is not exactly one withdrawal and one deposit. The first
   * writes {@code con_id_transaction = id_transaction}, the second links two transactions of the same type; neither is
   * forbidden by a database constraint and both later abort the rebuild of the cash account deposit holdings, because
   * that rebuild resolves the counterpart and insists on a real withdrawal/deposit pair.
   *
   * @param first  one side of the transfer, never {@code null}
   * @param second the other side of the transfer, never {@code null}
   * @throws DataViolationException if the two do not form a usable transfer pair
   */
  public static void validatePair(Transaction first, Transaction second) {
    if (first.getIdTransaction() != null && first.getIdTransaction().equals(second.getIdTransaction())) {
      throw new DataViolationException("transaction.type", "gt.transfer.same.transaction",
          new Object[] { first.getIdTransaction() });
    }
    boolean withdrawalAndDeposit = first.getTransactionType() == TransactionType.WITHDRAWAL
        && second.getTransactionType() == TransactionType.DEPOSIT
        || first.getTransactionType() == TransactionType.DEPOSIT
            && second.getTransactionType() == TransactionType.WITHDRAWAL;
    if (!withdrawalAndDeposit) {
      throw new DataViolationException("transaction.type", "gt.transfer.not.pair", new Object[] {});
    }
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

  /**
   * Validates that the withdrawal amount matches the calculated amount based on the deposit and exchange rate. Performs
   * currency conversion calculations and optionally auto-corrects exchange rates when enabled.
   *
   * @param withdrawalCurrencyFraction the number of decimal places for the withdrawal currency
   * @throws DataViolationException if calculated and actual withdrawal amounts don't match
   */
  public void validateWithdrawalCashaccountAmount(Integer withdrawalCurrencyFraction) {

    // Calculate the withdrawal amount
    double calcWithCashaccountAmountExact = (DataBusinessHelper.divideMultiplyExchangeRate(
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

  /**
   * Corrects the exchange rate to match the exact transaction amounts. This method is called when auto-correction is
   * enabled and there's a small difference between calculated and actual amounts due to rounding.
   *
   * @param withdrawalCurrencyFraction the number of decimal places for the withdrawal currency
   * @param difference                 the difference between calculated and rounded amounts
   */
  private void correctExChangeRageToMatchAmounts(Integer withdrawalCurrencyFraction, double difference) {
    double withoutTransCost = Math.abs(DataHelper
        .round(withdrawalTransaction.getCashaccountAmount() - getTransactionCost(), withdrawalCurrencyFraction));
    double exactCurrencyExRate = DataHelper.round(depositTransaction.getCashaccountAmount() / withoutTransCost,
        BaseConstants.FID_MAX_FRACTION_DIGITS);
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
