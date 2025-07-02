package grafioschtrader.reportviews.securitydividends;

import grafioschtrader.entities.Security;

public class UnitsCounter {

  /** The security for which units are being tracked. */
  public Security security;

  /** The current number of units held for this security. */
  public double units;

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

}
