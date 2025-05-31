package grafioschtrader.entities.projection;

/**
 * Projection interface for retrieving security or currency-pair information used in task notification contexts.
 * <p>
 * Provides the entity identifier and a human-readable tooltip for display.
 */
public interface IdSecurityCurrencyPairInfo {

  /**
   * The identifier of the security or currencypair entity.
   *
   * @return the security or currencypair ID
   */
  Integer getIdSecuritycurrency();

  /**
   * A descriptive tooltip for the entity, formatted for UI display.
   * <p>
   * For securities, this may include the name, ticker symbol, and ISIN. For currency pairs, this is formatted as
   * "FROM/TO" (e.g., "EUR/USD").
   *
   * @return the display tooltip string
   */
  String getTooltip();
}