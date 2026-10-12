package grafioschtrader.connector.securitysearch;

import java.util.Locale;
import java.util.Optional;

import grafioschtrader.dto.SecuritySearchCandidateDto;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.SecuritySearchStatus;
import grafioschtrader.types.SpecialInvestmentInstruments;

/**
 * A data provider that can look up instrument listings by ISIN, currency and MIC. The interface is deliberately
 * separate from {@code IFeedConnector}: not every price connector offers a search, and a metadata provider such as
 * OpenFIGI supplies no prices at all. Whether a provider is usable for searching is therefore decided here and not by
 * the activation of a price connector. What the search covers, however, is taken from the price connector of the same
 * data source when there is one ({@link #getFeedConnectorShortId()}).
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
   * Names the price connector of the same data source, e.g. {@code six} for {@code SixFeedConnector}. Its
   * {@code IFeedConnector.supports} then decides which exchanges and instrument types the search covers, so that the
   * coverage is declared in one place. A provider without such a connector, like OpenFIGI, declares its coverage with
   * {@link #covers(AssetclassType, SpecialInvestmentInstruments)}.
   *
   * @return the short id of the connector, or empty when the data source has no price connector
   */
  default Optional<String> getFeedConnectorShortId() {
    return Optional.empty();
  }

  /**
   * Tells whether the provider can find instruments of a type. Only asked when the provider names no price connector.
   *
   * @param categoryType asset class type chosen by the user
   * @param sii          financial instrument chosen by the user
   * @return true when a search for this type is worthwhile
   */
  default boolean covers(AssetclassType categoryType, SpecialInvestmentInstruments sii) {
    return true;
  }

  /**
   * Searches the provider for listings matching the query. Candidates contradicting the supplied ISIN, currency or MIC
   * are removed and only counted.
   *
   * @param query the search query
   * @return the result with its status; never null
   */
  SecuritySearchResult search(SecuritySearchQuery query);

  /**
   * Tells whether the provider can deliver master data for a candidate. Master data is always requested for a candidate
   * of the same provider, keyed by its provider instrument id or provider symbol.
   *
   * @param candidate a candidate returned by {@link #search(SecuritySearchQuery)}
   * @return true when {@link #getSecurityBaseData(SecuritySearchCandidateDto, Locale)} would ask the provider
   */
  default boolean supportsBaseData(SecuritySearchCandidateDto candidate) {
    return false;
  }

  /**
   * Fetches the master data of a candidate from the provider's detail endpoint. Like the search, this never throws; a
   * detail endpoint whose ISIN or currency contradicts the candidate results in {@link SecuritySearchStatus#NO_MATCH}.
   *
   * @param candidate a candidate returned by {@link #search(SecuritySearchQuery)} of this provider
   * @param locale    language of the user, used for links to pages offered in several languages
   * @return the result with its status; never null
   */
  default SecurityBaseDataResult getSecurityBaseData(SecuritySearchCandidateDto candidate, Locale locale) {
    return SecurityBaseDataResult.failure(getProviderId(), SecuritySearchStatus.UNSUPPORTED_QUERY,
        "Provider has no master data");
  }
}
