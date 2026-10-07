import { Transaction } from '../transaction';
import { TransactionPosition } from './transaction.position';
import { DisposalCostDetail } from './disposal.cost.detail';

export class SecurityTransactionPosition extends TransactionPosition {
  /** Only on a hypothetical sale with the disposal cost estimate switched on: the estimated commission is incomplete. */
  public disposalTransactionCostIncomplete?: boolean;
  /** Only on a hypothetical sale with the disposal cost estimate switched on: the estimated tax is incomplete. */
  public disposalTaxCostIncomplete?: boolean;
  public disposalDetails?: DisposalCostDetail[];

  constructor(
    transaction: Transaction,
    public transactionGainLoss: number,
    public transactionGainLossPercentage: number,
    public transactionExchangeRate: number,
    public transactionGainLossMC: number,
    public quotationSplitCorrection: number,
    public holdingsSplitAdjusted
  ) {
    super(transaction);
  }
}
