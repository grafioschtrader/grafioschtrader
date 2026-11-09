package grafioschtrader.connector.securitysearch;

import java.util.List;

import grafioschtrader.dto.CurrencypairSearchHit;
import grafioschtrader.types.SecuritySearchStatus;

/**
 * Result of searching one provider for a currency pair.
 *
 * @param providerId id of the provider that was asked
 * @param status     outcome of the search; {@link SecuritySearchStatus#NO_MATCH} when the provider does not know the
 *                   pair
 * @param hits       the hits; empty unless the status is {@link SecuritySearchStatus#OK}
 * @param detail     short technical explanation of a failure or an empty result, may be null
 */
public record CurrencypairSearchResult(String providerId, SecuritySearchStatus status, List<CurrencypairSearchHit> hits,
    String detail) {

  public CurrencypairSearchResult {
    hits = hits == null ? List.of() : List.copyOf(hits);
  }

  /**
   * Creates the result of a search that delivered hits.
   *
   * @param providerId id of the provider
   * @param hits       the hits, not empty
   * @return the result with status {@link SecuritySearchStatus#OK}
   */
  public static CurrencypairSearchResult ok(String providerId, List<CurrencypairSearchHit> hits) {
    return new CurrencypairSearchResult(providerId, SecuritySearchStatus.OK, hits, null);
  }

  /**
   * Creates the result of a search that could not be carried out or found nothing.
   *
   * @param providerId id of the provider
   * @param status     the failure or empty status
   * @param detail     short explanation, may be null
   * @return the result without hits
   */
  public static CurrencypairSearchResult failure(String providerId, SecuritySearchStatus status, String detail) {
    return new CurrencypairSearchResult(providerId, status, List.of(), detail);
  }
}
