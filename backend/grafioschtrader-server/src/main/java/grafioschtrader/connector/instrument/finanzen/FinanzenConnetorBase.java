package grafioschtrader.connector.instrument.finanzen;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.EnumSet;
import java.util.Map;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import grafioschtrader.GlobalConstants;
import grafioschtrader.connector.instrument.BaseFeedConnector;
import tools.jackson.databind.json.JsonMapper;

public abstract class FinanzenConnetorBase extends BaseFeedConnector {

  protected static final String currencyIntraPrefix = "devisen/";
  private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();
  private static final Pattern CHALLENGE_NUMBERS = Pattern.compile(
      "\\bvar\\s+i\\s*=\\s*(\\d+)\\s*;\\s*var\\s+j\\s*=\\s*i\\s*\\+\\s*Number\\(\\s*\"(\\d+)\"\\s*\\+\\s*\"(\\d+)\"\\s*\\)");
  private static final Pattern CHALLENGE_TOKEN = Pattern.compile("\"bm-verify\"\\s*:\\s*\"([A-Za-z0-9+/=_-]+)\"");
  private CookieManager cookieManager;
  private HttpClient client;

  private final Logger log = LoggerFactory.getLogger(this.getClass());

  public FinanzenConnetorBase(Map<FeedSupport, FeedIdentifier[]> supportedFeed, String id, String readableName,
      String regexStrPattern, EnumSet<UrlCheck> urlCheckSet) {
    super(supportedFeed, id, readableName, regexStrPattern, urlCheckSet);
  }

  protected void initalizeHttpClient(String domain) {
    this.cookieManager = new CookieManager();
    cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
    this.client = HttpClient.newBuilder().followRedirects(Redirect.NORMAL).cookieHandler(this.cookieManager)
        .connectTimeout(Duration.ofSeconds(30)).build();
    try {
      getDoc(domain);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.error("A connection to {} was interrupted.", domain, e);
    } catch (IOException e) {
      log.error("A connection to {} is not possible.", domain, e);
    }
  }

  protected Document getDoc(String url) throws IOException, InterruptedException {
    HttpClient client = getHttpClient();
    HttpRequest request = getRequest(url);
    HttpResponse<String> response = sendChecked(client, request);
    if (isVerificationPage(response.body())) {
      verifySession(client, response);
      response = sendChecked(client, request);
      if (isVerificationPage(response.body())) {
        throw new IOException("Finanzen verification still required after retry: " + response.uri());
      }
    }
    return Jsoup.parse(response.body(), response.uri().toString());
  }

  private HttpResponse<String> sendChecked(HttpClient client, HttpRequest request)
      throws IOException, InterruptedException {
    HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
    if (response.statusCode() != 200) {
      throw new IOException("Finanzen HTTP " + response.statusCode() + ": " + response.uri());
    }
    return response;
  }

  private boolean isVerificationPage(String html) {
    return html.contains("triggerInterstitialChallenge") || html.contains("id=\"akam-logo\"");
  }

  /**
   * Finanzen can return an Akamai interstitial with HTTP 200 instead of prices. Handle its known arithmetic challenge
   * using the same cookie session, without executing remote JavaScript. Unknown challenges fail explicitly.
   */
  private void verifySession(HttpClient client, HttpResponse<String> response)
      throws IOException, InterruptedException {
    var numbers = CHALLENGE_NUMBERS.matcher(response.body());
    var token = CHALLENGE_TOKEN.matcher(response.body());
    if (!numbers.find() || !token.find()) {
      throw new IOException("Unsupported Finanzen verification challenge: " + response.uri());
    }
    long proof;
    try {
      proof = Math.addExact(Long.parseLong(numbers.group(1)), Long.parseLong(numbers.group(2) + numbers.group(3)));
    } catch (IllegalArgumentException | ArithmeticException e) {
      throw new IOException("Invalid Finanzen verification challenge: " + response.uri(), e);
    }
    String body = JSON_MAPPER.writeValueAsString(Map.of("bm-verify", token.group(1), "pow", proof));
    String url = response.uri().resolve("/_sec/verify?provider=interstitial").toString();
    HttpRequest verification = HttpRequest.newBuilder(getRequest(url), (_, _) -> true)
        .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build();
    HttpResponse<String> result = sendChecked(client, verification);
    if (!JSON_MAPPER.readTree(result.body()).path("reload").asBoolean(false)) {
      throw new IOException("Finanzen verification did not authorize a reload: " + response.uri());
    }
  }

  @Override
  protected HttpRequest getRequest(String url) {
    return HttpRequest.newBuilder().timeout(Duration.ofSeconds(30)).header("Accept", "*/*")
        .header("Accept-Encoding", "deflate, br").header("Accept-Language", "de-CH,de;q=0.9,en;q=0.8")
        .header("Upgrade-Insecure-Requests", "1").header("Sec-Fetch-Dest", "document")
        .header("Sec-Fetch-Mode", "navigate").header("Sec-Fetch-Site", "none")
        .header("User-Agent", GlobalConstants.USER_AGENT_HTTPCLIENT).uri(URI.create(url)).build();
  }

  @Override
  protected HttpClient getHttpClient() {
    return this.client;
  }
}
