import type { Transaction } from '../../transaction';

export interface TransactionCostPosition {
  transaction: Transaction;
  basePriceForTransactionCostMC: number;
  transactionCostMC: number;
  taxCostMC: number;
}
