import type { SecurityPositionGroupSummary } from './security.position.group.summary';

/** Security-position totals grouped by instrument currency. */
export interface SecurityPositionCurrenyGroupSummary extends SecurityPositionGroupSummary {
  currencyExchangeRate: number;
  currency: string;
  groupGainLossSecurity: number;
  groupTransactionCost: number;
  groupAccountValueSecurity: number;
  groupTaxCost: number;
}
