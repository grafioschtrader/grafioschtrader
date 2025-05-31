package grafioschtrader.entities.projection;

/**
 * Projection interface for derived-security price formulas.
 * <p>
 * Provides mapping from a link securitycurrency to its formula for price calculation, with an indicator whether the
 * formula is applied to calculate the price.
 */
public interface IFormulaInSecurity {

  /**
   * The identifier of the linked (parent) securitycurrency from which this formula is derived.
   *
   * @return the link securitycurrency ID
   */
  Integer getIdLinkSecuritycurrency();

  /**
   * The price formula expression to be applied for this derived security.
   *
   * @return the formula string used for price calculation
   */
  String getFormulaPrices();

  /**
   * The identifier of the derived securitycurrency to which the formula applies.
   *
   * @return the target securitycurrency ID
   */
  Integer getIdSecuritycurrency();

  /**
   * Indicates whether the formula should be used to calculate the security’s price.
   *
   * @return true if the price is calculated via the formula; false otherwise
   */
  boolean isCalculatedPrice();
}
