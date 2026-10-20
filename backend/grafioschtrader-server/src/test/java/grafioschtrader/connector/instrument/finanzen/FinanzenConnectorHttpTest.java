package grafioschtrader.connector.instrument.finanzen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import grafioschtrader.connector.instrument.IFeedConnector.UrlCheck;
import tools.jackson.databind.json.JsonMapper;

/** Exercises the shared Finanzen transport against a recorded interstitial without a browser or live provider. */
class FinanzenConnectorHttpTest {
  private HttpServer server;
  private FinanzenConnetorBase connector;
  private String endpoint;
  private String challenge;
  private String verificationReply = "{\"reload\":true}";
  private boolean keepChallenging;
  private int quoteStatus = 200;
  private final AtomicInteger quoteRequests = new AtomicInteger();
  private final AtomicInteger verificationRequests = new AtomicInteger();

  @BeforeEach
  void startServer() throws IOException {
    try (var input = getClass().getResourceAsStream("/finanzen/akamai-interstitial.html")) {
      challenge = new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", exchange -> {
      exchange.getResponseHeaders().add("Set-Cookie", "session=fixture; Path=/");
      respond(exchange, 200, "<html>Finanzen</html>");
    });
    server.createContext("/quotes", exchange -> {
      quoteRequests.incrementAndGet();
      String cookies = exchange.getRequestHeaders().getFirst("Cookie");
      boolean verified = cookies != null && cookies.contains("verified=yes") && !keepChallenging;
      respond(exchange, quoteStatus,
          verified ? "<div class=\"snapshot__values\">123,45 EUR -1,00 -0,80 %</div>" : challenge);
    });
    server.createContext("/_sec/verify", exchange -> {
      verificationRequests.incrementAndGet();
      var payload = JsonMapper.builder().build().readTree(exchange.getRequestBody());
      String cookies = exchange.getRequestHeaders().getFirst("Cookie");
      boolean valid = "POST".equals(exchange.getRequestMethod())
          && "provider=interstitial".equals(exchange.getRequestURI().getQuery())
          && "application/json".equals(exchange.getRequestHeaders().getFirst("Content-Type")) && cookies != null
          && cookies.contains("session=fixture") && payload.path("bm-verify").asString().equals("fixture/token+value==")
          && payload.path("pow").asLong() == 2251356569L;
      if (valid) {
        exchange.getResponseHeaders().add("Set-Cookie", "verified=yes; Path=/");
      }
      respond(exchange, valid ? 200 : 400, verificationReply);
    });
    server.start();
    endpoint = "http://127.0.0.1:" + server.getAddress().getPort();
    connector = new FinanzenConnetorBase(Map.of(), "fixture", "fixture", null, EnumSet.noneOf(UrlCheck.class)) {
    };
    connector.initalizeHttpClient(endpoint);
  }

  @AfterEach
  void stopServer() {
    server.stop(0);
  }

  @Test
  @DisplayName("Complete the recorded challenge, retain cookies and return actual quotes")
  void verifiesAndReusesSession() throws Exception {
    assertThat(connector.getDoc(endpoint + "/quotes").select("div.snapshot__values").text())
        .isEqualTo("123,45 EUR -1,00 -0,80 %");
    connector.getDoc(endpoint + "/quotes");
    assertThat(quoteRequests.get()).isEqualTo(3);
    assertThat(verificationRequests.get()).isEqualTo(1);
  }

  @Test
  @DisplayName("Stop after one verification retry if the provider still returns its interstitial")
  void boundsRetries() {
    keepChallenging = true;
    assertThatThrownBy(() -> connector.getDoc(endpoint + "/quotes")).isInstanceOf(IOException.class)
        .hasMessageContaining("still required after retry");
    assertThat(quoteRequests.get()).isEqualTo(2);
    assertThat(verificationRequests.get()).isEqualTo(1);
  }

  @Test
  @DisplayName("Reject unknown JavaScript rather than evaluating it or returning it as a quote page")
  void rejectsUnknownChallenge() {
    challenge = challenge.replace("var j = i + Number", "var j = unknownFunction");
    assertThatThrownBy(() -> connector.getDoc(endpoint + "/quotes")).isInstanceOf(IOException.class)
        .hasMessageContaining("Unsupported Finanzen verification");
    assertThat(verificationRequests.get()).isZero();
  }

  @Test
  @DisplayName("Do not retry quotes when verification is refused")
  void rejectsRefusedVerification() {
    verificationReply = "{\"reload\":false}";
    assertThatThrownBy(() -> connector.getDoc(endpoint + "/quotes")).isInstanceOf(IOException.class)
        .hasMessageContaining("did not authorize a reload");
    assertThat(quoteRequests.get()).isEqualTo(1);
  }

  @Test
  @DisplayName("Report HTTP errors before trying to parse prices or verify a session")
  void rejectsHttpErrors() {
    quoteStatus = 403;
    assertThatThrownBy(() -> connector.getDoc(endpoint + "/quotes")).isInstanceOf(IOException.class)
        .hasMessageContaining("HTTP 403");
    assertThat(verificationRequests.get()).isZero();
  }

  private static void respond(HttpExchange exchange, int status, String body) throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.sendResponseHeaders(status, bytes.length);
    try (var output = exchange.getResponseBody()) {
      output.write(bytes);
    }
  }
}
