package grafioschtrader.connector.securitysearch;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import grafioschtrader.connector.yahoo.YahooHelper;
import grafioschtrader.dto.SecuritySearchCandidateDto;
import grafioschtrader.test.start.GTforTest;
import grafioschtrader.types.IdentityEvidence;

/**
 * Asks the live, key-free providers (Yahoo, OpenFIGI, SIX, Xetra) for the examples in
 * {@code securitysearch/live-examples.csv} and prints the coverage per provider and MIC: how many examples resolved to
 * exactly one listing on the queried exchange, how many stayed ambiguous, found nothing, were not supported or failed,
 * and the evidence of the resolved listing (I=ISIN, C=currency, M=MIC; R=returned, F=filtered, U=unknown).
 *
 * <p>
 * The coverage is reported, not asserted - a provider that does not know a market is a finding, not a failure. Only
 * the rows with an expected Yahoo symbol are asserted, as {@code YahooSymbolSearchTest} did before.
 * </p>
 */
@SpringBootTest(classes = GTforTest.class)
@ActiveProfiles("prod")
class SecuritySearchLiveTest {

  @Autowired
  private SecuritySearchService securitySearchService;

  private record Example(String mic, String isin, String currency, String ticker, String name, String expectedYahoo) {
  }

  @Test
  @DisplayName("Coverage of the key-free providers on XSWX, XNAS, XNYS and XETR")
  void coverage() {
    Map<String, int[]> coverage = new TreeMap<>();
    List<String> failures = new ArrayList<>();
    for (Example example : readExamples()) {
      SecuritySearchQuery query = SecuritySearchQuery.of(example.isin(), example.currency(), example.mic(),
          example.ticker());
      for (SecuritySearchResult result : securitySearchService.search(query)) {
        Optional<SecuritySearchCandidateDto> unique = result.uniqueCandidateOnMic(example.mic(), example.ticker());
        String outcome = unique.isPresent() ? "UNIQUE"
            : switch (result.status()) {
            case OK -> "AMBIGUOUS";
            case NO_MATCH -> "NO_MATCH";
            case UNSUPPORTED_QUERY -> "UNSUPPORTED";
            default -> "FAILED";
            };
        coverage.computeIfAbsent(result.providerId() + " " + example.mic(), _ -> new int[5])[index(outcome)]++;
        System.out.printf("%-8s %-4s %-12s %-6s %-11s %-9s %-24s %s%n", result.providerId(), example.mic(),
            example.isin(), example.currency(), outcome, unique.map(SecuritySearchLiveTest::evidence).orElse(""),
            unique.map(SecuritySearchCandidateDto::providerSymbol).orElse(""),
            result.detail() == null ? "" : result.detail());
        if (YahooHelper.YAHOO.equals(result.providerId()) && !example.expectedYahoo().isEmpty()
            && !example.expectedYahoo().equals(unique.map(SecuritySearchCandidateDto::providerSymbol).orElse(null))
            && !result.status().isTransient()) {
          failures.add(example.name() + ": expected " + example.expectedYahoo() + ", got "
              + unique.map(SecuritySearchCandidateDto::providerSymbol).orElse(result.status().name()));
        }
      }
    }
    System.out.printf("%n%-14s %6s %9s %8s %11s %6s%n", "provider MIC", "UNIQUE", "AMBIGUOUS", "NO_MATCH",
        "UNSUPPORTED", "FAILED");
    coverage.forEach((key, c) -> System.out.printf("%-14s %6d %9d %8d %11d %6d%n", key, c[0], c[1], c[2], c[3], c[4]));
    assertTrue(failures.isEmpty(), String.join("\n", failures));
  }

  private static int index(String outcome) {
    return List.of("UNIQUE", "AMBIGUOUS", "NO_MATCH", "UNSUPPORTED", "FAILED").indexOf(outcome);
  }

  private static String evidence(SecuritySearchCandidateDto c) {
    return "I" + letter(c.isinEvidence()) + " C" + letter(c.currencyEvidence()) + " M" + letter(c.micEvidence());
  }

  private static char letter(IdentityEvidence evidence) {
    return evidence.name().charAt(0);
  }

  private static List<Example> readExamples() {
    List<Example> examples = new ArrayList<>();
    String[] lines = FixtureHttpServer.readFixture("live-examples.csv").split("\\R");
    for (int i = 1; i < lines.length; i++) {
      if (!lines[i].isBlank()) {
        String[] f = lines[i].split("\\|", -1);
        examples.add(new Example(f[0], f[1], f[2], f[3], f[4], f[5]));
      }
    }
    return examples;
  }
}
