import { Transaction } from '../transaction';
import { TransactionPosition } from './transaction.position';
import { DisposalCostDetail } from './disposal.cost.detail';

/** A purchase a FIFO or LIFO sale consumed, in whole or in part. */
export interface CostLot {
  /** The purchase. */
  idTransaction: number;
  /** Time of the purchase as a local ISO date time. */
  transactionTime: string;
  /** Exchange rate to the main currency on the day of the purchase; null in the main currency. */
  exchangeRate: number | null;
  /** Units taken from the purchase, on the split basis of the report. */
  units: number;
  /** Cost assigned to these units in the currency of the security. */
  cost: number;
}

export class SecurityTransactionPosition extends TransactionPosition {
  /** Only on a hypothetical sale with the disposal cost estimate switched on: the estimated commission is incomplete. */
  public disposalTransactionCostIncomplete?: boolean;
  /** Only on a hypothetical sale with the disposal cost estimate switched on: the estimated tax is incomplete. */
  public disposalTaxCostIncomplete?: boolean;
  public disposalDetails?: DisposalCostDetail[];
  /** Only on a sale of a non-margin instrument: name of the cost basis method its gain was calculated with. */
  public costBasisMethod?: string;
  /** Only on a sale whose units the purchases did not cover; such a sale shows no percentage. */
  public costBasisIncomplete?: boolean;
  /** Only on a FIFO or LIFO sale: the purchases it consumed, in the order it consumed them. */
  public consumedLots?: CostLot[];

  constructor(
    transaction: Transaction,
    public transactionGainLoss: number,
    public transactionGainLossPercentage: number,
    public transactionExchangeRate: number,
    public transactionGainLossMC: number,
    public quotationSplitCorrection: number,
    public holdingsSplitAdjusted: number
  ) {
    super(transaction);
  }
}
