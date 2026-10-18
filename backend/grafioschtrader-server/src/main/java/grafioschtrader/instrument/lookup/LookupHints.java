package grafioschtrader.instrument.lookup;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import grafioschtrader.GlobalConstants;
import grafioschtrader.connector.securitysearch.SecuritySearchResult;
import grafioschtrader.dto.ConnectorAlternativeDto;
import grafioschtrader.dto.SecurityBaseDataDto;
import grafioschtrader.dto.SecurityLookupHint;
import grafioschtrader.dto.SecurityLookupHint.Severity;
import grafioschtrader.dto.SecuritySearchCandidateDto;
import grafioschtrader.instrument.lookup.LookupListing.FieldConflict;
import grafioschtrader.types.ConnectorProbeStatus;
import grafioschtrader.types.SecuritySearchStatus;

/**
 * Creates the hints of the security lookup: what it could not resolve, why, and what follows for the security form.
 * Without them the lookup would give up silently - a listing on another exchange than the chosen one is dropped, and a
 * field no provider delivers leaves the default of the form in place, e.g. the first trading day 2000-01-03.
 *
 * <p>
 * Only fields that the dialog takes over into the form and whose absence has a consequence are reported. A missing
 * product link of a share, for example, is normal and no hint.
 * </p>
 */
final class LookupHints {

  static final String OTHER_MIC = "LOOKUP_HINT_OTHER_MIC";
  static final String PROVIDER_FAILED = "LOOKUP_HINT_PROVIDER_FAILED";
  static final String BASE_DATA_FAILED = "LOOKUP_HINT_BASE_DATA_FAILED";
  static final String TYPE_MISMATCH = "INSTRUMENT_TYPE_MISMATCH_TOOLTIP";
  static final String ACTIVE_FROM_MISSING = "LOOKUP_HINT_ACTIVE_FROM_MISSING";
  static final String ACTIVE_TO_MISSING = "LOOKUP_HINT_ACTIVE_TO_MISSING";
  static final String DENOMINATION_MISSING = "LOOKUP_HINT_DENOMINATION_MISSING";
  static final String DISTRIBUTION_FREQUENCY_MISSING = "LOOKUP_HINT_DISTRIBUTION_FREQUENCY_MISSING";
  static final String TICKER_MISSING = "LOOKUP_HINT_TICKER_MISSING";
  static final String CONFLICT = "LOOKUP_HINT_CONFLICT";

  /** Outcomes of a search that are no failure: the provider found nothing or does not cover the query. */
  private static final Set<SecuritySearchStatus> NO_FAILURE = Set.of(SecuritySearchStatus.OK,
      SecuritySearchStatus.NO_MATCH, SecuritySearchStatus.UNSUPPORTED_QUERY);

  private static final String KEY_SUFFIX = "Key";
  private static final String DATE_SUFFIX = "Date";

  private LookupHints() {
  }

  /**
   * What the candidate-level rules need to know about the chosen asset class and the probes.
   *
   * @param bond               the chosen asset class is a directly held bond, which needs maturity, coupon frequency
   *                           and denomination
   * @param dividendApplicable dividends apply to the chosen asset class
   * @param distributesNothing the instrument distributes nothing, so a missing frequency has no consequence
   * @param dividendConnectors the probed dividend connectors; one of them may deliver the frequency
   */
  record CandidateContext(boolean bond, boolean dividendApplicable, boolean distributesNothing,
      List<ConnectorAlternativeDto> dividendConnectors) {
  }

  /**
   * Creates the hints of the query as a whole. A provider that found nothing on the queried exchange but the instrument
   * on another one is reported per exchange; when no candidate remains at all, this is an error, because the chosen
   * exchange is then probably wrong.
   *
   * @param results     the search result of every provider asked
   * @param queriedMic  MIC of the queried exchange
   * @param noCandidate true when the lookup proposes no listing
   * @return the hints, most important first
   */
  static List<SecurityLookupHint> queryHints(List<SecuritySearchResult> results, String queriedMic,
      boolean noCandidate) {
    List<SecurityLookupHint> hints = new ArrayList<>();
    otherMicProviders(results, queriedMic).forEach((mic, providers) -> hints
        .add(SecurityLookupHint.of(noCandidate ? Severity.ERROR : Severity.WARNING, "stockexchange", OTHER_MIC,
            Map.of("mic", mic, "queriedMic", queriedMic, "providers", String.join(", ", providers)))));
    results.stream().filter(r -> !NO_FAILURE.contains(r.status()))
        .forEach(r -> hints.add(SecurityLookupHint.of(Severity.WARNING, "providerId", PROVIDER_FAILED,
            Map.of("provider", r.providerId(), "status" + KEY_SUFFIX, r.status().name()))));
    return sorted(hints);
  }

