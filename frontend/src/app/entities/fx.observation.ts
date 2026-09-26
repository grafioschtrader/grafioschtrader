/** Percent statistics against exact-date EOD closes, including execution-timing deviations. */
export interface FxObservationReport {
  groups: FxObservationGroup[];
}

export interface FxObservationGroup {
  idCurrencypair: number;
  currencyPair: string;
  kind: 'TRADE' | 'INCOME' | 'TRANSFER';
  periodFrom: string | null;
  periodTo: string | null;
  count: number;
  skippedNoClose: number;
  skippedNoRate: number;
  skippedOutlier: number;
  mean: number | null;
  median: number | null;
  stdDev: number | null;
  volumeWeightedMean: number | null;
  modelledCount: number | null;
  modelledMean: number | null;
  uncoveredCount: number;
  outcomes: Partial<Record<'MATCHED' | 'NO_SECTION' | 'NO_PERIOD' | 'NO_RULE' | 'NO_TIER_RATE' | 'INVALID', number>>;
  error: string | null;
}
