package grafioschtrader.instrument;

import java.util.Map;

import grafioschtrader.types.CostBasisMethod;

/**
 * The cost basis methods in effect for one report run. A report resolves it once and hands it on unchanged, also
 * through a loop over security accounts, so that every sale of the run is calculated by the same rules.
 *
 * @param tenantMethod        the method of the tenant, used by every security account that is not overridden
 * @param byIdSecurityaccount the methods of the security accounts whose portfolio overrides the tenant, normally empty
 */
public record CostBasisMethods(CostBasisMethod tenantMethod, Map<Integer, CostBasisMethod> byIdSecurityaccount) {

  /** Every security account calculates by average cost. */
  public static final CostBasisMethods AVERAGE_ONLY = new CostBasisMethods(CostBasisMethod.AVERAGE_COST, Map.of());

  /**
   * One method for every security account, whatever tenant and portfolios have chosen.
   *
   * @param method the method to calculate by
   * @return the methods of a report run that ignores the setting
   */
  public static CostBasisMethods all(CostBasisMethod method) {
    return new CostBasisMethods(method, Map.of());
  }

  /**
   * The method of a security account. Only to be called with the security account of a booked transaction: the
   * transaction of a hypothetical sale carries none, and an immutable map rejects a null key.
   *
   * @param idSecurityaccount the security account of a booked transaction
   * @return its method
   */
  public CostBasisMethod forAccount(Integer idSecurityaccount) {
    return byIdSecurityaccount.getOrDefault(idSecurityaccount, tenantMethod);
  }
}
