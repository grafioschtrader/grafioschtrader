package grafioschtrader.connector.securitysearch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafioschtrader.dto.SecuritySearchCandidateDto;
import grafioschtrader.types.IdentityEvidence;
import grafioschtrader.types.SecuritySearchStatus;

/**
 * Identity rules shared by all providers, without HTTP.
 */
class SearchCandidateMatcherTest {

  private static final String ISIN = "CH0012005267";

  private static SecuritySearchCandidateDto candidate(String symbol, String isin, IdentityEvidence isinEv,
      String currency, IdentityEvidence currencyEv, String mic, IdentityEvidence micEv) {
    return new SecuritySearchCandidateDto("p", symbol, null, null, isin, isinEv, currency, currencyEv, mic, micEv,
        "Name", "EQUITY");
  }

  @Test
  @DisplayName("An exact match keeps its evidence and yields OK")
  void exactMatch() {
    SecuritySearchResult result = SearchCandidateMatcher.match("p", SecuritySearchQuery.of(ISIN, "CHF", "XSWX", null),
        List.of(candidate("NOVN", ISIN, IdentityEvidence.RETURNED, "CHF", IdentityEvidence.RETURNED, "XSWX",
            IdentityEvidence.RETURNED)));
    assertEquals(SecuritySearchStatus.OK, result.status());
    assertEquals(1, result.candidates().size());
    assertEquals("NOVN", result.uniqueCandidateOnMic("XSWX", null).orElseThrow().providerSymbol());
  }

  @Test
  @DisplayName("Several listings on the queried exchange stay ambiguous; the ticker hint narrows them")
  void multipleMatches() {
    SecuritySearchResult result = SearchCandidateMatcher.match("p", SecuritySearchQuery.of(ISIN, null, "XSWX", "NOVN"),
        List.of(
            candidate("NOVNEE.SW", null, IdentityEvidence.UNKNOWN, null, IdentityEvidence.UNKNOWN, "XSWX",
                IdentityEvidence.RETURNED),
            candidate("NOVN.SW", null, IdentityEvidence.UNKNOWN, null, IdentityEvidence.UNKNOWN, "XSWX",
                IdentityEvidence.RETURNED)));
    assertEquals(2, result.candidates().size());
    assertTrue(result.uniqueCandidateOnMic("XSWX", null).isEmpty(), "No ticker hint: ambiguous");
    assertEquals("NOVN.SW", result.uniqueCandidateOnMic("XSWX", "NOVN").orElseThrow().providerSymbol());
  }

  @Test
  @DisplayName("Two currencies of the same listing without currency in the query remain ambiguous")
  void multipleCurrenciesStayAmbiguous() {
    SecuritySearchResult result = SearchCandidateMatcher.match("p", SecuritySearchQuery.of(ISIN, null, "XSWX", "AAPL"),
        List.of(
            candidate("AAPL", ISIN, IdentityEvidence.RETURNED, "CHF", IdentityEvidence.RETURNED, "XSWX",
                IdentityEvidence.RETURNED),
            candidate("AAPL", ISIN, IdentityEvidence.RETURNED, "USD", IdentityEvidence.RETURNED, "XSWX",
                IdentityEvidence.RETURNED)));
    assertEquals(2, result.candidates().size());
    assertTrue(result.uniqueCandidateOnMic("XSWX", "AAPL").isEmpty());
  }

  @Test
  @DisplayName("A delivered currency other than the queried one rejects the candidate")
  void wrongCurrency() {
    SecuritySearchResult result = SearchCandidateMatcher.match("p", SecuritySearchQuery.of(ISIN, "USD", "XSWX", null),
        List.of(
            candidate("AAPL", ISIN, IdentityEvidence.RETURNED, "CHF", IdentityEvidence.RETURNED, "XSWX",
                IdentityEvidence.RETURNED),
            candidate("AAPL", ISIN, IdentityEvidence.RETURNED, "USD", IdentityEvidence.RETURNED, "XSWX",
                IdentityEvidence.RETURNED)));
    assertEquals(1, result.candidates().size());
    assertEquals("USD", result.candidates().getFirst().currency());
    assertEquals(1, result.rejectedCandidates());
  }

