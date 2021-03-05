import {Transaction} from '../transaction';
import {TransactionPosition} from './transaction.position';

export class CashaccountTransactionPosition extends TransactionPosition {

  public idTransaction: number;

  constructor(transaction: Transaction,
              public balance: number) {
    super(transaction);
  }
}
