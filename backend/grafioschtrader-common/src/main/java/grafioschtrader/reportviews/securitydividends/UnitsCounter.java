package grafioschtrader.reportviews.securitydividends;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import grafioschtrader.entities.Security;

public class UnitsCounter {

  /** Tolerance for treating a floating-point unit count as zero. */
  private static final double EPSILON = 1e-8;

  /** Per-transaction delta: date and signed unit change (+buy, -sell). */
  public record UnitMutation(LocalDate date, double delta) {}

  /** The security for which units are being tracked. */
  public Security security;

  /** The current number of units held for this security. */
  public double units;

  /** Timeline of cumulative units after each transaction, keyed by transaction date. */
  private TreeMap<LocalDate, Double> unitTimeline = new TreeMap<>();

  /** List of individual buy/sell mutations with their signed deltas. */
  private List<UnitMutation> mutations = new ArrayList<>();

  /**
   * Constructs a new UnitsCounter for the specified security with an initial unit count.
   *
   * @param security the security for which to track units; must not be null
   * @param units    the initial number of units to assign to this security
   */
  public UnitsCounter(Security security, double units) {
    this.security = security;
    this.units = units;
  }

  /**
   * Adds the specified number of units to the current unit count. This method allows for accumulating units across
   * multiple transactions or operations without creating new instances.
   *
   * <p>
   * The method handles null values gracefully by treating them as zero, ensuring that null addition operations don't
   * cause exceptions.
   * </p>
   *
   * @param unitsToAdd the number of units to add to the current count; if null, no units are added (treated as 0)
   */
  public void addUnits(Double unitsToAdd) {
    this.units += unitsToAdd;
  }

  /** Records the current unit count at the given date. Call after addUnits(). */
  public void recordUnitsAtDate(LocalDate date) {
    unitTimeline.put(date, units);
  }

  /** Returns units held at or before the given date, or 0 if no entry before that date. */
  public double getUnitsAtDate(LocalDate date) {
    Map.Entry<LocalDate, Double> entry = unitTimeline.floorEntry(date);
    return entry != null ? entry.getValue() : 0.0;
  }

  /** Records a buy/sell mutation delta at the given date. */
  public void recordMutation(LocalDate date, double delta) {
    mutations.add(new UnitMutation(date, delta));
  }

  /**
   * Units held going into maturity for a bond redeemed at or after maturity. Returns the cumulative units held
   * immediately before the first reducing (sell/redeem) mutation dated on or after the given maturity date, or 0.0 if no
   * reducing mutation occurs on/after that date (i.e. the position was not redeemed at maturity).
   *
   * <p>
   * Bonds do not split, so the running sum of mutations equals the recorded unit timeline; no split-basis
   * reconciliation is needed here.
   * </p>
   *
   * @param maturityDate the bond's maturity (activeToDate)
   * @return units held just before the maturity redemption, or 0.0 if there is none
   */
  public double getUnitsBeforeRedemptionOnOrAfter(LocalDate maturityDate) {
    double running = 0.0;
    for (UnitMutation m : mutations) {
      if (m.delta() < 0 && !m.date().isBefore(maturityDate)) {
        return running; // units held immediately before the maturity redemption
      }
      running += m.delta();
    }
    return 0.0;
  }

  /**
   * Determines whether the position was opened (or re-opened) in the given year, i.e. the holding crossed from 0 units
   * to a positive amount. This is the case when no units were held at the end of the previous year and at least one
   * buying mutation (positive delta) occurred during the year. A short entry (first mutation negative) does not count
   * as an opening.
   *
   * @param year the calendar year to check
   * @return true when the holding went from 0 to a positive amount in that year
   */
  public boolean wasOpenedInYear(int year) {
    Map.Entry<LocalDate, Double> prevYearEnd = unitTimeline.floorEntry(LocalDate.of(year - 1, 12, 31));
    if (prevYearEnd != null && Math.abs(prevYearEnd.getValue()) > EPSILON) {
      return false;
    }
    return mutations.stream().anyMatch(m -> m.delta() > 0 && m.date().getYear() == year);
  }

  /**
   * Determines whether the holding was 0 units both at the beginning (end of previous year) and at the end of the
   * given year — a position opened and fully closed within the year, or a row present only because of a trailing
   * dividend after a complete sale in an earlier year.
   *
   * @param year the calendar year to check
   * @return true when no units were held at the start and at the end of that year
   */
  public boolean wasZeroAtStartAndEndOfYear(int year) {
    Map.Entry<LocalDate, Double> prevYearEnd = unitTimeline.floorEntry(LocalDate.of(year - 1, 12, 31));
    boolean zeroAtStart = prevYearEnd == null || Math.abs(prevYearEnd.getValue()) < EPSILON;
    return zeroAtStart && Math.abs(units) < EPSILON;
  }

  /**
   * Date of the maturity redemption: the first reducing (sell/redeem) mutation dated on or after the given maturity
   * date, or null when the position was not redeemed at/after maturity.
   *
   * @param maturityDate the bond's maturity (activeToDate)
   * @return the redemption date or null
   */
  public LocalDate getMaturityRedemptionDate(LocalDate maturityDate) {
    for (UnitMutation m : mutations) {
      if (m.delta() < 0 && !m.date().isBefore(maturityDate)) {
        return m.date();
      }
    }
    return null;
  }

  public List<UnitMutation> getMutations() {
    return mutations;
  }

}
