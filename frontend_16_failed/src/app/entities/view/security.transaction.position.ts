import {Transaction} from '../transaction';
import {TransactionPosition} from './transaction.position';

export class SecurityTransactionPosition extends TransactionPosition {

  constructor(transaction: Transaction,
              public transactionGainLoss: number,
              public transactionGainLossPercentage: number,
              public transactionExchangeRate: number,
              public transactionGainLossMC: number,
              public quotationSplitCorrection: number) {
    super(transaction);
  }

}