  @Test
  @DisplayName("A delivered MIC or ISIN other than the queried one rejects the candidate; nothing left is NO_MATCH")
  void wrongMicAndIsin() {
    SecuritySearchResult result = SearchCandidateMatcher.match("p", SecuritySearchQuery.of(ISIN, null, "XSWX", null),
        List.of(
            candidate("CSSPX.MI", null, IdentityEvidence.UNKNOWN, null, IdentityEvidence.UNKNOWN, "MTAA",
                IdentityEvidence.RETURNED),
            candidate("X", "CH0000000001", IdentityEvidence.RETURNED, null, IdentityEvidence.UNKNOWN, null,
                IdentityEvidence.UNKNOWN)));
    assertEquals(SecuritySearchStatus.NO_MATCH, result.status());
    assertEquals(2, result.rejectedCandidates());
    assertEquals(List.of("CSSPX.MI"),
        result.otherMicCandidates().stream().map(SecuritySearchCandidateDto::providerSymbol).toList(),
        "only the listing rejected for its exchange alone hints at another exchange; the other ISIN does not");
  }

  @Test
  @DisplayName("A listing on another exchange in another currency is no hint at the queried instrument")
  void otherMicInOtherCurrency() {
    SecuritySearchResult result = SearchCandidateMatcher.match("p", SecuritySearchQuery.of(ISIN, "USD", "XNAS", null),
        List.of(
            candidate("CHGG", null, IdentityEvidence.UNKNOWN, "USD", IdentityEvidence.RETURNED, "XNYS",
                IdentityEvidence.RETURNED),
            candidate("0CG", null, IdentityEvidence.UNKNOWN, "EUR", IdentityEvidence.RETURNED, "XETR",
                IdentityEvidence.RETURNED)));
    assertEquals(List.of("XNYS"), result.otherMicCandidates().stream().map(SecuritySearchCandidateDto::mic).toList());
  }

  @Test
  @DisplayName("A MIC that could not be mapped stays unknown and is neither rejected nor confirmed")
  void absentMicMapping() {
    SecuritySearchResult result = SearchCandidateMatcher.match("p", SecuritySearchQuery.of(ISIN, null, "XSWX", null),
        List.of(candidate("NOVN.XX", null, IdentityEvidence.UNKNOWN, null, IdentityEvidence.UNKNOWN, null,
            IdentityEvidence.UNKNOWN)));
    assertEquals(SecuritySearchStatus.OK, result.status());
    assertTrue(result.uniqueCandidateOnMic("XSWX", "NOVN").isEmpty());
  }

  @Test
  @DisplayName("Incomplete metadata is kept as unknown and ordered after better established candidates")
  void incompleteMetadata() {
    SecuritySearchCandidateDto incomplete = candidate("A", null, IdentityEvidence.UNKNOWN, null,
        IdentityEvidence.UNKNOWN, null, IdentityEvidence.UNKNOWN);
    SecuritySearchCandidateDto complete = candidate("B", ISIN, IdentityEvidence.RETURNED, "CHF",
        IdentityEvidence.RETURNED, "XSWX", IdentityEvidence.RETURNED);
    SecuritySearchResult result = SearchCandidateMatcher.match("p", SecuritySearchQuery.of(ISIN, "CHF", "XSWX", null),
        List.of(incomplete, complete));
    assertEquals(List.of("B", "A"),
        result.candidates().stream().map(SecuritySearchCandidateDto::providerSymbol).toList());
    assertNull(result.candidates().get(1).currency());
  }

  @Test
  @DisplayName("Minor-unit currencies compare equal to their main currency")
  void minorUnitCurrency() {
    SecuritySearchResult result = SearchCandidateMatcher.match("p",
        SecuritySearchQuery.of("GB0002374006", "GBP", null, null), List.of(candidate("DGE.L", null,
            IdentityEvidence.UNKNOWN, "GBp", IdentityEvidence.RETURNED, null, IdentityEvidence.UNKNOWN)));
    assertEquals(SecuritySearchStatus.OK, result.status());
    assertEquals("GBp", result.candidates().getFirst().currency());
  }

  @Test
  @DisplayName("No candidates at all yield NO_MATCH without detail")
  void emptyResponse() {
    SecuritySearchResult result = SearchCandidateMatcher.match("p", SecuritySearchQuery.of(ISIN, null, null, null),
        List.of());
    assertEquals(SecuritySearchStatus.NO_MATCH, result.status());
    assertEquals(0, result.rejectedCandidates());
  }
}
