package grafioschtrader.fundamental;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import grafioschtrader.entities.SecurityFundamental;
import grafioschtrader.types.FundamentalMetric;
import grafioschtrader.types.FundamentalSource;
import grafioschtrader.types.FundamentalSourceCheck;
import grafioschtrader.types.FundamentalState;

/**
 * Grades the stored fundamental rows of one security, so that per metric at most one row binds an expression variable.
 * It is a pure function of the rows and a date: it reads no database and no price, which is why the checks that need
 * the price ran earlier, at load time, and arrive here as {@code sourceCheck}.
 *
 * <p>
 * Per bound metric, rows that failed their source check or are stale are not candidates. Yahoo share counts and revenue
 * per share additionally require a passed share-basis check. Candidates are compared only with candidates of a
 * comparable period; the most preferred candidate that agrees with another becomes {@code CONFIRMED}, a comparable
 * group without any agreement becomes {@code SUSPECT}, and otherwise the most preferred candidate of the newest period
 * becomes {@code CHECKED} or {@code SINGLE}. Finally the chosen fiscal year earnings are held against the chosen
 * trailing twelve months earnings when three of their quarters overlap.
 * </p>
 */
public final class FundamentalSelector {

  /** Metrics that bind an expression variable; the check metrics never do. */
  public static final List<FundamentalMetric> BOUND_METRICS = List.of(FundamentalMetric.EPS_TTM,
      FundamentalMetric.EPS_FY, FundamentalMetric.BVPS, FundamentalMetric.SPS_TTM, FundamentalMetric.SPS_FY,
      FundamentalMetric.SHARES, FundamentalMetric.NET_DEBT);

  /** States of a row that binds its variable. */
  public static final Set<FundamentalState> SELECTED_STATES = Set.of(FundamentalState.CONFIRMED,
      FundamentalState.CHECKED, FundamentalState.SINGLE);

  /** A dated value counts while its period ended no longer ago than this. */
  public static final int DATED_FRESH_MONTHS = 18;
  /** An undated value counts while it was fetched no longer ago than this; the weekly run may fail twice. */
  public static final int UNDATED_FRESH_DAYS = 21;
  /** Two dated candidates further apart describe different periods and are not compared. */
  static final int COMPARABLE_DAYS = 45;
  /** Fiscal year and trailing twelve months at most this far apart share three of their four quarters. */
  static final int FY_TTM_OVERLAP_DAYS = 92;
  static final double FY_TTM_TOLERANCE = 0.5;

  private static final Map<FundamentalMetric, List<FundamentalSource>> PREFERENCE = new EnumMap<>(
      Map.of(FundamentalMetric.EPS_TTM, List.of(FundamentalSource.FINNHUB, FundamentalSource.YAHOO),
          FundamentalMetric.EPS_FY, List.of(FundamentalSource.FINNHUB, FundamentalSource.EULERPOOL),
          FundamentalMetric.SPS_TTM, List.of(FundamentalSource.YAHOO, FundamentalSource.FINNHUB),
          FundamentalMetric.SPS_FY, List.of(FundamentalSource.EULERPOOL), FundamentalMetric.SHARES,
          List.of(FundamentalSource.YAHOO, FundamentalSource.EULERPOOL), FundamentalMetric.BVPS,
          List.of(FundamentalSource.YAHOO, FundamentalSource.FINNHUB, FundamentalSource.EULERPOOL),
          FundamentalMetric.NET_DEBT, List.of(FundamentalSource.YAHOO, FundamentalSource.EULERPOOL)));

  private FundamentalSelector() {
  }

  /**
   * Sets the state of every row and returns the rows that bind their variable.
   *
   * @param rows     all stored rows of one security, of every source; their state is overwritten
   * @param asOfDate the day the freshness is judged against
   * @return per bound metric the selected row; a metric without a selected row is absent
   */
  public static Map<FundamentalMetric, SecurityFundamental> select(List<SecurityFundamental> rows, LocalDate asOfDate) {
    rows.forEach(r -> r.setState(FundamentalState.CANDIDATE));
    Map<FundamentalMetric, List<SecurityFundamental>> candidates = new EnumMap<>(FundamentalMetric.class);
    for (FundamentalMetric metric : BOUND_METRICS) {
      List<SecurityFundamental> list = new ArrayList<>(
          rows.stream().filter(r -> r.getMetric() == metric && isCandidate(r, asOfDate)).toList());
      list.sort(Comparator.comparingInt(r -> PREFERENCE.get(metric).indexOf(r.getSource())));
      candidates.put(metric, list);
      markContradictedGroups(metric, list);
    }
    Map<FundamentalMetric, SecurityFundamental> selected = new EnumMap<>(FundamentalMetric.class);
    for (FundamentalMetric metric : BOUND_METRICS) {
      choose(metric, candidates.get(metric), selected);
    }
    while (checkFiscalYearAgainstTrailing(selected)) {
      choose(FundamentalMetric.EPS_TTM, candidates.get(FundamentalMetric.EPS_TTM), selected);
      choose(FundamentalMetric.EPS_FY, candidates.get(FundamentalMetric.EPS_FY), selected);
    }
    return selected;
  }

