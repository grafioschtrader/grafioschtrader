package grafioschtrader.reports;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import grafioschtrader.dto.TotalReturnFactor;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitycurrency;
import grafioschtrader.repository.DividendJpaRepository;
import grafioschtrader.repository.DividendJpaRepository.DividendTotalReturn;
import grafioschtrader.types.DistributionFrequency;

/**
 * Total return adjustment of a split-adjusted close series (GitHub issue #269). Every comparison of instruments that
 * opts in to include dividends goes through this class, so that no caller computes its own variant.
 *
 * <p>
 * The rule is the one of total return indices (S&amp;P, MSCI, STOXX) and of fund performance by the BVI method: the
 * gross dividend is reinvested at the close of the ex-date. For each ex-date {@code e} with the split-adjusted dividend
 * {@code D} in the currency of the instrument and {@code P_e} the close of the ex-date, the factor is
 * {@code f_e = P_e / (P_e + D)}. The adjusted close of day {@code d} is {@code close_d} multiplied by every {@code f_e}
 * with {@code e > d}. Older prices are scaled down, so the latest adjusted price equals the real latest price as long as
 * no ex-date after the last price is loaded. Only the absolute level depends on that choice; percentage lines,
 * correlations and volatilities do not.
 * </p>
 *
 * <p>
 * Every return without an ex-date equals the price return, and the return of an ex-date is {@code (P_e + D) / P} with
 * {@code P} the previous close: exactly the holding return of an investor who owns the share over the ex-date. The
 * adjusted series therefore moves like a forward total return index {@code TR_t = TR_t-1 * (P_t + D_t) / P_t-1}. The
 * <i>Adj Close</i> of Yahoo uses {@code 1 - D / P} instead, which deviates by a second order term on every ex-date.
 * </p>
 *
 * <p>
 * The factors of an instrument are always built on its own complete close series, independently of any join or sampling
 * applied later, so a dividend whose ex-date falls on a dropped row still counts. Only instruments for which
 * {@link Security#isDividendCapableInstrument()} holds are adjusted; currency pairs, derived instruments and indices
 * keep their price series. Dividends are reinvested gross, which slightly favours a distributing fund over its
 * accumulating twin that reinvests after withholding tax.
 * </p>
 */
public final class TotalReturnAdjuster {

  private static final Logger log = LoggerFactory.getLogger(TotalReturnAdjuster.class);

  /**
   * A distributing instrument is reported as lacking dividend data when its evaluated period, extended back to at least
   * this many years, holds no ex-date. Without the extension an annual payer would be reported in every short period.
   */
  private static final int MIN_YEARS_FOR_MISSING_DIVIDENDS = 1;

  private static final TotalReturnAdjuster EMPTY = new TotalReturnAdjuster(Map.of(), Set.of());

  private final Map<Integer, TotalReturnFactors> factorsById;
  /** Adjusted instruments that should distribute according to their distribution frequency. */
  private final Set<Integer> idsExpectingDividends;

  private TotalReturnAdjuster(Map<Integer, TotalReturnFactors> factorsById, Set<Integer> idsExpectingDividends) {
    this.factorsById = factorsById;
    this.idsExpectingDividends = idsExpectingDividends;
  }

  /**
   * An adjuster that leaves every series unchanged, used when the caller does not include dividends.
   *
   * @return the shared identity adjuster
   */
  public static TotalReturnAdjuster none() {
    return EMPTY;
  }

  /**
   * Loads the dividends of all adjustable instruments of the list in one query and builds their factors.
   *
   * @param dividendJpaRepository repository providing the dividends together with the closes before the ex-dates
   * @param instruments           securities and currency pairs to compare; only dividend-capable securities are
   *                              adjusted
   * @param untilDate             last ex-date taken into account, normally the date of the youngest price of the
   *                              series; a later ex-date (a calendar entry) would shift the whole series
   * @return the adjuster, never null
   */
  public static TotalReturnAdjuster load(DividendJpaRepository dividendJpaRepository,
      Collection<? extends Securitycurrency<?>> instruments, LocalDate untilDate) {
    Map<Integer, Security> adjustable = new LinkedHashMap<>();
    for (Securitycurrency<?> sc : instruments) {
      if (sc instanceof Security security && security.getAssetClass() != null
          && security.isDividendCapableInstrument()) {
        adjustable.putIfAbsent(security.getIdSecuritycurrency(), security);
      }
    }
    if (adjustable.isEmpty()) {
      return EMPTY;
    }
    Map<Integer, List<DividendTotalReturn>> rowsById = dividendJpaRepository
        .getDividendsForTotalReturn(new ArrayList<>(adjustable.keySet()), untilDate).stream()
        .collect(Collectors.groupingBy(DividendTotalReturn::getIdSecuritycurrency));
    Map<Integer, TotalReturnFactors> factorsById = new LinkedHashMap<>();
    Set<Integer> idsExpectingDividends = new HashSet<>();
    adjustable.forEach((id, security) -> {
      factorsById.put(id, TotalReturnFactors.of(id, rowsById.getOrDefault(id, List.of())));
      if (security.getDistributionFrequency() != DistributionFrequency.DF_NONE) {
        idsExpectingDividends.add(id);
      }
    });
    return new TotalReturnAdjuster(factorsById, idsExpectingDividends);
  }

