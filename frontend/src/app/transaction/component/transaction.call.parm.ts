import {TransactionType} from '../../shared/types/transaction.type';
import {Security} from '../../entities/security';
import {Securityaccount} from '../../entities/securityaccount';
import {Portfolio} from '../../entities/portfolio';
import {Transaction} from '../../entities/transaction';
import {Cashaccount} from '../../entities/cashaccount';

/**
 * It contains call parameters for the different transaction forms.
 */
export class TransactionCallParam {
  /**
   * When not null the transaction can not be set to another portfolio.
   */
  public portfolio: Portfolio = null;
  public transaction: Transaction = null;
  public transactionType: TransactionType;
  // Manly used for Security transaction
  public idSecuritycurrency: number;
  public security: Security = null;
  public securityaccount: Securityaccount = null;
  // Manly usd for Cashaccount transaction
  public cashaccount: Cashaccount;
  // User for security transaction, it is base for filling the
  // security select options.
  public idWatchList: number;

  public defaultTransactionTime;

  /**
   * Closing a margin trade, contains maximal number of units to close the position
   */
  public closeMarginPosition: CloseMarginPosition;
}

export class CloseMarginPosition {
  constructor(public quotationOpenPosition: number,
              public originUntis: number,
              public closeMaxMarginUnits: number,
              public idSecurityaccount: number,
              public idOpenMarginTransaction: number) {
  }
}


// http://stackoverflow.com/questions/38113489/typescript-json-arrays-optional-properties-typescript-is-too-helpful
