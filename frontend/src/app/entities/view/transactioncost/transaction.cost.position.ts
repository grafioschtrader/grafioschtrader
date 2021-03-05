import {Transaction} from '../../transaction';

export class TransactionCostPosition {
  public transaction: Transaction;
  public basePriceForTransactionCostMC: number;
  public transactionCostMC: number;
  public taxCostMC: number;

}
