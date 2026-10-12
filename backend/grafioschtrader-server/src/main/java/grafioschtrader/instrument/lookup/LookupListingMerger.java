package grafioschtrader.instrument.lookup;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import grafioschtrader.connector.securitysearch.EuronextSecuritySearch;
import grafioschtrader.connector.securitysearch.InstrumentType;
import grafioschtrader.connector.securitysearch.OpenFigiSecuritySearch;
import grafioschtrader.connector.securitysearch.SixSecuritySearch;
import grafioschtrader.connector.securitysearch.ViennaSecuritySearch;
import grafioschtrader.connector.securitysearch.XetraSecuritySearch;
import grafioschtrader.dto.SecurityBaseDataDto;

/**
 * Merges the listings that several providers found for the same instrument on the same exchange, so that the dialog
 * shows one row per listing. The provider that is the exchange itself (SIX on XSWX, Deutsche Börse on XETR and XFRA,
 * Wiener Börse on XVIE, Euronext on its exchanges) is primary: its values are kept, and another provider only fills the fields it left open - e.g.
 * Yahoo the first trading day of a share, which SIX does not deliver. Every field keeps the provider it came from.
 *
 * <p>
 * Two listings are the same when their MIC and their currency (minor units folded) agree and the exchange ticker of the
 * secondary one equals the ticker of the primary one, case-insensitive. Deutsche Börse delivers no ticker; its listing
 * absorbs the one listing of each other provider on the same exchange in the same currency, which then supplies the
 * ticker. A listing without ticker that names the same ISIN is absorbed the same way, e.g. onvista for a bond on
 * SIX; listings without ticker are merged after those with one, so that the outcome does not depend on the order of the
 * providers. A row without ISIN, e.g. Yahoo's, takes the ISIN of a listing it absorbs by ticker, which lets a listing
 * without ticker of the same ISIN join it; when no listing confirmed the ISIN, a listing without ticker joins the one
 * row without ISIN of its exchange and currency. A listing without currency - Euronext, or a bond Boursorama quotes in percent - is compatible with any; a
 * primary listing without currency takes the one of the listing it absorbs. The raw attributes of a secondary provider
 * are kept with its provider id as prefix, e.g. {@code yahoo.instrumentType}.
 * </p>
 */
final class LookupListingMerger {

  private LookupListingMerger() {
  }

  /**
   * Merges the listings.
   *
   * @param listings listings of all providers, each with its master data
   * @return one listing per distinct listing, primary providers first
   */
  static List<LookupListing> merge(List<LookupListing> listings) {
    List<LookupListing> result = new ArrayList<>();
    listings.stream().filter(LookupListingMerger::isExchangeProvider).forEach(result::add);
    List<LookupListing> others = listings.stream()
        .filter(listing -> !isExchangeProvider(listing) && !isIdentificationProvider(listing)).toList();
    others.stream().filter(listing -> listing.baseData().tickerSymbol() != null)
        .forEach(listing -> mergeOrAdd(result, listing));
    others.stream().filter(listing -> listing.baseData().tickerSymbol() == null)
        .forEach(listing -> mergeWithoutTicker(result, listing));
    listings.stream().filter(LookupListingMerger::isIdentificationProvider)
        .forEach(listing -> supplement(result, listing));
    return result;
  }

  private static void mergeOrAdd(List<LookupListing> result, LookupListing listing) {
    int match = indexOfSameListing(result, listing);
    if (match < 0) {
      result.add(listing);
    } else {
      result.set(match, merge(result.get(match), listing));
    }
  }

  /**
   * A listing without ticker joins the row with the same ISIN; failing that, like an OpenFIGI hit, the one row of the
   * same exchange and currency whose ISIN is unknown - Yahoo's, which never delivers an ISIN. It forms a row of its own
   * when no row or several rows qualify.
   */
  private static void mergeWithoutTicker(List<LookupListing> result, LookupListing listing) {
    int match = indexOfSameListing(result, listing);
    if (match < 0) {
      List<Integer> candidates = new ArrayList<>();
      for (int i = 0; i < result.size(); i++) {
        LookupListing row = result.get(i);
        if (row.baseData().isin() == null && !row.providerIds().contains(listing.candidate().providerId())
            && isSameExchangeAndCurrency(row.baseData(), listing.baseData())) {
          candidates.add(i);
        }
      }
      match = candidates.size() == 1 ? candidates.getFirst() : -1;
    }
    if (match < 0) {
      result.add(listing);
    } else {
      result.set(match, merge(result.get(match), listing));
    }
  }

  /**
   * OpenFIGI identifies a listing by an exact ISIN and MIC filter but supplies little more than ticker and type, so it
   * only fills the open fields of the row of the same exchange - in particular the ticker Deutsche Börse does not
   * deliver. It forms a row of its own only when no other provider found the listing.
   */
  private static void supplement(List<LookupListing> result, LookupListing identification) {
    String providerId = identification.candidate().providerId();
    for (int i = 0; i < result.size(); i++) {
      LookupListing row = result.get(i);
      if (!row.providerIds().contains(providerId)
          && isSameExchangeAndCurrency(row.baseData(), identification.baseData())) {
        result.set(i, merge(row, identification));
        return;
      }
    }
    if (result.isEmpty()) {
      result.add(identification);
    }
  }

  private static boolean isIdentificationProvider(LookupListing listing) {
    return OpenFigiSecuritySearch.PROVIDER_ID.equals(listing.candidate().providerId());
  }

