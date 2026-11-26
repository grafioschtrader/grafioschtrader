package grafioschtrader.types;

/**
 * Whether a window or a step of the period performance has a benchmark comparison. The constant names are the NLS keys
 * that explain an empty comparison cell.
 */
public enum BenchmarkCoverage {
  /** The window lies inside the comparison span and holds at least one common interval. */
  BENCHMARK_COVERED,
  /** The window reaches over the start or the end of the comparison span, so a comparison would mix two periods. */
  BENCHMARK_PARTIAL,
  /** The window lies outside the comparison span or holds no common interval. */
  BENCHMARK_NONE
}
