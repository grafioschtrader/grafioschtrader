package grafiosch.test.gtnet;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Client-only support for the two-peer GTNet suites: these tests never start a Spring context of their own, they
 * address the two peer processes the runner already started, over HTTP, exactly as the browser specs do.
 *
 * Both peer addresses come from the environment, so the same helper serves the library peers (8081/8082 against
 * grafiosch_t / grafiosch_t1) and the application peers (8080/8082 against grafioschtrader_t / grafioschtrader_t1).
 * Nothing here is application specific; the defaults are the library ports.
 */
public final class GTNetPeerTestSupport {

  public static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
  public static final ObjectMapper JSON = new ObjectMapper();
  public static final URI PEER_A = URI.create(env("GTNET_PEER_A_BACKEND_URL", "http://localhost:8081"));
  public static final URI PEER_B = URI.create(env("GTNET_PEER_B_BACKEND_URL", "http://localhost:8082"));

  private GTNetPeerTestSupport() {
  }

  public static HttpResponse<String> get(URI peer, String path) throws IOException, InterruptedException {
    return HTTP.send(HttpRequest.newBuilder(peer.resolve(path)).timeout(Duration.ofSeconds(15)).GET().build(),
        HttpResponse.BodyHandlers.ofString());
  }

  public static HttpResponse<String> postJson(URI peer, String path, String token, String json)
      throws IOException, InterruptedException {
    HttpRequest.Builder request = HttpRequest.newBuilder(peer.resolve(path)).timeout(Duration.ofSeconds(15))
        .header("Content-Type", "application/json");
    if (token != null) {
      request.header("Authorization", token);
    }
    return HTTP.send(request.POST(HttpRequest.BodyPublishers.ofString(json)).build(),
        HttpResponse.BodyHandlers.ofString());
  }

  public static String loginAdmin(URI peer) throws IOException, InterruptedException {
    return login(peer, "admin@test.local");
  }

  public static String login(URI peer, String email) throws IOException, InterruptedException {
    HttpResponse<String> response = postApi(peer, "/api/login", null,
        "{\"email\":\"" + email + "\",\"password\":\"A123abcd\",\"timezoneOffset\":0}");
    if (response.statusCode() != 200) {
      throw new IllegalStateException("Admin login failed at " + peer + ": " + response.body());
    }
    return response.headers().firstValue("x-auth-token").orElseThrow();
  }

  public static HttpResponse<String> getApi(URI peer, String path, String jwt)
      throws IOException, InterruptedException {
    return HTTP.send(HttpRequest.newBuilder(peer.resolve(path)).timeout(Duration.ofSeconds(15))
        .header("x-auth-token", jwt).GET().build(), HttpResponse.BodyHandlers.ofString());
  }

  public static HttpResponse<String> postApi(URI peer, String path, String jwt, String json)
      throws IOException, InterruptedException {
    HttpRequest.Builder request = HttpRequest.newBuilder(peer.resolve(path)).timeout(Duration.ofSeconds(30))
        .header("Content-Type", "application/json");
    if (jwt != null) {
      request.header("x-auth-token", jwt);
    }
    return HTTP.send(request.POST(HttpRequest.BodyPublishers.ofString(json)).build(),
        HttpResponse.BodyHandlers.ofString());
  }

  public static HttpResponse<String> putApi(URI peer, String path, String jwt, String json)
      throws IOException, InterruptedException {
    HttpRequest.Builder request = HttpRequest.newBuilder(peer.resolve(path)).timeout(Duration.ofSeconds(30))
        .header("Content-Type", "application/json");
    if (jwt != null) {
      request.header("x-auth-token", jwt);
    }
    return HTTP.send(request.PUT(HttpRequest.BodyPublishers.ofString(json)).build(),
        HttpResponse.BodyHandlers.ofString());
  }

  public static HttpResponse<String> deleteApi(URI peer, String path, String jwt)
      throws IOException, InterruptedException {
    return HTTP.send(HttpRequest.newBuilder(peer.resolve(path)).timeout(Duration.ofSeconds(15))
        .header("x-auth-token", jwt).DELETE().build(), HttpResponse.BodyHandlers.ofString());
  }

  public static JsonNode readGTNet(URI peer, String jwt) throws IOException, InterruptedException {
    HttpResponse<String> response = getApi(peer, "/api/gtnet/gtnetwithmessage", jwt);
    if (response.statusCode() != 200) {
      throw new IllegalStateException("Reading GTNet failed at " + peer + ": " + response.body());
    }
    return JSON.readTree(response.body());
  }

  public static int remoteId(JsonNode state, String remoteDomain) {
    for (JsonNode entry : state.path("gtNetList")) {
      if (remoteDomain.equals(entry.path("domainRemoteName").asText())) {
        return entry.path("idGtNet").asInt();
      }
    }
    throw new IllegalStateException("Remote domain not found: " + remoteDomain);
  }

  public static HttpResponse<String> submit(URI peer, String jwt, Integer targetId, String code,
      Map<String, String> parameters, String message) throws IOException, InterruptedException {
    var request = JSON.createObjectNode();
    if (targetId != null) {
      request.put("idGTNetTargetDomain", targetId);
    }
    request.put("messageCode", code);
    request.put("message", message);
    var parameterMap = request.putObject("gtNetMessageParamMap");
    parameters.forEach((name, value) -> parameterMap.putObject(name).put("paramValue", value));
    return postApi(peer, "/api/gtnet/submitmsg", jwt, request.toString());
  }

  private static String env(String name, String defaultValue) {
    return System.getenv().getOrDefault(name, defaultValue);
  }
}