  /**
   * The factors of one instrument.
   *
   * @param idSecuritycurrency the instrument
   * @return its factors, or the identity when the instrument is not adjusted
   */
  public TotalReturnFactors getFactors(Integer idSecuritycurrency) {
    return factorsById.getOrDefault(idSecuritycurrency, TotalReturnFactors.NONE);
  }

  /**
   * Tells whether the series of the instrument is adjusted at all, i.e. whether it is a dividend-capable security.
   *
   * @param idSecuritycurrency the instrument
   * @return true when dividends are added to its prices
   */
  public boolean isAdjusted(Integer idSecuritycurrency) {
    return factorsById.containsKey(idSecuritycurrency);
  }

  /**
   * Multiplies every price column of an adjustable instrument by its factor of the row date. Columns of other
   * instruments, for example the currency pairs appended for a currency conversion, are left alone.
   *
   * @param dateCloseTree the closes per date, one column per entry of {@code columnIds}; changed in place
   * @param columnIds     the instrument of every column
   */
  public void adjustCloseColumns(NavigableMap<LocalDate, double[]> dateCloseTree, List<Integer> columnIds) {
    if (factorsById.isEmpty()) {
      return;
    }
    for (int col = 0; col < columnIds.size(); col++) {
      TotalReturnFactors factors = factorsById.get(columnIds.get(col));
      if (factors != null && !factors.isEmpty()) {
        for (Map.Entry<LocalDate, double[]> entry : dateCloseTree.entrySet()) {
          entry.getValue()[col] *= factors.factorForDate(entry.getKey());
        }
      }
    }
  }

  /**
   * Tells whether an instrument should distribute but has no usable dividend in the evaluated period. Its "total
   * return" then silently equals its price return, which the user has to be told about. The period is extended back to
   * at least one year, so that an annual payer is not reported for a short period.
   *
   * @param idSecuritycurrency the instrument
   * @param dateFrom           start of the evaluated period, null for the whole history
   * @param dateTo             end of the evaluated period, inclusive
   * @return true when dividend data is missing
   */
  public boolean isMissingDividends(Integer idSecuritycurrency, LocalDate dateFrom, LocalDate dateTo) {
    if (!idsExpectingDividends.contains(idSecuritycurrency)) {
      return false;
    }
    LocalDate minFrom = dateTo.minusYears(MIN_YEARS_FOR_MISSING_DIVIDENDS);
    LocalDate from = dateFrom == null || dateFrom.isAfter(minFrom) ? minFrom : dateFrom;
    return !getFactors(idSecuritycurrency).hasExDateBetween(from, dateTo);
  }

  /**
   * The instruments of the period for which {@link #isMissingDividends(Integer, LocalDate, LocalDate)} holds.
   *
   * @param dateFrom start of the evaluated period, null for the whole history
   * @param dateTo   end of the evaluated period, inclusive
   * @return ids of the instruments lacking dividend data, empty when nothing is missing
   */
  public List<Integer> getIdsMissingDividends(LocalDate dateFrom, LocalDate dateTo) {
    return idsExpectingDividends.stream().filter(id -> isMissingDividends(id, dateFrom, dateTo)).sorted().toList();
  }

  /**
   * The adjustment factors of a single instrument as a step function over the dates.
   */
  public static final class TotalReturnFactors {

    /** The identity: no dividend, every factor is 1. */
    public static final TotalReturnFactors NONE = new TotalReturnFactors(new TreeMap<>());

    /** Per ex-date the product of its own factor and those of every younger ex-date. */
    private final NavigableMap<LocalDate, Double> cumulativeByExDate;

    private TotalReturnFactors(NavigableMap<LocalDate, Double> cumulativeByExDate) {
      this.cumulativeByExDate = cumulativeByExDate;
    }

