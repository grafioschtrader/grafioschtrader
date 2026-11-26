package grafioschtrader.types;

/**
 * What the return of a benchmark contains. GT cannot tell a price index from a performance index, so the report names
 * the basis instead of calling a series a total return. The constant names are the NLS keys shown to the user.
 */
public enum BenchmarkReturnBasis {
  /** A dividend-capable security whose recorded gross distributions are reinvested on their ex-dates. */
  DIVIDENDS_REINVESTED,
  /** The series as supplied: indices, derived instruments, currency pairs and securities that pay nothing. */
  AS_SUPPLIED
}
