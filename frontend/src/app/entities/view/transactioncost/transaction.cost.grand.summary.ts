import type { TransactionCostGroupSummary } from './transaction.cost.group.summary';

export interface TransactionCostGrandSummary {
  transactionCostGroupSummaries: TransactionCostGroupSummary[];
  mainCurrency: string;
  grandTotalTransactionCostMC: number;
}
