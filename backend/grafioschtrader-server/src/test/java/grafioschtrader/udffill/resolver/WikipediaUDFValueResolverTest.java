package grafioschtrader.udffill.resolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafioschtrader.entities.Security;
import grafioschtrader.udffill.http.FakeHttpTransport;
import grafioschtrader.udffill.http.FakeHttpTransport.Answer;
import grafioschtrader.udffill.http.UDFFillHttp;
import grafioschtrader.udffill.resolver.ResolveOutcome.Found;
import grafioschtrader.udffill.resolver.ResolveOutcome.NotFound;
import grafioschtrader.udffill.resolver.ResolveOutcome.NotFoundReason;
import grafioschtrader.udffill.resolver.ResolveOutcome.Retryable;

/**
 * The Wikipedia resolver against recorded answers of the Wikidata Action API under {@code udffill/wikidata/}. The real
 * fetcher runs with an injected DNS lookup and a fake transport; no test opens a socket.
 */
class WikipediaUDFValueResolverTest {

  private static final String APPLE = "US0378331005";

  private final List<Long> sleeps = new ArrayList<>();

  private static Answer recorded(String name) {
    try (InputStream in = WikipediaUDFValueResolverTest.class.getResourceAsStream("/udffill/wikidata/" + name)) {
      return Answer.json(new String(in.readAllBytes(), StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private FakeHttpTransport transport(Answer search, Answer entities) {
    return new FakeHttpTransport(uri -> uri.getRawQuery().contains("action=wbgetentities") ? entities : search);
  }

  private ResolveOutcome resolve(FakeHttpTransport transport, String isin, String languages) {
    UDFFillHttp http = new UDFFillHttp(_ -> new InetAddress[] { InetAddress.ofLiteral("208.80.154.224") }, transport,
        UDFFillHttp.userAgent("test"));
    WikipediaUDFValueResolver resolver = new WikipediaUDFValueResolver(http, sleeps::add);
    Security security = new Security("Apple Inc.", "USD", null, null, null, null, null, "AAPL", isin);
    return resolver.resolve(security, resolver.normalizeArgument(languages));
  }

  @Test
  @DisplayName("One hit carrying the ISIN: the article of the first requested language with a sitelink")
  void oneHit() {
    FakeHttpTransport transport = transport(recorded("search-one-hit.json"), recorded("entities-one-hit.json"));

    assertThat(resolve(transport, APPLE, "de,en"))
        .isEqualTo(new Found("https://de.wikipedia.org/wiki/Apple", "https://www.wikidata.org/wiki/Q312"));
    assertThat(resolve(transport, APPLE, "en,de"))
        .isEqualTo(new Found("https://en.wikipedia.org/wiki/Apple_Inc.", "https://www.wikidata.org/wiki/Q312"));
    URI search = transport.getRequests().getFirst().uri();
    assertThat(URLDecoder.decode(search.getRawQuery(), StandardCharsets.UTF_8))
        .contains("srsearch=haswbstatement:P946=" + APPLE).contains("maxlag=5");
  }

  @Test
  @DisplayName("Two hits of which only one carries the ISIN exactly: that one wins")
  void twoHitsOneCarries() {
    assertThat(
        resolve(transport(recorded("search-two-hits.json"), recorded("entities-two-one-carries.json")), APPLE, "en"))
            .isEqualTo(new Found("https://en.wikipedia.org/wiki/Apple_Inc.", "https://www.wikidata.org/wiki/Q312"));
  }

  @Test
  @DisplayName("Two hits that both carry the ISIN are ambiguous")
  void twoHitsBothCarry() {
    assertThat(
        resolve(transport(recorded("search-two-hits.json"), recorded("entities-two-both-carry.json")), APPLE, "de,en"))
            .isEqualTo(new NotFound(NotFoundReason.AMBIGUOUS));
  }

  @Test
  @DisplayName("Zero hits ask no entities; no ISIN asks nothing at all")
  void zeroHitsAndNoIsin() {
    FakeHttpTransport transport = transport(recorded("search-zero-hits.json"), recorded("entities-one-hit.json"));
    assertThat(resolve(transport, APPLE, "de,en")).isEqualTo(new NotFound(NotFoundReason.ZERO_HITS));
    assertThat(transport.getRequests()).hasSize(1);

    FakeHttpTransport none = transport(recorded("search-one-hit.json"), recorded("entities-one-hit.json"));
    assertThat(resolve(none, null, "de,en")).isEqualTo(new NotFound(NotFoundReason.NO_ISIN));
    assertThat(none.getRequests()).isEmpty();
  }

  @Test
  @DisplayName("No article in any requested language: no sitelink")
  void missingLanguage() {
    assertThat(
        resolve(transport(recorded("search-one-hit.json"), recorded("entities-missing-language.json")), APPLE, "de,en"))
            .isEqualTo(new NotFound(NotFoundReason.NO_SITELINK));
  }

  @Test
  @DisplayName("A maxlag error object with status 200 is retryable")
  void maxlagIsRetryable() {
    assertThat(resolve(transport(recorded("error-maxlag.json"), recorded("entities-one-hit.json")), APPLE, "de,en"))
        .isInstanceOf(Retryable.class);
  }

  @Test
  @DisplayName("429 waits for Retry-After, at most 60 s, and is then retryable")
  void tooManyRequests() {
    FakeHttpTransport transport = new FakeHttpTransport(_ -> new Answer(429, "text/html", null, "120", new byte[0]));

    assertThat(resolve(transport, APPLE, "de,en")).isInstanceOf(Retryable.class);
    assertThat(sleeps).contains(60_000L);
    assertThat(transport.getRequests()).hasSize(1);
  }

  @Test
  @DisplayName("A status outside 2xx, a body that is not JSON and a transport failure are retryable")
  void otherFailuresAreRetryable() {
    assertThat(resolve(new FakeHttpTransport(_ -> Answer.status(503)), APPLE, "de")).isInstanceOf(Retryable.class);
    assertThat(resolve(new FakeHttpTransport(_ -> Answer.json("<html>")), APPLE, "de")).isInstanceOf(Retryable.class);
    assertThat(resolve(new FakeHttpTransport(_ -> null), APPLE, "de")).isInstanceOf(Retryable.class);
  }

  @Test
  @DisplayName("The language list is normalised to lower case without duplicates and refused when invalid")
  void normalizeArgument() {
    WikipediaUDFValueResolver resolver = new WikipediaUDFValueResolver(null, _ -> {
    });
    assertThat(resolver.normalizeArgument(" DE, en ,de,zh-yue")).isEqualTo("de,en,zh-yue");
    assertThat(resolver.normalizeArgument("")).isEqualTo(WikipediaUDFValueResolver.DEFAULT_ARGUMENT);
    for (String invalid : List.of("de,,en", "deutsch", "de,en,fr,it,es,pt", "d1")) {
      assertThatThrownBy(() -> resolver.normalizeArgument(invalid)).isInstanceOf(IllegalArgumentException.class)
          .hasMessage(WikipediaUDFValueResolver.INVALID_ARGUMENT_KEY);
    }
  }
}
