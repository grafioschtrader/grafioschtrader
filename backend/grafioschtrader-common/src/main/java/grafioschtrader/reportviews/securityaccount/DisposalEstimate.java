package grafioschtrader.reportviews.securityaccount;

import java.util.List;

/**
 * Estimated cost of selling a position at the report date, in the currency of the security. Every component is the sum
 * of what could be priced over the security accounts holding the position; it is null when nothing could be priced. A
 * component is complete only when every security account contributed a priced amount, because an unknown cost is never
 * taken as zero.
 *
 * @param commission         commission of the fee model
 * @param commissionComplete true when the commission is known for every security account
 * @param tax                transaction tax of the simulation tax model
 * @param taxComplete        true when the tax is known for every security account
 * @param fxCost             currency conversion markup into the currency of the settlement cash account
 * @param fxComplete         true when the markup is known for every security account
 * @param sameCurrencyNet    net proceeds of the units that settle into a cash account in the currency of the security;
 *                           the portfolio report converts them into the main currency together with that cash balance
 * @param details            the matched rules and the reasons of the unknown components
 */
public record DisposalEstimate(Double commission, boolean commissionComplete, Double tax, boolean taxComplete,
    Double fxCost, boolean fxComplete, double sameCurrencyNet, List<DisposalCostDetail> details) {

  public boolean complete() {
    return commissionComplete && taxComplete && fxComplete;
  }

  /** @return the sum of the known components, 0 when none is known */
  public double knownTotal() {
    return nz(commission) + nz(tax) + nz(fxCost);
  }

  private static double nz(Double value) {
    return value == null ? 0.0 : value;
  }
}
