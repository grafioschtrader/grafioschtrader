import {Cashaccount} from './cashaccount';
import {Security} from '../entities/security';

/**
 * Base standing order with common scheduling fields. Discriminated by dtype: 'C' for cashaccount, 'S' for security.
 * Follows the same inheritance pattern as Securitycurrency / Security / Currencypair.
 */
export class StandingOrder {
  dtype?: string;
  idStandingOrder?: number = null;
  idTenant?: number = null;
  transactionType?: string = null;
  cashaccount?: Cashaccount = null;
  note?: string = null;
  repeatUnit?: string = null;
  repeatInterval?: number = null;
  dayOfExecution?: number = null;
  monthOfExecution?: number = null;
  periodDayPosition?: string = null;
  weekendAdjust?: string = null;
  validFrom?: Date = null;
  validTo?: Date = null;
  lastExecutionDate?: Date = null;
  nextExecutionDate?: Date = null;
  transactionCost?: number = null;
  hasTransactions?: boolean;
  failureCount?: number;
}

/**
 * Standing order for cash-account transactions (WITHDRAWAL=0 or DEPOSIT=1).
 */
export class StandingOrderCashaccount extends StandingOrder {
  cashaccountAmount?: number = null;
}

/**
 * Standing order for security transactions (ACCUMULATE=4 or REDUCE=5).
 */
export class StandingOrderSecurity extends StandingOrder {
  security?: Security = null;
  idSecurityaccount?: number = null;
  idCurrencypair?: number = null;
  units?: number = null;
  investAmount?: number = null;
  amountIncludesCosts?: boolean = null;
  fractionalUnits?: boolean = null;
  taxCostFormula?: string = null;
  transactionCostFormula?: string = null;
  taxCost?: number = null;
}

/**
 * Persisted failure record for a standing order execution attempt.
 */
export class StandingOrderFailure {
  idStandingOrderFailure?: number;
  idStandingOrder?: number;
  executionDate?: string;
  businessError?: string;
  unexpectedError?: string;
  createdAt?: string;
}
