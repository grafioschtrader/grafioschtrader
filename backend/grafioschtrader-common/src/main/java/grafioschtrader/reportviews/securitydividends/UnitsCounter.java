package grafioschtrader.reportviews.securitydividends;

import grafioschtrader.entities.Security;

public class UnitsCounter {

  public Security security;
  public double units;

  public UnitsCounter(Security security, double units) {
    super();
    this.security = security;
    this.units = units;
  }

  public void addUnits(Double unitsToAdd) {
    this.units += unitsToAdd;

  }

}
