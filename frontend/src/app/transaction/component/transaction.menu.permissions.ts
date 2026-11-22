import { Transaction } from '../../entities/transaction';

/** Menu permissions mirror opening-ledger protection while retaining the existing transaction-type rules. */
export function transactionMenuPermissions(transaction: Transaction | null, canDelete = true) {
  const persisted = !!transaction?.idTransaction;
  const editable = persisted && !transaction.simulationOpening && !transaction.securityActionRole;
  return {
    edit: editable,
    delete: editable && canDelete,
    transfer:
      editable && Transaction.isWithdrawalOrDeposit(transaction.transactionType) && !transaction.connectedIdTransaction,
    standingOrder:
      persisted &&
      !transaction.securityActionRole &&
      (Transaction.isSecurityTransaction(transaction.transactionType) ||
        Transaction.isWithdrawalOrDeposit(transaction.transactionType)),
    taxableInterest: editable && Transaction.isDividendOrInterest(transaction.transactionType)
  };
}
