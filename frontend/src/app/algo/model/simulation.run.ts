/**
 * Definition, progress and result of one historical replay of a simulation environment.
 *
 * There is at most one per environment: a repeat run restores the recorded opening state and replaces this record
 * rather than adding a second one. Every metric is optional because only a completed run has any; a cancelled, failed
 * or interrupted run leaves them empty on purpose, so that a partial replay cannot be read as a finished one.
 */
export interface SimulationRunResult {
  applyTaxModels?: boolean;
  generateBondCoupons?: boolean;
  taxIncomeSummaryJson?: string;
  inputAssumptionsJson?: string;
  idSimulationResult: number;
  idAlgoTop: number;
  openingDate: string;
  endDate: string;
  status: SimulationRunStatus;
  startedAt: string;
  finishedAt?: string;
  tradingDaysTotal: number;
  tradingDaysDone: number;
  /** Space separated message keys of the assumptions the figures were calculated under. */
  conventions: string;
  totalReturn?: number;
  annualizedReturn?: number;
  maxDrawdown?: number;
  sharpeRatio?: number;
  totalTrades?: number;
  winningTrades?: number;
  losingTrades?: number;
  failureMessage?: string;
  dividendPaymentDelayDays?: number;
  /** Gross payments in tenant currency, using payment-date FX. */
  paidDividends?: number;
  /** Unpaid entitlements in tenant currency at the end date. */
  dividendReceivables?: number;
}

export interface SimulationFailureMessage {
  key: string;
  detail?: string;
}

/** Separates a translatable replay error key from its optional diagnostic, including legacy persisted failures. */
export function splitSimulationFailureMessage(failureMessage?: string): SimulationFailureMessage | undefined {
  if (!failureMessage) {
    return undefined;
  }
  const separator = failureMessage.indexOf(':');
  if (separator < 0) {
    return { key: failureMessage };
  }
  const detail = failureMessage.substring(separator + 1).trim();
  return {
    key: failureMessage.substring(0, separator).trim(),
    detail: detail && detail !== 'null' ? detail : undefined
  };
}

/**
 * Lifecycle of a historical replay.
 *
 * Corresponds to backend: grafioschtrader-common/src/main/java/grafioschtrader/types/AlgoSimulationRunStatus.java
 */
export enum SimulationRunStatus {
  RUNNING = 'RUNNING',
  COMPLETED = 'COMPLETED',
  CANCELLED = 'CANCELLED',
  RUN_FAILED = 'RUN_FAILED',
  INTERRUPTED = 'INTERRUPTED'
}

/**
 * What one row of the audit trail records.
 *
 * Corresponds to backend: grafioschtrader-common/src/main/java/grafioschtrader/types/AlgoEventType.java
 */
export enum AlgoEventType {
  RUN_START = 'RUN_START',
  OPENING_EXCLUDED_CLOSE = 'OPENING_EXCLUDED_CLOSE',
  ALLOCATION_PLAN = 'ALLOCATION_PLAN',
  ALLOCATION_FILL = 'ALLOCATION_FILL',
  DECISION = 'DECISION',
  FILL = 'FILL',
  BLOCKED = 'BLOCKED',
  UNAVAILABLE = 'UNAVAILABLE',
  REBALANCE_PLAN = 'REBALANCE_PLAN',
  REBALANCE_FILL = 'REBALANCE_FILL',
  FUNDING_TRANSFER = 'FUNDING_TRANSFER',
  CASH_STANDING_ORDER = 'CASH_STANDING_ORDER',
  DIVIDEND_ENTITLEMENT = 'DIVIDEND_ENTITLEMENT',
  DIVIDEND_PAYMENT = 'DIVIDEND_PAYMENT',
  MATURITY_REDEMPTION = 'MATURITY_REDEMPTION',
  TERMINAL_CLOSE = 'TERMINAL_CLOSE',
  RUN_END = 'RUN_END'
}

/** One decision, execution, refusal or unavailable outcome of a replay, on the closing day it belongs to. */
export interface SimulationRunEvent {
  idAlgoEvent: number;
  idAlgoStrategy?: number;
  idSecuritycurrency?: number;
  eventDate: string;
  eventType: AlgoEventType;
  rationale?: string;
  units?: number;
  price?: number;
  amount?: number;
  currency?: string;
  details?: string;
}
