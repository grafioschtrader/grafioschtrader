package grafioschtrader.udffill.resolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafiosch.exceptions.DataViolationException;
import grafiosch.types.UDFDataType;
import grafioschtrader.dto.UDFFillArgumentOption;
import grafioschtrader.entities.Security;
import grafioschtrader.udffill.http.FakeHttpTransport;
import grafioschtrader.udffill.http.FakeHttpTransport.Answer;
import grafioschtrader.udffill.http.UDFFillHttp;
import grafioschtrader.udffill.resolver.ResolveOutcome.Found;
import grafioschtrader.udffill.resolver.ResolveOutcome.NotFound;
import grafioschtrader.udffill.resolver.ResolveOutcome.NotFoundReason;
import grafioschtrader.udffill.resolver.ResolveOutcome.Retryable;

/**
 * The justETF resolver against a recorded profile page under {@code udffill/justetf/}. The real fetcher runs with an
 * injected DNS lookup and a fake transport; no test opens a socket.
 */
class JustEtfUDFValueResolverTest {

  private static final String ISIN = "IE00B1XNHC34";
  private static final String PROFILE = "https://www.justetf.com/de/etf-profile.html?isin=" + ISIN;

  private final List<Long> sleeps = new ArrayList<>();

  private static String recorded(String name) {
    try (InputStream in = JustEtfUDFValueResolverTest.class.getResourceAsStream("/udffill/justetf/" + name)) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private static Answer html(String body) {
    return new Answer(200, "text/html; charset=utf-8", null, null, body.getBytes(StandardCharsets.UTF_8));
  }

  private JustEtfUDFValueResolver resolver(FakeHttpTransport transport) {
    UDFFillHttp http = new UDFFillHttp(_ -> new InetAddress[] { InetAddress.ofLiteral("188.114.97.0") }, transport,
        UDFFillHttp.userAgent("test"));
    return new JustEtfUDFValueResolver(http, sleeps::add);
  }

  private static Security security(String isin) {
    return new Security("iShares Global Clean Energy", "USD", null, null, null, null, null, null, isin);
  }

  private static FakeHttpTransport profile(String body) {
    return new FakeHttpTransport(_ -> html(body));
  }

  @Test
  @DisplayName("TER and fund size of the recorded profile: 0.65 percent and EUR 2.472 million")
  void recordedProfile() {
    FakeHttpTransport transport = profile(recorded("profile-ishares-global-clean-energy.html"));
    JustEtfUDFValueResolver resolver = resolver(transport);

    assertThat(resolver.resolve(security(ISIN), JustEtfUDFValueResolver.TER)).isEqualTo(new Found("0.65", PROFILE));
    assertThat(resolver.resolve(security(ISIN), JustEtfUDFValueResolver.FUND_SIZE))
        .isEqualTo(new Found("2472", PROFILE));
  }

  @Test
  @DisplayName("Two fields over the same ISIN cost one request")
  void profileIsCached() {
    FakeHttpTransport transport = profile(recorded("profile-ishares-global-clean-energy.html"));
    JustEtfUDFValueResolver resolver = resolver(transport);

    resolver.resolve(security(ISIN), JustEtfUDFValueResolver.TER);
    resolver.resolve(security(ISIN), JustEtfUDFValueResolver.FUND_SIZE);

    assertThat(transport.getRequests()).hasSize(1);
    assertThat(transport.getRequests().getFirst().uri().toString()).isEqualTo(PROFILE);
  }

  @Test
  @DisplayName("Fund size units: Mrd. is a thousand millions, a size with decimals keeps them")
  void fundSizeUnits() {
    String page = recorded("profile-ishares-global-clean-energy.html");
    assertThat(resolve(page.replace("EUR 2.472 Mio.", "EUR 1,5 Mrd."), JustEtfUDFValueResolver.FUND_SIZE))
        .isEqualTo(new Found("1500", PROFILE));
    assertThat(resolve(page.replace("EUR 2.472 Mio.", "USD 130.304 Mio."), JustEtfUDFValueResolver.FUND_SIZE))
        .isEqualTo(new Found("130304", PROFILE));
    assertThat(resolve(page.replace("EUR 2.472 Mio.", "EUR 28,5 Mio."), JustEtfUDFValueResolver.FUND_SIZE))
        .isEqualTo(new Found("28.5", PROFILE));
  }

  @Test
  @DisplayName("A figure the page shows as n/a is not found, the other figure is still read")
  void missingFigure() {
    String page = recorded("profile-ishares-global-clean-energy.html").replace("0,65% p.a.", "n/a");

    assertThat(resolve(page, JustEtfUDFValueResolver.TER)).isEqualTo(new NotFound(NotFoundReason.NO_VALUE));
    assertThat(resolve(page, JustEtfUDFValueResolver.FUND_SIZE)).isEqualTo(new Found("2472", PROFILE));
  }

  @Test
  @DisplayName("ACTIVE: the recorded active ETF is true, the index ETF false, a page without concept says nothing")
  void activeFigure() {
    assertThat(resolve(recorded("profile-jpm-active.html"), JustEtfUDFValueResolver.ACTIVE, "IE0003UVYC20"))
        .isEqualTo(new Found("true", "https://www.justetf.com/de/etf-profile.html?isin=IE0003UVYC20"));
    String indexEtf = recorded("profile-ishares-global-clean-energy.html");
    assertThat(resolve(indexEtf, JustEtfUDFValueResolver.ACTIVE, ISIN)).isEqualTo(new Found("false", PROFILE));
    String noConcept = indexEtf.replace("Long-only", "").replace("tl_etf-basics_value_strategy-risk", "other");
    assertThat(resolve(noConcept, JustEtfUDFValueResolver.ACTIVE, ISIN))
        .isEqualTo(new NotFound(NotFoundReason.NO_VALUE));
  }

  @Test
  @DisplayName("ACTIVE needs a boolean field, TER and FUND_SIZE a decimal field")
  void dataTypePerFigure() {
    JustEtfUDFValueResolver resolver = resolver(new FakeHttpTransport(_ -> null));

    assertThat(resolver.normalizeArgument("active")).isEqualTo("ACTIVE");
    assertThat(resolver.supportsDataType(UDFDataType.UDF_Boolean, "ACTIVE")).isTrue();
    assertThat(resolver.supportsDataType(UDFDataType.UDF_Numeric, "ACTIVE")).isFalse();
    assertThat(resolver.supportsDataType(UDFDataType.UDF_Numeric, "TER")).isTrue();
    assertThat(resolver.supportsDataType(UDFDataType.UDF_Numeric, "FUND_SIZE")).isTrue();
    assertThat(resolver.supportsDataType(UDFDataType.UDF_Boolean, "TER")).isFalse();
    assertThat(resolver.supportsDataType(UDFDataType.UDF_String, "TER")).isFalse();
    assertThat(resolver.getArgumentOptions()).extracting(UDFFillArgumentOption::argument)
        .containsExactly("TER", "FUND_SIZE", "ACTIVE");
  }

  @Test
  @DisplayName("Saving a rule checks the figure against the data type of the field")
  void ruleIsCheckedAgainstTheFieldType() {
    UDFValueResolverRegistry registry = new UDFValueResolverRegistry(
        List.of(resolver(new FakeHttpTransport(_ -> null))));

    assertThat(registry.normalizeRule("JUSTETF active", UDFDataType.UDF_Boolean)).isEqualTo("JUSTETF ACTIVE");
    assertThat(registry.normalizeRule("JUSTETF", UDFDataType.UDF_Numeric)).isEqualTo("JUSTETF TER");
    assertThatThrownBy(() -> registry.normalizeRule("JUSTETF ACTIVE", UDFDataType.UDF_Numeric))
        .isInstanceOf(DataViolationException.class);
    assertThatThrownBy(() -> registry.normalizeRule("JUSTETF TER", UDFDataType.UDF_Boolean))
        .isInstanceOf(DataViolationException.class);
    assertThatThrownBy(() -> registry.normalizeRule("JUSTETF AUM", UDFDataType.UDF_Numeric))
        .isInstanceOf(DataViolationException.class);
  }

  @Test
  @DisplayName("An unknown ISIN is redirected to the search page: not found")
  void unknownIsin() {
    FakeHttpTransport transport = new FakeHttpTransport(uri -> uri.getPath().endsWith("search.html")
        ? html(recorded("search.html"))
        : Answer.redirect("/de/search.html?query=" + ISIN + "&search=ETFS"));

    assertThat(resolver(transport).resolve(security(ISIN), JustEtfUDFValueResolver.TER))
        .isEqualTo(new NotFound(NotFoundReason.ZERO_HITS));
  }

  @Test
  @DisplayName("A profile page that does not carry the ISIN is not trusted")
  void pageWithoutIsin() {
    String page = recorded("profile-ishares-global-clean-energy.html").replace(ISIN, "IE0000000000");

    assertThat(resolve(page, JustEtfUDFValueResolver.TER)).isEqualTo(new NotFound(NotFoundReason.ZERO_HITS));
  }

  @Test
  @DisplayName("No ISIN asks nothing at all")
  void noIsin() {
    FakeHttpTransport transport = profile(recorded("profile-ishares-global-clean-energy.html"));

    assertThat(resolver(transport).resolve(security(null), JustEtfUDFValueResolver.TER))
        .isEqualTo(new NotFound(NotFoundReason.NO_ISIN));
    assertThat(transport.getRequests()).isEmpty();
  }

  @Test
  @DisplayName("A server error and a refused connection are retryable and not cached; 429 waits for Retry-After")
  void failuresAreRetryable() {
    FakeHttpTransport error = new FakeHttpTransport(_ -> Answer.status(503));
    JustEtfUDFValueResolver resolver = resolver(error);
    assertThat(resolver.resolve(security(ISIN), JustEtfUDFValueResolver.TER)).isInstanceOf(Retryable.class);
    assertThat(resolver.resolve(security(ISIN), JustEtfUDFValueResolver.TER)).isInstanceOf(Retryable.class);
    assertThat(error.getRequests()).hasSize(2);

    FakeHttpTransport refused = new FakeHttpTransport(_ -> null);
    assertThat(resolver(refused).resolve(security(ISIN), JustEtfUDFValueResolver.TER)).isInstanceOf(Retryable.class);

    sleeps.clear();
    FakeHttpTransport limited = new FakeHttpTransport(
        _ -> new Answer(429, "text/html", null, "7", new byte[0]));
    assertThat(resolver(limited).resolve(security(ISIN), JustEtfUDFValueResolver.TER)).isInstanceOf(Retryable.class);
    assertThat(sleeps).containsExactly(7000L);
  }

  @Test
  @DisplayName("The argument is the figure: blank is the TER, case and a space are forgiven, anything else is refused")
  void argument() {
    JustEtfUDFValueResolver resolver = resolver(new FakeHttpTransport(_ -> null));

    assertThat(resolver.normalizeArgument(null)).isEqualTo("TER");
    assertThat(resolver.normalizeArgument(" ter ")).isEqualTo("TER");
    assertThat(resolver.normalizeArgument("fund size")).isEqualTo("FUND_SIZE");
    assertThatThrownBy(() -> resolver.normalizeArgument("AUM")).isInstanceOf(IllegalArgumentException.class)
        .hasMessage(JustEtfUDFValueResolver.INVALID_ARGUMENT_KEY);
  }

  private ResolveOutcome resolve(String page, String figure) {
    return resolve(page, figure, ISIN);
  }

  private ResolveOutcome resolve(String page, String figure, String isin) {
    return resolver(profile(page)).resolve(security(isin), figure);
  }
}
