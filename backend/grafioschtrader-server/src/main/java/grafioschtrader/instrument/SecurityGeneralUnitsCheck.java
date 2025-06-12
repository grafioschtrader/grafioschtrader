package grafioschtrader.instrument;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import grafiosch.exceptions.DataViolationException;
import grafiosch.types.OperationType;
import grafioschtrader.GlobalConstants;
import grafioschtrader.common.DataBusinessHelper;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitysplit;
import grafioschtrader.entities.Transaction;
import grafioschtrader.repository.SecuritysplitJpaRepository;
import grafioschtrader.types.TransactionType;

/**
 * Units integrity checker for general securities (stocks, bonds, ETFs).
 * 
 * <p>
 * This class validates that security transactions maintain proper units integrity by ensuring:
 * <ul>
 * <li>Units never go below zero after any transaction sequence</li>
 * <li>Dividends are only paid for existing units at the ex-dividend date</li>
 * <li>Security splits are properly accounted for in calculations</li>
 * <li>Transaction ordering respects business rules for same-day transactions</li>
 * </ul>
 * 
 * <p>
 * The validation considers security splits and adjusts units accordingly to ensure historical transactions remain valid
 * after split events.
 * </p>
 */
public class SecurityGeneralUnitsCheck {

  /**
   * Validates units integrity for a security transaction within the context of all related transactions.
   * 
   * <p>
   * This method performs comprehensive validation by:
   * <ul>
   * <li>Applying or removing the target transaction from the transaction list</li>
   * <li>Reordering transactions to ensure proper same-day transaction sequencing</li>
   * <li>Calculating running units balances with security split adjustments</li>
   * <li>Validating that units never go negative and dividends have sufficient backing units</li>
   * </ul>
   * 
   * @param securitysplitJpaRepository repository for retrieving security split data
   * @param operationType              the type of operation being performed (ADD, UPDATE, DELETE)
   * @param transactions               existing transactions for the security in the same security account
   * @param targetTransaction          the transaction being validated
   * @param security                   the security entity containing split information
   * @throws DataViolationException if units would go negative or dividend validation fails
   */
  public static void checkUnitsIntegrity(final SecuritysplitJpaRepository securitysplitJpaRepository,
      final OperationType operationType, final List<Transaction> transactions, final Transaction targetTransaction,
      final Security security) {

    final Map<Integer, List<Securitysplit>> securitySplitMap = securitysplitJpaRepository
        .getSecuritysplitMapByIdSecuritycurrency(security.getIdSecuritycurrency());

    final List<TransactionTimeUnits> transactionTimeUnits = new ArrayList<>();
    final DataViolationException dataViolationException = new DataViolationException();
    List<Transaction> transactionAfter = addUpdateRemoveToTransaction(operationType, transactions, targetTransaction);
    reorderTransactionForDividends(transactionAfter);
    transactionAfter.forEach(transaction -> {
      checkUnitsIntegrity(securitySplitMap, security, transaction, transactionTimeUnits, dataViolationException);
      if (!transactionTimeUnits.isEmpty() && transactionTimeUnits.get(0).units < 0.0) {
        dataViolationException.addDataViolation(GlobalConstants.UNITS, "units.less.zero",
            transaction.getTransactionTime());
      }
    });

    if (dataViolationException.hasErrors()) {
      throw dataViolationException;
    }

  }

  /**
   * Applies the target transaction to the transaction list based on the operation type. For ADD/UPDATE operations,
   * inserts the transaction in chronological order. For DELETE operations, excludes the transaction from the list.
   * 
   * @param operationType     the type of operation (ADD, UPDATE, DELETE)
   * @param transactions      the existing list of transactions
   * @param targetTransaction the transaction to add, update, or remove
   * @return modified transaction list with the operation applied
   */
  private static List<Transaction> addUpdateRemoveToTransaction(final OperationType operationyType,
      List<Transaction> transactions, final Transaction targetTransaction) {
    List<Transaction> transactionsAfter = new ArrayList<>();
    boolean added = false;
    for (Transaction transaction : transactions) {
      if (targetTransaction.getIdTransaction() != null
          && targetTransaction.getIdTransaction().equals(transaction.getIdTransaction())) {
        continue;
      }
      if (!added && (operationyType == OperationType.ADD || operationyType == OperationType.UPDATE)) {
        if (targetTransaction.getTransactionTime().before(transaction.getTransactionTime())) {
          added = true;
          transactionsAfter.add(targetTransaction);
        }
      }
      transactionsAfter.add(transaction);
    }
    if (!added && (operationyType == OperationType.ADD || operationyType == OperationType.UPDATE)) {
      transactionsAfter.add(targetTransaction);
    }
    return transactionsAfter;
  }