  /**
   * True when a row is fresh, its check did not fail and its source is preferred for the metric. Yahoo's current share
   * count and the revenue divided by it need a passed market capitalisation check to establish the share basis.
   */
  static boolean isCandidate(SecurityFundamental row, LocalDate asOfDate) {
    return row.getSourceCheck() != FundamentalSourceCheck.FAILED && isFresh(row, asOfDate)
        && (row.getSource() != FundamentalSource.YAHOO
            || row.getMetric() != FundamentalMetric.SHARES && row.getMetric() != FundamentalMetric.SPS_TTM
            || row.getSourceCheck() == FundamentalSourceCheck.PASSED)
        && PREFERENCE.get(row.getMetric()).contains(row.getSource());
  }

  /** The freshness rule, applied again when the rows are read for the watchlist. */
  public static boolean isFresh(SecurityFundamental row, LocalDate asOfDate) {
    return row.getPeriodEnd() != null ? !row.getPeriodEnd().isBefore(asOfDate.minusMonths(DATED_FRESH_MONTHS))
        : row.getFetchedTime() != null
            && !row.getFetchedTime().toLocalDate().isBefore(asOfDate.minusDays(UNDATED_FRESH_DAYS));
  }

  /**
   * Makes every candidate {@code SUSPECT} that belongs to a group of comparable candidates of which no two agree. A
   * group is a connected set under comparability; metrics that are not compared form no groups.
   */
  private static void markContradictedGroups(FundamentalMetric metric, List<SecurityFundamental> candidates) {
    if (tolerance(metric) == null) {
      return;
    }
    List<SecurityFundamental> unassigned = new ArrayList<>(candidates);
    while (!unassigned.isEmpty()) {
      List<SecurityFundamental> group = new ArrayList<>(List.of(unassigned.removeFirst()));
      for (int i = 0; i < group.size(); i++) {
        SecurityFundamental member = group.get(i);
        List<SecurityFundamental> joined = unassigned.stream().filter(c -> comparable(member, c)).toList();
        group.addAll(joined);
        unassigned.removeAll(joined);
      }
      if (group.size() >= 2 && group.stream().noneMatch(a -> hasAgreeingPartner(metric, a, group))) {
        group.forEach(r -> r.setState(FundamentalState.SUSPECT));
      }
    }
  }

  /**
   * Chooses the row of one metric among its candidates that are not {@code SUSPECT}, replacing an earlier choice.
   */
  private static void choose(FundamentalMetric metric, List<SecurityFundamental> candidates,
      Map<FundamentalMetric, SecurityFundamental> selected) {
    SecurityFundamental previous = selected.remove(metric);
    if (previous != null && previous.getState() != FundamentalState.SUSPECT) {
      previous.setState(FundamentalState.CANDIDATE);
    }
    List<SecurityFundamental> open = candidates.stream().filter(c -> c.getState() != FundamentalState.SUSPECT).toList();
    for (SecurityFundamental candidate : open) {
      if (tolerance(metric) != null && hasAgreeingPartner(metric, candidate, open)) {
        candidate.setState(FundamentalState.CONFIRMED);
        selected.put(metric, candidate);
        return;
      }
    }
    // Shares describe the current capital structure, not a reporting period. A fresh undated count can stand
    // alone when it reproduces market capitalisation; undated earnings still require another source.
    if (metric == FundamentalMetric.SHARES) {
      var checked = open.stream()
          .filter(c -> c.getPeriodEnd() == null && c.getSourceCheck() == FundamentalSourceCheck.PASSED).findFirst();
      if (checked.isPresent()) {
        checked.get().setState(FundamentalState.CHECKED);
        selected.put(metric, checked.get());
        return;
      }
    }
    LocalDate newest = open.stream().map(SecurityFundamental::getPeriodEnd).filter(d -> d != null)
        .max(LocalDate::compareTo).orElse(null);
    if (newest == null) {
      return;
    }
    open.stream().filter(c -> c.getPeriodEnd() != null && daysApart(c.getPeriodEnd(), newest) <= COMPARABLE_DAYS)
        .findFirst().ifPresent(c -> {
          c.setState(
              c.getSourceCheck() == FundamentalSourceCheck.PASSED ? FundamentalState.CHECKED : FundamentalState.SINGLE);
          selected.put(metric, c);
        });
  }

