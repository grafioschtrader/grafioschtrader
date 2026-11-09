package grafioschtrader.connector.securitysearch;

/**
 * A data provider that can be searched for a currency pair. A currency pair has neither ISIN nor exchange, so this
 * search is separate from {@link ISecuritySearchProvider}; it only finds the identifiers a connector needs for its URL
 * extension when they cannot be built from the two currencies, such as the onvista notation or the Boursorama symbol.
 *
 * <p>
 * Like the security search, a currency pair search never throws: every failure is reported through the status of the
 * result.
 * </p>
 */
public interface ICurrencypairSearchProvider {

  /**
   * Short id of the provider, the same as of its security search.
   *
   * @return e.g. {@code onvista}
   */
  String getProviderId();

  /**
   * Tells whether the provider quotes pairs of this kind at all; a provider that does not is not asked.
   *
   * @param cryptocurrency true when one of the two currencies is a cryptocurrency
   * @return true when the provider is worth asking
   */
  boolean coversCurrencypair(boolean cryptocurrency);

  /**
   * Searches the pair in the given direction; a provider that quotes only the inverse pair delivers no hit.
   *
   * @param fromCurrency base currency, e.g. EUR
   * @param toCurrency   quote currency, e.g. CAD
   * @return the outcome with the hits, never null
   */
  CurrencypairSearchResult searchCurrencypair(String fromCurrency, String toCurrency);
}
