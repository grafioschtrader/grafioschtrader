package grafioschtrader.types;

/**
 * Conditions of a benchmark comparison that the reader must know about. The server decides every condition, so the
 * screen, the PDF and the MCP tool show the same notices. The constant names are the NLS keys, the parameters of a
 * notice fill their placeholders.
 */
public enum BenchmarkNoticeCode {
  /** No reliable quote of the instrument covers any valuation date of the period. */
  BENCHMARK_NO_QUOTES,
  /** Neither the direct nor the inverse currency pair into the main currency exists. Parameters: from, to. */
  BENCHMARK_NO_FX_PAIR,
  /** The currency pair exists but no reliable rate covers any valuation date. Parameters: from, to. */
  BENCHMARK_NO_FX_HISTORY,
  /** A reliable value is zero, negative or not finite. Parameters: date, leg. */
  BENCHMARK_INVALID_PRICE,
  /** The security keeps period valuations instead of a daily history. */
  BENCHMARK_PERIOD_PRICES,
  /** The comparison starts after the requested start. Parameter: startDate. */
  BENCHMARK_START_LATER,
  /** The comparison ends before the requested end. Parameters: endDate, lastInstrumentDate, lastRateDate. */
  BENCHMARK_STALE,
  /** An expected distributor has no usable ex-date in the check period of the dividend adjustment. */
  BENCHMARK_MISSING_DIVIDENDS,
  /** The portfolio had no usable capital in some intervals of the comparison span. */
  BENCHMARK_PARTIAL_EXPOSURE,
  /** A withdrawal the shadow portfolio could not fund ended it. Parameter: date. */
  BENCHMARK_SHADOW_SHORTFALL
}
