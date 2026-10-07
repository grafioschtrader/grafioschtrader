package grafioschtrader.types;

import java.time.LocalDate;

import grafioschtrader.common.DateBusinessHelper;

/**
 * Period a dashboard chart covers, counted back from today. The constant names are also the translation keys of the
 * options, which is why they carry a prefix rather than being bare abbreviations such as {@code M1}.
 */
public enum DashboardChartRange {
  CHART_RANGE_1M, CHART_RANGE_3M, CHART_RANGE_YTD, CHART_RANGE_1Y, CHART_RANGE_3Y, CHART_RANGE_5Y, CHART_RANGE_MAX;

  /**
   * The first day of the period.
   *
   * @param today the last day of the period, in the time zone of the client
   * @return the first day, inclusive; for {@link #CHART_RANGE_MAX} the oldest trading day GT knows of
   */
  public LocalDate fromDate(LocalDate today) {
    return switch (this) {
    case CHART_RANGE_1M -> today.minusMonths(1);
    case CHART_RANGE_3M -> today.minusMonths(3);
    case CHART_RANGE_YTD -> today.withDayOfYear(1);
    case CHART_RANGE_1Y -> today.minusYears(1);
    case CHART_RANGE_3Y -> today.minusYears(3);
    case CHART_RANGE_5Y -> today.minusYears(5);
    case CHART_RANGE_MAX -> DateBusinessHelper.getOldestTradingDayAsLocalDate();
    };
  }

  /**
   * Resolves a stored or transmitted name.
   *
   * @param name the constant name
   * @return the constant, or null when the name is none of them
   */
  public static DashboardChartRange fromName(String name) {
    for (DashboardChartRange range : values()) {
      if (range.name().equals(name)) {
        return range;
      }
    }
    return null;
  }
}