  /**
   * Collects the exchanges on which providers found the instrument, counting only providers without a candidate on the
   * queried exchange: a provider that lists the instrument there as well has merely found its other listings.
   *
   * @return the providers by MIC, both sorted
   */
  static Map<String, Set<String>> otherMicProviders(List<SecuritySearchResult> results, String queriedMic) {
    Map<String, Set<String>> byMic = new TreeMap<>();
    if (queriedMic == null) {
      return byMic;
    }
    for (SecuritySearchResult result : results) {
      boolean onQueriedMic = result.candidates().stream()
          .anyMatch(c -> c.hasEstablishedMic() && queriedMic.equals(c.mic()));
      if (!onQueriedMic) {
        result.otherMicCandidates().stream().map(SecuritySearchCandidateDto::mic)
            .forEach(mic -> byMic.computeIfAbsent(mic, _ -> new TreeSet<>()).add(result.providerId()));
      }
    }
    return byMic;
  }

  /**
   * Creates the hints of one proposed listing.
   *
   * @param listing                the merged listing
   * @param baseData               its master data, merged from all providers
   * @param instrumentTypeMismatch the type the providers report contradicts the chosen asset class
   * @param context                asset class and probe facts
   * @return the hints, most important first
   */
  static List<SecurityLookupHint> candidateHints(LookupListing listing, SecurityBaseDataDto baseData,
      boolean instrumentTypeMismatch, CandidateContext context) {
    List<SecurityLookupHint> hints = new ArrayList<>();
    SecuritySearchStatus baseDataStatus = listing.baseDataResult().status();
    if (baseDataStatus != SecuritySearchStatus.OK) {
      hints.add(SecurityLookupHint.of(Severity.WARNING, "baseDataStatus", BASE_DATA_FAILED,
          Map.of("provider", listing.candidate().providerId(), "status" + KEY_SUFFIX, baseDataStatus.name())));
    }
    if (instrumentTypeMismatch) {
      hints.add(SecurityLookupHint.of(Severity.WARNING, "instrumentTypeMismatch", TYPE_MISMATCH, Map.of()));
    }
    addMissingFieldHints(hints, baseData, context);
    hints.addAll(conflictHints(listing));
    return sorted(hints);
  }

  /**
   * Turns the values in which the merged providers disagree into hints.
   *
   * @param listing the merged listing
   * @return one informational hint per conflict
   */
  static List<SecurityLookupHint> conflictHints(LookupListing listing) {
    return listing.conflicts().stream().map(LookupHints::conflictHint).toList();
  }

  private static void addMissingFieldHints(List<SecurityLookupHint> hints, SecurityBaseDataDto baseData,
      CandidateContext context) {
    if (baseData.activeFromDate() == null) {
      hints.add(SecurityLookupHint.of(Severity.WARNING, "activeFromDate", ACTIVE_FROM_MISSING,
          Map.of("default" + DATE_SUFFIX, GlobalConstants.OLDEST_TRADING_DAY)));
    }
    if (baseData.tickerSymbol() == null) {
      hints.add(SecurityLookupHint.of(Severity.INFO, "tickerSymbol", TICKER_MISSING, Map.of()));
    }
    if (context.bond()) {
      if (baseData.activeToDate() == null) {
        hints.add(SecurityLookupHint.of(Severity.WARNING, "activeToDate", ACTIVE_TO_MISSING, Map.of()));
      }
      if (baseData.denomination() == null) {
        hints.add(SecurityLookupHint.of(Severity.INFO, "denomination", DENOMINATION_MISSING, Map.of()));
      }
    }
    if (isDistributionFrequencyMissing(baseData, context)) {
      hints.add(SecurityLookupHint.of(context.bond() ? Severity.WARNING : Severity.INFO, "distributionFrequency",
          DISTRIBUTION_FREQUENCY_MISSING, Map.of()));
    }
  }

  /**
   * A bond needs its coupon frequency from the master data. A distributing instrument may get it from a dividend
   * connector instead, as the dialog falls back to the frequency of the connectors.
   */
  private static boolean isDistributionFrequencyMissing(SecurityBaseDataDto baseData, CandidateContext context) {
    if (baseData.distributionFrequency() != null || context.distributesNothing()) {
      return false;
    }
    if (context.bond()) {
      return true;
    }
    return context.dividendApplicable() && context.dividendConnectors().stream()
        .noneMatch(a -> a.probeStatus() == ConnectorProbeStatus.OK && a.distributionFrequency() != null);
  }

  private static SecurityLookupHint conflictHint(FieldConflict conflict) {
    Map<String, String> args = new LinkedHashMap<>();
    args.put("keptProvider", conflict.keptProvider());
    args.put("otherProvider", conflict.otherProvider());
    putValue(args, "kept", conflict.keptValue());
    putValue(args, "other", conflict.otherValue());
    return SecurityLookupHint.of(Severity.INFO, conflict.field(), CONFLICT, args);
  }

  /** Marks a date and an enum value by the suffix of the argument name, so that the client formats or translates it. */
  private static void putValue(Map<String, String> args, String name, Object value) {
    if (value instanceof LocalDate date) {
      args.put(name + DATE_SUFFIX, date.toString());
    } else if (value instanceof Enum<?> e) {
      args.put(name + KEY_SUFFIX, e.name());
    } else {
      args.put(name, String.valueOf(value));
    }
  }

  private static List<SecurityLookupHint> sorted(List<SecurityLookupHint> hints) {
    return hints.stream().sorted(Comparator.comparing(SecurityLookupHint::severity)).toList();
  }
}