  /**
   * When the chosen trailing twelve months end on or after the chosen fiscal year and at most a quarter later, three of
   * their four quarters are the same and the two values must not differ by more than half of the larger one. A
   * contradiction makes the fiscal year row {@code SUSPECT} unless it is confirmed, and the trailing row unless it is
   * confirmed or checked. Values in different currencies are not compared.
   *
   * @return true when a chosen row became {@code SUSPECT}, so the metric has to be chosen again
   */
  private static boolean checkFiscalYearAgainstTrailing(Map<FundamentalMetric, SecurityFundamental> selected) {
    SecurityFundamental ttm = selected.get(FundamentalMetric.EPS_TTM);
    SecurityFundamental fy = selected.get(FundamentalMetric.EPS_FY);
    if (ttm == null || fy == null || !Objects.equals(ttm.getCurrency(), fy.getCurrency()) || ttm.getPeriodEnd() == null
        || fy.getPeriodEnd() == null || ttm.getPeriodEnd().isBefore(fy.getPeriodEnd())
        || daysApart(ttm.getPeriodEnd(), fy.getPeriodEnd()) > FY_TTM_OVERLAP_DAYS) {
      return false;
    }
    double a = fy.getValue();
    double b = ttm.getValue();
    if (Math.abs(a - b) <= FY_TTM_TOLERANCE * Math.max(Math.abs(a), Math.abs(b))) {
      return false;
    }
    boolean changed = false;
    if (fy.getState() != FundamentalState.CONFIRMED) {
      fy.setState(FundamentalState.SUSPECT);
      changed = true;
    }
    if (ttm.getState() != FundamentalState.CONFIRMED && ttm.getState() != FundamentalState.CHECKED) {
      ttm.setState(FundamentalState.SUSPECT);
      changed = true;
    }
    return changed;
  }

  private static boolean hasAgreeingPartner(FundamentalMetric metric, SecurityFundamental candidate,
      List<SecurityFundamental> others) {
    return others.stream().anyMatch(o -> o != candidate && comparable(candidate, o) && agree(metric, candidate, o));
  }

  /**
   * Two values are compared when they are in the same currency and their periods end at most 45 days apart or one of
   * them is undated.
   */
  static boolean comparable(SecurityFundamental a, SecurityFundamental b) {
    // Values in different currencies describe the same thing only after a conversion, which this pure function cannot
    // do; comparing them raw would make BioNTech's -4.70 USD contradict its -5.14 EUR.
    return Objects.equals(a.getCurrency(), b.getCurrency()) && (a.getPeriodEnd() == null || b.getPeriodEnd() == null
        || daysApart(a.getPeriodEnd(), b.getPeriodEnd()) <= COMPARABLE_DAYS);
  }

  static boolean agree(FundamentalMetric metric, SecurityFundamental a, SecurityFundamental b) {
    double x = a.getValue();
    double y = b.getValue();
    double larger = Math.max(Math.abs(x), Math.abs(y));
    return switch (metric) {
    case EPS_TTM, EPS_FY -> Math.abs(x - y) <= Math.max(0.05 * larger, 0.02);
    default -> Math.abs(x - y) <= tolerance(metric) * larger;
    };
  }

  /** Relative tolerance of the agreement, null for a metric whose sources report it at different dates. */
  private static Double tolerance(FundamentalMetric metric) {
    return switch (metric) {
    case EPS_TTM, EPS_FY -> 0.05;
    case SPS_TTM -> 0.10;
    case SHARES -> 0.05;
    default -> null;
    };
  }

  private static long daysApart(LocalDate a, LocalDate b) {
    return Math.abs(ChronoUnit.DAYS.between(a, b));
  }

}
