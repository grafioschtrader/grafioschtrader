package grafioschtrader.types;

/**
 * Where the benchmark of a period performance comparison came from. The constant names are the NLS keys shown to the
 * user; the value is neither persisted nor carries a byte value.
 */
public enum BenchmarkSource {
  /** Chosen for this request only, overriding any stored benchmark. */
  BENCHMARK_SOURCE_REQUEST,
  /** The benchmark stored with the portfolio. */
  BENCHMARK_SOURCE_PORTFOLIO,
  /** The benchmark stored with the tenant, the default of the client view and of every portfolio without its own. */
  BENCHMARK_SOURCE_TENANT,
  /** The benchmark of the main tenant, inherited by a simulation environment without its own. */
  BENCHMARK_SOURCE_PARENT_TENANT
}
