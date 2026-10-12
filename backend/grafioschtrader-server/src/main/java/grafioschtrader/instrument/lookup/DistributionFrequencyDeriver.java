package grafioschtrader.instrument.lookup;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import grafioschtrader.types.DistributionFrequency;

/**
 * Derives the distribution frequency of an instrument from the ex-dates its dividend connector delivers, the same way a
 * user counts the distributions on DivvyDiary or Yahoo.
 *
 * <p>
 * The distributions of the last {@value #FULL_YEARS} full calendar years are counted per year. When the first
 * distribution in the fetched history falls into one of these years, the instrument probably started distributing then,
 * so only the years from that one on count, and that first, usually incomplete year is dropped as long as a later full
 * year remains. The median of the yearly counts - robust against a single special dividend or an ex-date moving across
 * the turn of the year - is mapped to the nearest regular frequency when it deviates by less than
 * {@value #TOLERANCE_PERCENT}%, otherwise to {@link DistributionFrequency#DF_AD_HOC}.
 * </p>
 *
 * <p>
 * No distribution at all in the whole period means {@link DistributionFrequency#DF_NONE}, e.g. an accumulating fund,
 * unless the listing is younger than a year. Less than one full year of distributions leaves the frequency open, as
 * does a single counted year in which the listing itself started.
 * </p>
 */
public final class DistributionFrequencyDeriver {

  /** Number of full calendar years that are counted. */
  static final int FULL_YEARS = 3;

  /** Number of years the dividend history must cover, so that the start of the distributions can be recognised. */
  static final int HISTORY_YEARS = 5;

  static final int TOLERANCE_PERCENT = 25;

  private static final Map<Integer, DistributionFrequency> REGULAR = Map.of(1, DistributionFrequency.DF_YEAR, 2,
      DistributionFrequency.DF_SEMI_ANNUAL, 4, DistributionFrequency.DF_QUARTERLY, 12,
      DistributionFrequency.DF_MONTHLY);

  private DistributionFrequencyDeriver() {
  }

  /**
   * First day of the dividend history a connector must deliver for {@link #derive}.
   *
   * @param today the reference day
   * @return 1 January {@value #HISTORY_YEARS} years before the current year
   */
  public static LocalDate historyStart(LocalDate today) {
    return LocalDate.of(today.getYear() - HISTORY_YEARS, 1, 1);
  }

  /**
   * Determines the frequency proposed for one dividend connector. A frequency the provider states itself, such as
   * DivvyDiary's {@code dividendFrequency}, takes precedence over the derivation from the ex-dates. "No distribution"
   * is never proposed for a fund that the master data marks as distributing, nor for a listing younger than a year: a
   * newly listed distributing fund simply has no dividends yet, so the frequency stays open.
   *
   * @param providerFrequency frequency stated by the provider, null when none
   * @param exDates           ex-dates delivered from {@link #historyStart(LocalDate)} on
   * @param listedSince       first trading day of the listing, null when unknown
   * @param distributing      true for a distributing fund, false for an accumulating one, null when unknown
   * @param today             the reference day
   * @return the frequency, or null when it stays open
   */
  public static DistributionFrequency combine(DistributionFrequency providerFrequency, Collection<LocalDate> exDates,
      LocalDate listedSince, Boolean distributing, LocalDate today) {
    DistributionFrequency frequency = providerFrequency != null ? providerFrequency
        : derive(exDates, listedSince, today);
    boolean noneImpossible = Boolean.TRUE.equals(distributing) || isYoungListing(listedSince, today);
    return frequency == DistributionFrequency.DF_NONE && noneImpossible ? null : frequency;
  }

  /**
   * Derives the frequency.
   *
   * @param exDates     ex-dates delivered from {@link #historyStart(LocalDate)} on; may contain older dates
   * @param listedSince first trading day of the listing, null when unknown
   * @param today       the reference day
   * @return the frequency, or null when the history is too short to tell
   */
  public static DistributionFrequency derive(Collection<LocalDate> exDates, LocalDate listedSince, LocalDate today) {
    int currentYear = today.getYear();
    int firstCountedYear = currentYear - FULL_YEARS;
    Map<Integer, Long> countByYear = exDates.stream()
        .filter(d -> !d.isBefore(historyStart(today)) && d.getYear() < currentYear)
        .collect(Collectors.groupingBy(LocalDate::getYear, Collectors.counting()));
    if (countByYear.isEmpty()) {
      boolean distributesThisYear = exDates.stream().anyMatch(d -> d.getYear() == currentYear);
      return distributesThisYear || isYoungListing(listedSince, today) ? null : DistributionFrequency.DF_NONE;
    }
    int firstDividendYear = countByYear.keySet().stream().min(Integer::compare).get();
    int fromYear = Math.max(firstCountedYear, firstDividendYear);
    if (firstDividendYear > currentYear - HISTORY_YEARS && firstDividendYear >= firstCountedYear
        && fromYear < currentYear - 1) {
      fromYear++;
    }
    boolean onlyStartYear = fromYear == firstDividendYear && fromYear == currentYear - 1;
    if (fromYear > currentYear - 1
        || onlyStartYear && listedSince != null && listedSince.getYear() >= firstDividendYear) {
      return null;
    }
    List<Long> counts = IntStream.range(fromYear, currentYear).mapToObj(y -> countByYear.getOrDefault(y, 0L)).sorted()
        .toList();
    return toFrequency(median(counts));
  }

  private static boolean isYoungListing(LocalDate listedSince, LocalDate today) {
    return listedSince != null && listedSince.isAfter(today.minusYears(1));
  }

  private static double median(List<Long> sortedCounts) {
    int n = sortedCounts.size();
    return n % 2 == 1 ? sortedCounts.get(n / 2) : (sortedCounts.get(n / 2 - 1) + sortedCounts.get(n / 2)) / 2.0;
  }

  private static DistributionFrequency toFrequency(double perYear) {
    if (perYear == 0) {
      return DistributionFrequency.DF_AD_HOC;
    }
    int nearest = REGULAR.keySet().stream()
        .min((a, b) -> Double.compare(Math.abs(perYear / a - 1), Math.abs(perYear / b - 1))).get();
    return Math.abs(perYear / nearest - 1) * 100 < TOLERANCE_PERCENT ? REGULAR.get(nearest)
        : DistributionFrequency.DF_AD_HOC;
  }
}
