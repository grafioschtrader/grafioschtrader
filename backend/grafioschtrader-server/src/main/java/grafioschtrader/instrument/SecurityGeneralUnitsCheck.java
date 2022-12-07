package grafioschtrader.instrument;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import grafioschtrader.GlobalConstants;
import grafioschtrader.common.DataHelper;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitysplit;
import grafioschtrader.entities.Transaction;
import grafioschtrader.exceptions.DataViolationException;
import grafioschtrader.repository.SecuritysplitJpaRepository;
import grafioschtrader.types.OperationType;
import grafioschtrader.types.TransactionType;

/**
 * For stocks, bond, ETF a check for units is executed. In this way it is not
 * possible does units gets less than zero or dividend is paid for non existing
 * units.
 *
 *
 * @author Hugo Graf
 *
 */
public class SecurityGeneralUnitsCheck {

  public static void checkUnitsIntegrity(final SecuritysplitJpaRepository securitysplitJpaRepository,
      final OperationType operationyType, final List<Transaction> transactions, final Transaction targetTransaction,
      final Security security) {

    final Map<Integer, List<Securitysplit>> securitySplitMap = securitysplitJpaRepository
        .getSecuritysplitMapByIdSecuritycurrency(security.getIdSecuritycurrency());

    final List<TransactionTimeUnits> transactionTimeUnits = new ArrayList<>();

    final DataViolationException dataViolationException = new DataViolationException();

    List<Transaction> transactionAfter = addUpdateRemoveToTransaction(operationyType, transactions, targetTransaction);

    reorderTransactionForDividends(transactionAfter);

    transactionAfter.forEach(transaction -> {
      checkUnitsIntegrity(securitySplitMap, security, transaction, transactionTimeUnits, dataViolationException);
      if (!transactionTimeUnits.isEmpty() && transactionTimeUnits.get(0).units < 0.0) {
        System.out.println( transactionTimeUnits.get(0).units);
        dataViolationException.addDataViolation(GlobalConstants.UNITS, "units.less.zero",
            transaction.getTransactionTime());
      }
    });

    if (dataViolationException.hasErrors()) {
      throw dataViolationException;
    }

  }

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

  private static boolean checkDividendUnits(final double requiredUnits, final Transaction transaction,
      final List<TransactionTimeUnits> transactionTimeUnits) {

    final Date transactionExDate = (transaction.getExDate() != null) ? transaction.getExDate()
        : transaction.getTransactionTime();

    for (final TransactionTimeUnits ttU : transactionTimeUnits) {
      // At dividend time there are enough units or they at least they has been sold
      // less than a month before dividend time
      if (ttU.units >= requiredUnits || ttU.transaction.getTransactionType() == TransactionType.REDUCE
          && ttU.units >= requiredUnits && ttU.transaction.getTransactionTime().after(transactionExDate)) {
        return true;
      }
    }
    return false;
  }

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

class TransactionTimeUnits {
  public Transaction transaction;
  public double units;

  public TransactionTimeUnits(final Transaction transaction, final double units) {
    super();
    this.transaction = transaction;
    this.units = DataHelper.round(units);
  }

}
