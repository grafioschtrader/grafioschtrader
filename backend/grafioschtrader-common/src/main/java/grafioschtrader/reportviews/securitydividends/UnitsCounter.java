package grafioschtrader.reportviews.securitydividends;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import grafioschtrader.entities.Security;

public class UnitsCounter {

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

  public List<UnitMutation> getMutations() {
    return mutations;
  }

}
