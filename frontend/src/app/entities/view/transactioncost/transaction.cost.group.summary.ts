import type { TransactionCostPosition } from './transaction.cost.position';
import type { Securityaccount } from '../../securityaccount';

export interface TransactionCostGroupSummary {
  securityaccount: Securityaccount;
  groupTotalTransactionCostMC: number;
  groupTotalTaxCostMc: number;
  groupTotalAverageTransactionCostMC: number;
  groupCountPaidTransaction: number;
  transactionCostPositions: TransactionCostPosition[];
}
