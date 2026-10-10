package grafioschtrader.connector.securitysearch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafioschtrader.dto.SecuritySearchCandidateDto;
import grafioschtrader.types.IdentityEvidence;
import grafioschtrader.types.SecuritySearchStatus;

/**
 * The aggregation keeps the results of working providers when others fail, throw or hang.
 */
class SecuritySearchServiceTest {

  private static final SecuritySearchQuery QUERY = SecuritySearchQuery.of("CH0012005267", "CHF", "XSWX", "NOVN");

  private record StubProvider(String id, Function<SecuritySearchQuery, SecuritySearchResult> answer)
      implements ISecuritySearchProvider {

    @Override
    public String getProviderId() {
      return id;
    }

    @Override
    public boolean supports(SecuritySearchQuery query) {
      return true;
    }

    @Override
    public SecuritySearchResult search(SecuritySearchQuery query) {
      return answer.apply(query);
    }
  }

  private static SecuritySearchResult ok(String id) {
    return SearchCandidateMatcher.match(id, QUERY, List.of(new SecuritySearchCandidateDto(id, "NOVN", null, null,
        null, IdentityEvidence.UNKNOWN, null, IdentityEvidence.UNKNOWN, "XSWX", IdentityEvidence.RETURNED, "Novartis",
        null)));
  }

  @Test
  @DisplayName("A failing, a throwing and a hanging provider do not discard the result of a working one")
  void partialResults() {
    SecuritySearchService service = new SecuritySearchService(List.of(new StubProvider("good", _ -> ok("good")),
        new StubProvider("limited",
            _ -> SecuritySearchResult.failure("limited", SecuritySearchStatus.RATE_LIMITED, "HTTP 429")),
        new StubProvider("throwing", _ -> {
          throw new IllegalStateException("boom");
        }), new StubProvider("hanging", _ -> {
          try {
            Thread.sleep(10_000);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          return ok("hanging");
        })));

    long start = System.nanoTime();
    List<SecuritySearchResult> results = service.search(QUERY, null, Duration.ofMillis(500));
    assertTrue(Duration.ofNanos(System.nanoTime() - start).toMillis() < 5_000, "Deadline must bound the search");

    assertEquals(List.of("good", "limited", "throwing", "hanging"),
        results.stream().map(SecuritySearchResult::providerId).toList());
    assertEquals(List.of(SecuritySearchStatus.OK, SecuritySearchStatus.RATE_LIMITED,
        SecuritySearchStatus.PROVIDER_ERROR, SecuritySearchStatus.TIMEOUT),
        results.stream().map(SecuritySearchResult::status).toList());
    assertEquals("NOVN", results.getFirst().uniqueCandidateOnMic("XSWX", "NOVN").orElseThrow().providerSymbol());
  }

  @Test
  @DisplayName("Only the selected providers are asked")
  void providerSelection() {
    SecuritySearchService service = new SecuritySearchService(
        List.of(new StubProvider("a", _ -> ok("a")), new StubProvider("b", _ -> ok("b"))));
    List<SecuritySearchResult> results = service.search(QUERY, Set.of("b"), Duration.ofSeconds(5));
    assertEquals(List.of("b"), results.stream().map(SecuritySearchResult::providerId).toList());
  }
}
