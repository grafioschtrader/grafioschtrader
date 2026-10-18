package grafioschtrader.udffill.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import grafioschtrader.udffill.http.FakeHttpTransport.Answer;
import grafioschtrader.udffill.http.UDFFillHttp.FetchResult;

/**
 * The checks of the public source fetcher, with the DNS lookup and the transport injected. No test opens a socket.
 */
class UDFFillHttpTest {

  private static final String PUBLIC_IP = "93.184.215.14";

  /** Host names of the test and their addresses; every other name does not resolve. */
  private static final Map<String, String[]> DNS = Map.of("public.example", new String[] { PUBLIC_IP }, "mixed.example",
      new String[] { PUBLIC_IP, "10.1.2.3" }, "intranet.example", new String[] { "192.168.1.20" }, "metadata.example",
      new String[] { "169.254.169.254" }, "ula.example", new String[] { "fd00::1" });

  private static InetAddress[] lookup(String host) throws UnknownHostException {
    String[] literals = DNS.get(host);
    if (literals == null) {
      throw new UnknownHostException(host);
    }
    InetAddress[] addresses = new InetAddress[literals.length];
    for (int i = 0; i < literals.length; i++) {
      addresses[i] = InetAddress.ofLiteral(literals[i]);
    }
    return addresses;
  }

  private static UDFFillHttp http(FakeHttpTransport transport) {
    return new UDFFillHttp(UDFFillHttpTest::lookup, transport, UDFFillHttp.userAgent("test"));
  }

  @Test
  @DisplayName("A public https URL is fetched, connecting only to the checked addresses")
  void fetchesPublicUrl() throws Exception {
    FakeHttpTransport transport = new FakeHttpTransport(_ -> Answer.json("{\"ok\":true}"));

    FetchResult result = http(transport).fetch("https://public.example/api?x=1");

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.body()).isEqualTo("{\"ok\":true}");
    assertThat(result.contentType()).isEqualTo("application/json");
    assertThat(transport.getRequests()).singleElement()
        .satisfies(r -> assertThat(r.addresses()).containsExactly(InetAddress.ofLiteral(PUBLIC_IP)));
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = { "http://public.example/", "https://public.example:8443/", "https://user@public.example/",
      "https://user:secret@public.example/", "https://127.0.0.1/", "https://[::1]/", "https://[::ffff:10.0.0.1]/",
      "https://[::10.0.0.1]/", "https://0.0.0.0/", "https://mixed.example/", "https://intranet.example/",
      "https://metadata.example/latest/meta-data/", "https://ula.example/", "https://224.0.0.1/",
      "ftp://public.example/" })
  @DisplayName("A URL is refused before any request when scheme, port, user info or an address is not allowed")
  void refusesBeforeRequest(String url) {
    FakeHttpTransport transport = new FakeHttpTransport(_ -> Answer.json("{}"));

    assertThatThrownBy(() -> http(transport).fetch(url)).isInstanceOfSatisfying(UDFFillHttpException.class,
        e -> assertThat(e.isRefused()).isTrue());
    assertThat(transport.getRequests()).isEmpty();
  }

  @Test
  @DisplayName("A redirect to a private address is refused before the second request")
  void refusesRedirectToPrivateAddress() {
    FakeHttpTransport transport = new FakeHttpTransport(
        uri -> uri.getHost().equals("public.example") ? Answer.redirect("https://intranet.example/admin") : null);

    assertThatThrownBy(() -> http(transport).fetch("https://public.example/"))
        .isInstanceOfSatisfying(UDFFillHttpException.class, e -> assertThat(e.isRefused()).isTrue());
    assertThat(transport.getRequests()).hasSize(1);
  }

  @Test
  @DisplayName("A redirect to http is refused by the scheme check")
  void refusesRedirectToHttp() {
    FakeHttpTransport transport = new FakeHttpTransport(_ -> Answer.redirect("http://public.example/plain"));

    assertThatThrownBy(() -> http(transport).fetch("https://public.example/")).isInstanceOf(UDFFillHttpException.class);
    assertThat(transport.getRequests()).hasSize(1);
  }

  @Test
  @DisplayName("Three relative redirects are followed, a fourth is refused")
  void followsAtMostThreeRedirects() throws Exception {
    FakeHttpTransport three = new FakeHttpTransport(uri -> switch (uri.getPath()) {
    case "/0" -> Answer.redirect("/1");
    case "/1" -> Answer.redirect("/2");
    case "/2" -> Answer.redirect("/3");
    default -> Answer.json("{}");
    });
    FetchResult result = http(three).fetch("https://public.example/0");
    assertThat(result.finalUrl()).isEqualTo("https://public.example/3");
    assertThat(three.getRequests()).hasSize(4);

    FakeHttpTransport endless = new FakeHttpTransport(_ -> Answer.redirect("/again"));
    assertThatThrownBy(() -> http(endless).fetch("https://public.example/0")).isInstanceOf(UDFFillHttpException.class);
    assertThat(endless.getRequests()).hasSize(4);
  }

  @Test
  @DisplayName("A body over 1 MB after decompression is refused")
  void refusesLargeBody() {
    byte[] body = new byte[UDFFillHttp.MAX_BODY_BYTES + 1];
    FakeHttpTransport transport = new FakeHttpTransport(_ -> new Answer(200, "text/plain", null, null, body));

    assertThatThrownBy(() -> http(transport).fetch("https://public.example/big"))
        .isInstanceOfSatisfying(UDFFillHttpException.class, e -> assertThat(e.isRefused()).isTrue());
  }

  @Test
  @DisplayName("A content type other than html, plain text or JSON is refused")
  void refusesContentType() {
    FakeHttpTransport transport = new FakeHttpTransport(
        _ -> new Answer(200, "application/octet-stream", null, null, new byte[] { 1, 2, 3 }));

    assertThatThrownBy(() -> http(transport).fetch("https://public.example/file"))
        .isInstanceOfSatisfying(UDFFillHttpException.class, e -> assertThat(e.isRefused()).isTrue());
  }

  @Test
  @DisplayName("A status outside 2xx is returned with its Retry-After and without body, a host failure is technical")
  void statusAndTransportFailure() throws Exception {
    FakeHttpTransport tooMany = new FakeHttpTransport(_ -> new Answer(429, "text/html", null, "30", new byte[0]));
    FetchResult result = http(tooMany).fetch("https://public.example/");
    assertThat(result.status()).isEqualTo(429);
    assertThat(result.retryAfter()).isEqualTo("30");
    assertThat(result.body()).isNull();

    assertThatThrownBy(() -> http(tooMany).fetch("https://unknown.example/"))
        .isInstanceOfSatisfying(UDFFillHttpException.class, e -> assertThat(e.isRefused()).isFalse());
  }
}
