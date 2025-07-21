import {TransactionType} from '../../shared/types/transaction.type';
import {Security} from '../../entities/security';
import {Securityaccount} from '../../entities/securityaccount';
import {Portfolio} from '../../entities/portfolio';
import {Transaction} from '../../entities/transaction';
import {Cashaccount} from '../../entities/cashaccount';

/**
 * Container class that holds call parameters for different transaction forms and dialogs.
 * This class centralizes all the necessary data required to initialize and configure various transaction editing
 * components, whether they are for security transactions, cash account transactions, or margin trading operations.
 * It supports both new transaction creation and existing transaction modification scenarios.
 */
export class TransactionCallParam {
  /** When not null, restricts the transaction to this specific portfolio and prevents portfolio changes */
  public portfolio: Portfolio = null;

  /** Existing transaction to be edited, null for new transaction creation */
  public transaction: Transaction = null;

  /** Type of transaction being performed (buy, sell, dividend, etc.) */
  public transactionType: TransactionType;

  /** Security currency identifier, mainly used for security transactions */
  public idSecuritycurrency: number;

  /** Security entity associated with the transaction */
  public security: Security = null;

  /** Security account where the transaction will be recorded */
  public securityaccount: Securityaccount = null;

  /** Cash account for cash-based transactions */
  public cashaccount: Cashaccount;

  /** Watchlist identifier used as basis for populating security select options in transaction forms */
  public idWatchList: number;

  /** Default transaction time to be pre-filled in transaction forms */
  public defaultTransactionTime;

  /** Configuration for closing margin trades, contains position limits and constraints */
  public closeMarginPosition: CloseMarginPosition;
}

/**
 * Configuration class for closing margin trading positions. Contains all necessary information to properly
 * close an existing margin position including position limits, quotation data, and related transaction references.
 */
export class CloseMarginPosition {
  /**
   * Creates a new CloseMarginPosition configuration.
   * @param quotationOpenPosition The quotation price when the margin position was originally opened
   * @param originUnits The original number of units in the margin position
   * @param closeMaxMarginUnits Maximum number of units that can be closed from this position
   * @param idSecurityaccount Security account identifier where the position is held
   * @param idOpenMarginTransaction Transaction identifier of the original margin opening transaction
   */
  constructor(public quotationOpenPosition: number,
              public originUnits: number,
              public closeMaxMarginUnits: number,
              public idSecurityaccount: number,
              public idOpenMarginTransaction: number) {
  }
}
