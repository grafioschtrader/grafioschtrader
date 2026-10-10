package grafioschtrader.connector.securitysearch;

/**
 * A data provider that can look up instrument listings by ISIN, currency and MIC. The interface is deliberately
 * separate from {@code IFeedConnector}: not every price connector offers a search, and a metadata provider such as
 * OpenFIGI supplies no prices at all. Whether a provider is usable for searching is therefore decided here and not by
 * the activation of a price connector.
 *
 * <p>
 * A search never writes anything and never throws. Every failure - missing credentials, rate limits, timeouts or a
 * provider error - is reported as a {@link grafioschtrader.types.SecuritySearchStatus}, so that a caller merging
 * several providers keeps the results of the others.
 * </p>
 */
public interface ISecuritySearchProvider {

  /**
   * Short id of the provider, used as {@code providerId} of its candidates and for selecting providers.
   *
   * @return the id, e.g. {@code yahoo}, {@code openfigi}, {@code six} or {@code xetra}
   */
  String getProviderId();

  /**
   * Tells whether the provider can answer the query at all, for example because it covers only one exchange or needs an
   * ISIN.
   *
   * @param query the search query
   * @return true when {@link #search(SecuritySearchQuery)} would ask the provider
   */
  boolean supports(SecuritySearchQuery query);

  /**
   * Searches the provider for listings matching the query. Candidates contradicting the supplied ISIN, currency or MIC
   * are removed and only counted.
   *
   * @param query the search query
   * @return the result with its status; never null
   */
  SecuritySearchResult search(SecuritySearchQuery query);
}
