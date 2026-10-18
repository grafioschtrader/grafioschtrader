package grafioschtrader.instrument.lookup;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafioschtrader.connector.securitysearch.SecuritySearchResult;
import grafioschtrader.dto.SecurityLookupHint;
import grafioschtrader.dto.SecurityLookupHint.Severity;
import grafioschtrader.dto.SecuritySearchCandidateDto;
import grafioschtrader.types.DistributionFrequency;
import grafioschtrader.types.IdentityEvidence;
import grafioschtrader.types.SecuritySearchStatus;

/** The hints of the query as a whole and the arguments of a conflict hint. */
class LookupHintsTest {

  private static SecuritySearchCandidateDto onMic(String providerId, String mic) {
    return new SecuritySearchCandidateDto(providerId, "X", null, null, null, IdentityEvidence.UNKNOWN, null,
        IdentityEvidence.UNKNOWN, mic, IdentityEvidence.RETURNED, "X", null);
  }

  @Test
  @DisplayName("Only providers without a listing on the queried exchange point to another exchange")
  void otherMicOnlyWithoutQueriedListing() {
    SecuritySearchResult yahoo = new SecuritySearchResult("yahoo", SecuritySearchStatus.NO_MATCH, List.of(), 1, null,
        List.of(onMic("yahoo", "XNYS")));
    SecuritySearchResult boursorama = new SecuritySearchResult("boursorama", SecuritySearchStatus.NO_MATCH, List.of(),
        2, null, List.of(onMic("boursorama", "XNYS"), onMic("boursorama", "XLON")));
    SecuritySearchResult onvista = new SecuritySearchResult("onvista", SecuritySearchStatus.OK,
        List.of(onMic("onvista", "XNAS")), 1, null, List.of(onMic("onvista", "XETR")));

    assertEquals(Map.of("XLON", Set.of("boursorama"), "XNYS", Set.of("boursorama", "yahoo")),
        LookupHints.otherMicProviders(List.of(yahoo, boursorama, onvista), "XNAS"),
        "onvista lists the instrument on XNAS, so its Xetra listing is merely another listing");

    List<SecurityLookupHint> hints = LookupHints.queryHints(List.of(yahoo, boursorama, onvista,
        SecuritySearchResult.failure("eodhd", SecuritySearchStatus.MISSING_CREDENTIALS, null)), "XNAS", false);
    assertEquals(List.of(LookupHints.OTHER_MIC, LookupHints.OTHER_MIC, LookupHints.PROVIDER_FAILED),
        hints.stream().map(SecurityLookupHint::messageKey).toList());
    assertEquals(Severity.WARNING, hints.getFirst().severity(), "another provider found a listing on XNAS");
    assertEquals("boursorama, yahoo", hints.get(1).args().get("providers"));
    assertEquals(Map.of("provider", "eodhd", "statusKey", "MISSING_CREDENTIALS"), hints.get(2).args());
  }

  @Test
  @DisplayName("A conflict marks dates and enum values by the suffix of the argument name")
  void conflictArguments() {
    LookupListing listing = new LookupListing(onMic("six", "XSWX"), null, null, Optional.empty(),
        List.of("six", "yahoo"), Map.of(), List.of(),
        List.of(
            new LookupListing.FieldConflict("activeFromDate", "six", LocalDate.of(2000, 1, 3), "yahoo",
                LocalDate.of(2001, 5, 2)),
            new LookupListing.FieldConflict("distributionFrequency", "six", DistributionFrequency.DF_YEAR, "eodhd",
                DistributionFrequency.DF_SEMI_ANNUAL)));
    List<SecurityLookupHint> conflicts = LookupHints.conflictHints(listing);
    assertEquals(
        Map.of("keptProvider", "six", "otherProvider", "yahoo", "keptDate", "2000-01-03", "otherDate", "2001-05-02"),
        conflicts.getFirst().args());
    assertEquals("DF_SEMI_ANNUAL", conflicts.get(1).args().get("otherKey"));
  }
}
