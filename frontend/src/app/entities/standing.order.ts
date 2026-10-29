import { Cashaccount } from './cashaccount';
import { Security } from '../entities/security';

/**
 * Base standing order with common scheduling fields. Discriminated by dtype: 'C' for cashaccount, 'S' for security.
 * Follows the same inheritance pattern as Securitycurrency / Security / Currencypair.
 */
export class StandingOrder {
  dtype?: string;
  idStandingOrder?: number | null = null;
  idTenant?: number | null = null;
  transactionType?: string | null = null;
  cashaccount?: Cashaccount | null = null;
  note?: string | null = null;
  repeatUnit?: string | null = null;
  repeatInterval?: number | null = null;
  dayOfExecution?: number | null = null;
  monthOfExecution?: number | null = null;
  periodDayPosition?: string | null = null;
  weekendAdjust?: string | null = null;
  quoteToleranceDays?: number | null = null;
  validFrom?: Date | null = null;
  validTo?: Date | null = null;
  lastExecutionDate?: Date | null = null;
  nextExecutionDate?: Date | null = null;
  transactionCost?: number | null = null;
  hasTransactions?: boolean;
  transactionCount?: number;
  failureCount?: number;
}

/**
 * Standing order for cash-account transactions (WITHDRAWAL=0, DEPOSIT=1, INTEREST_CASHACCOUNT=2 or FEE=3).
 *
 * The amount may be denominated in a foreign currency. In that case the exchange rate of the execution date is
 * applied, optionally through the EvalEx formula in cashaccountAmountFormula. idCurrencypair is derived from
 * amountCurrency by the backend and is read-only for the client.
 */
export class StandingOrderCashaccount extends StandingOrder {
  cashaccountAmount?: number | null = null;
  amountCurrency?: string | null = null;
  idCurrencypair?: number | null = null;
  cashaccountAmountFormula?: string | null = null;
}

/**
 * Standing order for security transactions (ACCUMULATE=4 or REDUCE=5).
 */
export class StandingOrderSecurity extends StandingOrder {
  security?: Security | null = null;
  idSecurityaccount?: number | null = null;
  idCurrencypair?: number | null = null;
  units?: number | null = null;
  investAmount?: number | null = null;
  amountIncludesCosts?: boolean | null = null;
  fractionalUnits?: boolean | null = null;
  taxCostFormula?: string | null = null;
  transactionCostFormula?: string | null = null;
  taxCost?: number | null = null;
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

/**
 * Inclusive bounds an administrator permits for quoteToleranceDays, derived from the global parameter
 * gt.standing.order.quote.tolerance. A negative bound allows searching the past first, a positive one the future.
 */
export interface QuoteToleranceRange {
  min: number;
  max: number;
}

export interface StandingOrderCapabilities {
  simulationTenant: boolean;
  cashStandingOrderSupported: boolean;
  securityStandingOrderSupported: boolean;
  minQuoteTolerance: number;
  maxQuoteTolerance: number;
}