  /**
   * Validates a single transaction's impact on units integrity within the transaction sequence. Calculates
   * split-adjusted units and updates the running balance for ACCUMULATE/REDUCE transactions. For DIVIDEND transactions,
   * validates that sufficient units exist at the ex-dividend date.
   * 
   * @param securitySplitMap       map of security splits by security ID for split factor calculations
   * @param security               the security being transacted
   * @param transaction            the transaction to validate
   * @param transactionTimeUnits   running list of transaction units (modified by this method)
   * @param dataViolationException exception collector for validation errors
   */
  private static void checkUnitsIntegrity(final Map<Integer, List<Securitysplit>> securitySplitMap,
      final Security security, final Transaction transaction, final List<TransactionTimeUnits> transactionTimeUnits,
      final DataViolationException dataViolationException) {
    final double splitfactor = Securitysplit.calcSplitFatorForFromDate(security.getIdSecuritycurrency(),
        transaction.getTransactionTime(), securitySplitMap);
    final double unitsSplited = transaction.getUnits() * splitfactor;
    final double units = transactionTimeUnits.isEmpty() ? 0 : transactionTimeUnits.get(0).units;
    switch (transaction.getTransactionType()) {
    case ACCUMULATE:
      transactionTimeUnits.add(0, new TransactionTimeUnits(transaction, units + unitsSplited));
      break;
    case REDUCE:
      transactionTimeUnits.add(0, new TransactionTimeUnits(transaction, units - unitsSplited));
      break;
    case DIVIDEND:
      if (!checkDividendUnits(unitsSplited, transaction, transactionTimeUnits)) {
        dataViolationException.addDataViolation(GlobalConstants.UNITS, "dividend.units.exceeds",
            transaction.getTransactionTime());
      }
      break;
    default:
      break;
    }
  }

  /**
   * Validates that sufficient units exist for a dividend payment. Checks the transaction history to ensure enough units
   * were held at the ex-dividend date. Uses the transaction's ex-date if available, otherwise falls back to transaction
   * time.
   * 
   * @param requiredUnits        the number of units for which dividend is being paid (split-adjusted)
   * @param transaction          the dividend transaction being validated
   * @param transactionTimeUnits chronological list of transaction units to check against
   * @return true if sufficient units exist for the dividend, false otherwise
   */
  private static boolean checkDividendUnits(final double requiredUnits, final Transaction transaction,
      final List<TransactionTimeUnits> transactionTimeUnits) {

    final Date transactionExDate = (transaction.getExDate() != null) ? transaction.getExDate()
        : transaction.getTransactionTime();

    for (final TransactionTimeUnits ttU : transactionTimeUnits) {
      if (ttU.units >= requiredUnits || ttU.transaction.getTransactionType() == TransactionType.REDUCE
          && ttU.units >= requiredUnits && ttU.transaction.getTransactionTime().after(transactionExDate)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Reorders transactions that occur on the same day to ensure proper business logic sequence.
   * 
   * <p>
   * Applies these ordering rules for same-day transactions:
   * <ul>
   * <li>ACCUMULATE transactions come before DIVIDEND transactions</li>
   * <li>DIVIDEND transactions come before REDUCE transactions</li>
   * </ul>
   * 
   * <p>
   * This ensures that dividends are calculated after stock purchases but before sales when they occur on the same
   * trading day.
   * </p>
   * 
   * @param transactions the list of transactions to reorder (modified in place)
   */
  private static void reorderTransactionForDividends(final List<Transaction> transactions) {
    for (int i = 0; i < transactions.size(); i++) {
      if (i + 1 < transactions.size()
          && transactions.get(i).getTransactionTime().equals(transactions.get(i + 1).getTransactionTime())) {
        if (transactions.get(i).getTransactionType() == TransactionType.DIVIDEND
            && transactions.get(i + 1).getTransactionType() == TransactionType.ACCUMULATE
            || transactions.get(i).getTransactionType() == TransactionType.REDUCE
                && transactions.get(i + 1).getTransactionType() == TransactionType.DIVIDEND) {
          final Transaction swapTransaction = transactions.get(i);
          transactions.set(i, transactions.get(i + 1));
          transactions.set(i + 1, swapTransaction);
        }
      }
    }
  }
}

/**
 * Helper class that represents a transaction with its calculated units balance. Used internally for tracking running
 * units balances during validation.
 */
class TransactionTimeUnits {
  /** The transaction being tracked */
  public Transaction transaction;
  /** The calculated units balance after this transaction (including split adjustments) */
  public double units;

  /**
   * Creates a transaction-units pair with rounded units for precision.
   * 
   * @param transaction the transaction
   * @param units       the calculated units balance (will be rounded)
   */
  public TransactionTimeUnits(final Transaction transaction, final double units) {
    super();
    this.transaction = transaction;
    this.units = DataBusinessHelper.round(units);
  }

}
