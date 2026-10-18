package grafioschtrader.instrument.lookup;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import grafioschtrader.connector.securitysearch.InstrumentType;
import grafioschtrader.connector.securitysearch.SecurityBaseDataResult;
import grafioschtrader.dto.SecurityBaseDataDto;
import grafioschtrader.dto.SecuritySearchCandidateDto;

/**
 * One listing found by the lookup, before or after it was merged with the same listing of another provider.
 *
 * @param candidate        search candidate of the primary provider
 * @param baseDataResult   outcome of the primary provider's master data request
 * @param baseData         master data; falls back to the search values when the request failed
 * @param instrumentType   instrument type derived from the raw types of all merged providers
 * @param providerIds      every provider that contributed, the primary one first
 * @param fieldSources     provider of each master data field that is set, keyed by the field name of
 *                         {@link SecurityBaseDataDto}
 * @param providerBaseData the master data each provider delivered itself, in the order of {@code providerIds}. A merged
 *                         row carries the provider id and instrument id of the primary provider only, but a connector
 *                         derives its URL extension from the instrument id of its own provider, e.g. the onvista
 *                         notation; these keep it.
 * @param conflicts        values of merged providers that differ from the value kept, in the order of the merge
 */
record LookupListing(SecuritySearchCandidateDto candidate, SecurityBaseDataResult baseDataResult,
    SecurityBaseDataDto baseData, Optional<InstrumentType> instrumentType, List<String> providerIds,
    Map<String, String> fieldSources, List<SecurityBaseDataDto> providerBaseData, List<FieldConflict> conflicts) {

  /**
   * A field in which a merged provider delivered another value than the one kept.
   *
   * @param field         field name of {@link SecurityBaseDataDto}
   * @param keptProvider  provider of the value kept
   * @param keptValue     the value kept
   * @param otherProvider provider whose value was not taken
   * @param otherValue    the value not taken
   */
  record FieldConflict(String field, String keptProvider, Object keptValue, String otherProvider, Object otherValue) {
  }

  /**
   * Creates the listing of a single provider; every set field is attributed to it.
   *
   * @param candidate      the search candidate
   * @param baseDataResult the master data request
   * @param baseData       the master data or the search values
   * @param instrumentType the derived instrument type
   * @return the listing
   */
  static LookupListing of(SecuritySearchCandidateDto candidate, SecurityBaseDataResult baseDataResult,
      SecurityBaseDataDto baseData, Optional<InstrumentType> instrumentType) {
    Map<String, String> sources = new LinkedHashMap<>();
    LookupListingMerger.fieldValues(baseData).forEach((field, value) -> {
      if (value != null) {
        sources.put(field, candidate.providerId());
      }
    });
    return new LookupListing(candidate, baseDataResult, baseData, instrumentType, List.of(candidate.providerId()),
        sources, List.of(baseData), List.of());
  }
}
