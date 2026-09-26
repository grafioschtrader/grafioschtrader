/** One of the largest trades the stored plan proposes. */
export interface AlgoMonitoringTrade {
  securityName: string;
  recommendedAction: string;
  recommendedAmount: number;
  recommendedUnits: number | null;
}

/**
 * The card payload: a summary of the stored live plan of the hierarchy assigned to monitoring. When
 * {@link reasonKey} is set there is nothing to summarize and every other field is empty.
 */
export interface AlgoMonitoringSummary {
  reasonKey: string | null;
  /** Nothing to do, drift reported between two checkpoints, or checkpoint due with trades. */
  statusKey: string | null;
  idAlgoTop: number;
  algoTopName: string;
  valuationDate: string;
  currency: string;
  periodicDue: boolean;
  lastCheckpointDate: string | null;
  nextCheckpointDate: string | null;
  buyCount: number;
  buyAmount: number;
  sellCount: number;
  sellAmount: number;
  blockedCount: number;
  largestDeviationBucket: string | null;
  /** Actual minus target share of that bucket, in percentage points of net equity. */
  largestDeviationPercentage: number | null;
  meanReversionSignals: number;
  topTrades: AlgoMonitoringTrade[];
}
