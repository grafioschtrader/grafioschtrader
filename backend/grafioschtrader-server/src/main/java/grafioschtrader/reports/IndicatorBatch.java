package grafioschtrader.reports;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import grafioschtrader.reportviews.securitycurrency.SecuritycurrencyIndicatorGroup.BenchmarkInfo;
import grafioschtrader.reportviews.securitycurrency.SecuritycurrencyIndicatorGroup.FundamentalInfo;
import grafioschtrader.reportviews.securitycurrency.SecuritycurrencyIndicatorGroup.IndicatorCell;
import grafioschtrader.types.IndicatorCategory;

/**
 * Result of one evaluation of indicator columns over a list of instruments by
 * {@link WatchlistIndicatorReport#evaluate}. Besides the cells it carries what the watchlist indicator tab shows around
 * them, so that the tab and the instrument search share one loading and one evaluation pass.
 */
public class IndicatorBatch {

  /** Per instrument id and column id the unrounded cell. Instruments appear in the order they were passed. */
  public final Map<Integer, Map<Integer, IndicatorCell>> cells = new LinkedHashMap<>();

  /** Readable identity and currency of the benchmarks that were found, by instrument id of the benchmark. */
  public final Map<Integer, BenchmarkInfo> benchmarks = new HashMap<>();

  /** Per column the fundamental variables its expression reads; columns without any are absent. */
  public final Map<Integer, List<String>> columnFundamentalVariables = new HashMap<>();

  /** Per instrument and fundamental variable the origin of the bound value. */
  public final Map<Integer, Map<String, FundamentalInfo>> fundamentalInfo = new HashMap<>();

  /** Per column whether it is a technical or a fundamental indicator. */
  public final Map<Integer, IndicatorCategory> columnCategories = new HashMap<>();

  /** Per instrument id the date of its newest quote of the loaded series; absent without any quote. */
  public final Map<Integer, LocalDate> newestHistoryDate = new HashMap<>();

  /** True when a quote series was loaded at all, which is the case only if a column needs a series or a benchmark. */
  public boolean seriesLoaded;
}
