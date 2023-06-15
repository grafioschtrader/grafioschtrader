import {TransactionCostGroupSummary} from './transaction.cost.group.summary';

export class TransactionCostGrandSummary {
  public transactionCostGroupSummaries: TransactionCostGroupSummary[];
  public mainCurrency: string;
  public grandTotalTransactionCostMC: number;
}
