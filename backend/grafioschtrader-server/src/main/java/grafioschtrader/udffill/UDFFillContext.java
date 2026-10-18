package grafioschtrader.udffill;

import grafioschtrader.entities.Security;
import grafioschtrader.entities.Stockexchange;

/**
 * Everything a fill rule may read about one security, collected before the evaluation, so that
 * {@link UDFFillTemplate#evaluate(UDFFillContext, grafioschtrader.entities.UDFMetadataSecurity)} performs no I/O.
 *
 * @param security      the security whose field is computed
 * @param stockexchange its stock exchange; may be null, and its MIC may be null
 * @param yahooSymbol   the persisted value of the user-0 field {@code UDF_SPEC_INTERNAL_YAHOO_SYMBOL_HIDE} for this
 *                      security, or null when it has not been stored yet
 */
public record UDFFillContext(Security security, Stockexchange stockexchange, String yahooSymbol) {

  /**
   * Creates the context of a security, taking the stock exchange from the security itself.
   *
   * @param security    the security whose field is computed
   * @param yahooSymbol the persisted Yahoo symbol of the security, or null
   * @return the context for the evaluation
   */
  public static UDFFillContext of(Security security, String yahooSymbol) {
    return new UDFFillContext(security, security.getStockexchange(), yahooSymbol);
  }

  /**
   * Returns the MIC of the stock exchange of the security.
   *
   * @return the MIC, or null when the security has no stock exchange or the exchange has no MIC
   */
  public String mic() {
    return stockexchange == null ? null : stockexchange.getMic();
  }
}