  /** Same exchange; an unknown currency of either side is compatible, since the identification filtered the ISIN. */
  private static boolean isSameExchangeAndCurrency(SecurityBaseDataDto row, SecurityBaseDataDto identification) {
    return row.mic() != null && row.mic().equals(identification.mic())
        && (row.currency() == null || identification.currency() == null
            || SecurityLookupService.sameCurrency(row.currency(), identification.currency()));
  }

  private static boolean isExchangeProvider(LookupListing listing) {
    String providerId = listing.candidate().providerId();
    return SixSecuritySearch.PROVIDER_ID.equals(providerId) || XetraSecuritySearch.PROVIDER_ID.equals(providerId)
        || ViennaSecuritySearch.PROVIDER_ID.equals(providerId) || EuronextSecuritySearch.PROVIDER_ID.equals(providerId);
  }

  private static int indexOfSameListing(List<LookupListing> listings, LookupListing secondary) {
    for (int i = 0; i < listings.size(); i++) {
      LookupListing primary = listings.get(i);
      boolean providerAlreadyMerged = primary.providerIds().contains(secondary.candidate().providerId());
      if (!providerAlreadyMerged && isSameListing(primary.baseData(), secondary.baseData())) {
        return i;
      }
    }
    return -1;
  }

  static boolean isSameListing(SecurityBaseDataDto primary, SecurityBaseDataDto secondary) {
    return primary.mic() != null && primary.mic().equals(secondary.mic())
        && (primary.currency() == null || secondary.currency() == null
            || SecurityLookupService.sameCurrency(primary.currency(), secondary.currency()))
        && (primary.tickerSymbol() == null || secondary.tickerSymbol() == null && sameIsin(primary, secondary)
            || secondary.tickerSymbol() != null && primary.tickerSymbol().toUpperCase(Locale.ROOT)
                .equals(secondary.tickerSymbol().toUpperCase(Locale.ROOT)));
  }

  /** Both listings name the same ISIN; a listing whose provider does not deliver it never matches this way. */
  private static boolean sameIsin(SecurityBaseDataDto primary, SecurityBaseDataDto secondary) {
    return primary.isin() != null && primary.isin().equals(secondary.isin());
  }

  /** Fills the open fields of the primary listing from the secondary one. */
  private static LookupListing merge(LookupListing primary, LookupListing secondary) {
    SecurityBaseDataDto p = primary.baseData();
    SecurityBaseDataDto s = secondary.baseData();
    String secondaryId = secondary.candidate().providerId();
    Map<String, String> sources = new LinkedHashMap<>(primary.fieldSources());
    Map<String, Object> primaryValues = fieldValues(p);
    fieldValues(s).forEach((field, value) -> {
      if (primaryValues.get(field) == null && value != null) {
        sources.put(field, secondaryId);
      }
    });
    Map<String, String> attributes = new LinkedHashMap<>(p.providerAttributes());
    s.providerAttributes().forEach((key, value) -> attributes.put(secondaryId + "." + key, value));
    SecurityBaseDataDto merged = new SecurityBaseDataDto(p.providerId(), p.providerInstrumentId(),
        first(p.isin(), s.isin()),
        first(p.currency(), s.currency()), p.mic(), first(p.name(), s.name()), first(p.tickerSymbol(), s.tickerSymbol()),
        first(p.issuerCountry(), s.issuerCountry()), first(p.activeFromDate(), s.activeFromDate()),
        first(p.activeToDate(), s.activeToDate()), first(p.distributionFrequency(), s.distributionFrequency()),
        first(p.denomination(), s.denomination()), first(p.productLink(), s.productLink()),
        first(p.stockexchangeLink(), s.stockexchangeLink()), first(p.listedSince(), s.listedSince()),
        first(p.distributing(), s.distributing()), attributes);
    List<String> providerIds = new ArrayList<>(primary.providerIds());
    providerIds.add(secondaryId);
    List<SecurityBaseDataDto> providerBaseData = new ArrayList<>(primary.providerBaseData());
    providerBaseData.addAll(secondary.providerBaseData());
    return new LookupListing(primary.candidate(), primary.baseDataResult(), merged,
        mergeTypes(primary.instrumentType(), secondary.instrumentType()), List.copyOf(providerIds), sources,
        List.copyOf(providerBaseData));
  }

  /**
   * Combines two derived instrument types. A missing type is taken from the other provider; contradicting financial
   * instruments leave the type open; an open asset class type is filled from the other provider.
   */
  static Optional<InstrumentType> mergeTypes(Optional<InstrumentType> primary, Optional<InstrumentType> secondary) {
    if (primary.isEmpty() || secondary.isEmpty()) {
      return primary.isPresent() ? primary : secondary;
    }
    InstrumentType p = primary.get();
    InstrumentType s = secondary.get();
    if (p.specialInvestmentInstrument() != s.specialInvestmentInstrument()) {
      return Optional.empty();
    }
    return Optional.of(new InstrumentType(first(p.categoryType(), s.categoryType()), p.specialInvestmentInstrument()));
  }

  /** The fields that carry a provenance, by their name in {@link SecurityBaseDataDto}. */
  static Map<String, Object> fieldValues(SecurityBaseDataDto d) {
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("currency", d.currency());
    values.put("name", d.name());
    values.put("tickerSymbol", d.tickerSymbol());
    values.put("issuerCountry", d.issuerCountry());
    values.put("activeFromDate", d.activeFromDate());
    values.put("activeToDate", d.activeToDate());
    values.put("distributionFrequency", d.distributionFrequency());
    values.put("denomination", d.denomination());
    values.put("productLink", d.productLink());
    values.put("stockexchangeLink", d.stockexchangeLink());
    return values;
  }

  private static <T> T first(T primary, T secondary) {
    return primary != null ? primary : secondary;
  }
}