    /**
     * Builds the factors from the dividends of one instrument. A dividend whose ex-date precedes the first quote does
     * not belong to the series, and one without any price on or after its ex-date cannot be reinvested yet. A dividend
     * without an exchange rate, or one at least as high as the previous close or giving no positive factor (bad data),
     * is skipped and logged. Several dividends on the same ex-date are combined.
     *
     * @param idSecuritycurrency the instrument, for the log only
     * @param dividends          its dividends
     * @return the factors
     */
    static TotalReturnFactors of(Integer idSecuritycurrency, List<DividendTotalReturn> dividends) {
      TreeMap<LocalDate, Double> factorByExDate = new TreeMap<>();
      for (DividendTotalReturn dividend : dividends) {
        Double factor = factorOf(idSecuritycurrency, dividend);
        if (factor != null) {
          factorByExDate.merge(dividend.getExDate(), factor, (f1, f2) -> f1 * f2);
        }
      }
      TreeMap<LocalDate, Double> cumulativeByExDate = new TreeMap<>();
      double cumulative = 1.0;
      for (Map.Entry<LocalDate, Double> entry : factorByExDate.descendingMap().entrySet()) {
        cumulative *= entry.getValue();
        cumulativeByExDate.put(entry.getKey(), cumulative);
      }
      return new TotalReturnFactors(cumulativeByExDate);
    }

    private static Double factorOf(Integer idSecuritycurrency, DividendTotalReturn dividend) {
      if (dividend.getCloseBefore() == null || dividend.getCloseBefore() <= 0 || dividend.getCloseExDate() == null
          || dividend.getCloseExDate() <= 0) {
        return null;
      }
      if (dividend.getExchangeRate() == null) {
        log.warn(
            "Total return: dividend of security {} on {} in {} skipped, no exchange rate into the security currency",
            idSecuritycurrency, dividend.getExDate(), dividend.getCurrency());
        return null;
      }
      double amount = dividend.getAmountAdjusted() * dividend.getExchangeRate();
      double factor = dividend.getCloseExDate() / (dividend.getCloseExDate() + amount);
      if (amount >= dividend.getCloseBefore() || !(factor > 0) || !Double.isFinite(factor)) {
        log.warn("Total return: dividend of security {} on {} skipped, amount {} {} gives the factor {}",
            idSecuritycurrency, dividend.getExDate(), dividend.getAmountAdjusted(), dividend.getCurrency(), factor);
        return null;
      }
      return factor;
    }

    /**
     * The factor by which the close of a date is multiplied: the product of the factors of all ex-dates after it.
     *
     * @param date the date of the price
     * @return the factor, 1 when no ex-date follows
     */
    public double factorForDate(LocalDate date) {
      Map.Entry<LocalDate, Double> entry = cumulativeByExDate.higherEntry(date);
      return entry == null ? 1.0 : entry.getValue();
    }

    /**
     * The growth that the reinvested dividends add between two dates: the product of the factors of the ex-dates in
     * {@code (startExclusive, endInclusive]}. A total return change is {@code last / (closeStart * growth) - 1}.
     *
     * @param startExclusive date of the starting price
     * @param endInclusive   date of the final price
     * @return the product of the factors, 1 without an ex-date in between
     */
    public double growthBetween(LocalDate startExclusive, LocalDate endInclusive) {
      return factorForDate(startExclusive) / factorForDate(endInclusive);
    }

    /**
     * Tells whether a usable dividend has its ex-date in {@code (fromExclusive, toInclusive]}.
     *
     * @param fromExclusive start of the period
     * @param toInclusive   end of the period
     * @return true when at least one ex-date lies within
     */
    public boolean hasExDateBetween(LocalDate fromExclusive, LocalDate toInclusive) {
      return !fromExclusive.isAfter(toInclusive)
          && !cumulativeByExDate.subMap(fromExclusive, false, toInclusive, true).isEmpty();
    }

    /**
     * Tells whether the series is left unchanged.
     *
     * @return true without a usable dividend
     */
    public boolean isEmpty() {
      return cumulativeByExDate.isEmpty();
    }

    /**
     * The step function for the client, ordered by ex-date ascending.
     *
     * @return per ex-date the factor applied to the dates before it
     */
    public List<TotalReturnFactor> getCumulativeFactors() {
      return cumulativeByExDate.entrySet().stream()
          .map(entry -> new TotalReturnFactor(entry.getKey(), entry.getValue())).toList();
    }
  }
}
