import type { PerformanceBenchmarkResult } from '../../performanceperiod/model/performance.period';

/**
 * Comparison of a completed historical replay with a benchmark. Mirrors the backend
 * grafioschtrader.service.ReplayBenchmarkComparison. Every return is in percent already and must not be scaled again,
 * unlike the decimal ratios of the replay result.
 */
export interface ReplayBenchmarkComparison {
  /** The comparison in the currency of the simulation environment; flows count at the start of a day. */
  comparison: PerformanceBenchmarkResult;
  /** Moment the server calculated the comparison, yyyy-MM-dd HH:mm:ss. */
  calculatedAt: string;
}
