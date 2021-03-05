import {TransactionCostPosition} from './transaction.cost.position';
import {Securityaccount} from '../../securityaccount';

export class TransactionCostGroupSummary {
  public securityaccount: Securityaccount;
  public groupTotalTransactionCostMC: number;
  public groupTotalTaxCostMc: number;
  public groupTotalAverageTransactionCostMC: number;
  public groupCountPaidTransaction: number;
  public transactionCostPositions: TransactionCostPosition[];
}
