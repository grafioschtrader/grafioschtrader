package grafioschtrader.mcp.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

import grafiosch.entities.UserAccessToken;
import grafioschtrader.mcp.McpTestSwitch;
import grafioschtrader.rest.BaseIntegrationTest;
import grafioschtrader.rest.RestTestHelper;
import jakarta.persistence.EntityManagerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The OAuth authorization of the MCP server over its real HTTP path, as a connector such as ChatGPT runs it: discovery
 * from the 401 of the MCP endpoint, the metadata documents under a path prefix, validation, consent, code exchange with
 * PKCE and resource, refresh with rotation, and the refusals the specification requires - unknown client and redirect
 * without a redirect, code and refresh replay, a replay revoking the family, the end of a family and the revocation in
 * the token dialog.
 *
 * <p>
 * The class opens the server through the global parameters for its run, with a public base URL that carries the prefix
 * {@code /gt}, so the well-known locations differ from a plain concatenation. The client's metadata document comes from
 * a mock, because the real fetch accepts public HTTPS addresses only. The class removes the families it creates and
 * runs after {@code McpServerIntegrationTest}.
 * </p>
 */
@TestMethodOrder(OrderAnnotation.class)
@TestInstance(Lifecycle.PER_CLASS)
public class McpOAuthIntegrationTest extends BaseIntegrationTest {

  private static final String ISSUER = "https://host.example/gt";
  private static final String RESOURCE = ISSUER + "/api/mcp";
  private static final String CLIENT_ID = "https://client.example/oauth/metadata.json";
  private static final String REDIRECT_URI = "https://client.example/connector_platform_oauth_redirect";
  private static final JsonMapper JSON = JsonMapper.builder().build();

  @MockitoBean
  private ClientMetadataFetcher clientMetadataFetcher;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Autowired
  private EntityManagerFactory entityManagerFactory;

  private Integer idUser;
  private JsonNode tokens;

  @BeforeAll
  void setUp() throws Exception {
    RestTestHelper.inizializeUserTokens(restTestClient, jwtTokenHandler);
    idUser = RestTestHelper.getUserByNickname(RestTestHelper.ALLEDIT).idUser;
    jdbcTemplate.update("DELETE FROM oauth_authorization WHERE id_user = ?", idUser);
    McpTestSwitch.open(jdbcTemplate, entityManagerFactory, ISSUER);
  }

  @AfterAll
  void tearDown() {
    McpTestSwitch.close(jdbcTemplate, entityManagerFactory);
    jdbcTemplate.update("DELETE FROM oauth_authorization WHERE id_user = ?", idUser);
  }

  /** A {@code @MockitoBean} is reset after every test, so the document is served anew before each. */
  @BeforeEach
  void serveClientDocument() throws Exception {
    // Any other client_id gets null from the mock, which the service refuses as a missing document
    when(clientMetadataFetcher.fetch(CLIENT_ID)).thenReturn("""
        {"client_id":"%s","client_name":"Spike Assistant","redirect_uris":["%s"],"token_endpoint_auth_method":"none"}"""
        .formatted(CLIENT_ID, REDIRECT_URI));
  }

  @Test
  @Order(1)
  @DisplayName("Discovery: the 401 names the resource metadata, both documents live outside the /gt prefix")
  void discovery() throws Exception {
    String header = restTestClient.post().uri("/api/mcp").contentType(MediaType.APPLICATION_JSON).body("{}").exchange()
        .expectStatus().isUnauthorized().returnResult(String.class).getResponseHeaders()
        .getFirst(HttpHeaders.WWW_AUTHENTICATE);
    assertThat(header).isEqualTo("Bearer resource_metadata=\"https://host.example/.well-known/"
        + "oauth-protected-resource/gt/api/mcp\", scope=\"mcp.read\"");

    JsonNode resource = getJson("/.well-known/oauth-protected-resource/gt/api/mcp");
    assertThat(resource.get("resource").asString()).isEqualTo(RESOURCE);
    assertThat(resource.get("authorization_servers").get(0).asString()).isEqualTo(ISSUER);

    JsonNode server = getJson("/.well-known/oauth-authorization-server/gt");
    assertThat(server.get("issuer").asString()).isEqualTo(ISSUER);
    assertThat(server.get("authorization_endpoint").asString()).isEqualTo(ISSUER + "/grafioschtrader/oauth/authorize");
    assertThat(server.get("token_endpoint").asString()).isEqualTo(ISSUER + "/api/oauth/token");
    assertThat(server.get("grant_types_supported").toString()).isEqualTo("[\"authorization_code\",\"refresh_token\"]");
    assertThat(server.get("token_endpoint_auth_methods_supported").toString()).isEqualTo("[\"none\"]");
    assertThat(server.get("client_id_metadata_document_supported").asBoolean()).isTrue();
    assertThat(server.get("authorization_response_iss_parameter_supported").asBoolean()).isTrue();

    restTestClient.get().uri("/.well-known/oauth-authorization-server").exchange().expectStatus().isNotFound();
    restTestClient.get().uri("/.well-known/oauth-protected-resource/api/mcp").exchange().expectStatus().isNotFound();
    restTestClient.get().uri("/.well-known/openid-configuration").exchange().expectStatus().is4xxClientError();
  }

  @Test
  @Order(2)
  @DisplayName("Validation: unknown client and foreign redirect are never redirected; other refusals carry error, state, iss")
  void validation() throws Exception {
    JsonNode unknown = validate(request("https://evil.example/meta.json", REDIRECT_URI, RESOURCE, challenge("v")), 400);
    assertThat(unknown.get("error").asString()).isEqualTo("invalid_client");
    assertThat(unknown.has("redirect")).isFalse();

    JsonNode foreign = validate(request(CLIENT_ID, "https://evil.example/cb", RESOURCE, challenge("v")), 400);
    assertThat(foreign.get("error").asString()).isEqualTo("invalid_redirect_uri");
    assertThat(foreign.has("redirect")).isFalse();

    JsonNode wrongResource = validate(request(CLIENT_ID, REDIRECT_URI, "https://other.example/api/mcp", challenge("v")),
        200);
    Map<String, String> redirect = query(wrongResource.get("redirect").asString());
    assertThat(redirect).containsEntry("error", "invalid_target").containsEntry("state", "s 1&x").containsEntry("iss",
        ISSUER);
  }

  @Test
  @Order(3)
  @DisplayName("Consent, code exchange with PKCE, the access token on /api/mcp; code replay and refresh token refused")
  void authorizeAndExchange() throws Exception {
    String verifier = verifier();
    String code = authorize(verifier);
    // A failed exchange consumes the code as well
    assertThat(token(codeParams(code, "wrong-" + verifier)).get("error").asString()).isEqualTo("invalid_grant");
    String verifier2 = verifier();
    String code2 = authorize(verifier2);
    tokens = token(codeParams(code2, verifier2));
    assertThat(tokens.get("token_type").asString()).isEqualTo("Bearer");
    assertThat(tokens.get("access_token").asString()).startsWith(UserAccessToken.TOKEN_PREFIX);
    assertThat(tokens.get("expires_in").asLong()).isBetween(3500L, 3600L);
    assertThat(token(codeParams(code2, verifier2)).get("error").asString()).isEqualTo("invalid_grant");

    mcp(tokens.get("access_token").asString()).expectStatus().isOk();
    mcp(tokens.get("refresh_token").asString()).expectStatus().isUnauthorized();
    assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_access_token WHERE token_hash IN (?, ?)",
        Integer.class, tokens.get("access_token").asString(), tokens.get("refresh_token").asString())).isZero();
  }

  @Test
  @Order(4)
  @DisplayName("Refresh: once a minute, rotation, and a replayed refresh token revokes the whole family")
  void refreshRotationAndReplay() throws Exception {
    String firstRefresh = tokens.get("refresh_token").asString();
    jdbcTemplate.update("UPDATE oauth_authorization SET last_refresh_at = ? WHERE id_user = ?",
        LocalDateTime.now(ZoneOffset.UTC), idUser);
    assertThat(token(refreshParams(firstRefresh)).get("error_description").asString()).contains("a minute");
    allowRefresh();
    JsonNode rotated = token(refreshParams(firstRefresh));
    assertThat(rotated.get("refresh_token").asString()).isNotEqualTo(firstRefresh);
    mcp(rotated.get("access_token").asString()).expectStatus().isOk();

    allowRefresh();
    assertThat(token(refreshParams(firstRefresh)).get("error").asString()).isEqualTo("invalid_grant");
    mcp(rotated.get("access_token").asString()).expectStatus().isUnauthorized();
    assertThat(token(refreshParams(rotated.get("refresh_token").asString())).get("error").asString())
        .isEqualTo("invalid_grant");
  }

  @Test
  @Order(5)
  @DisplayName("A new consent replaces the family; its end and the revocation in the dialog stop its tokens")
  void endAndRevocation() throws Exception {
    String verifier = verifier();
    JsonNode fresh = token(codeParams(authorize(verifier), verifier));
    assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM oauth_authorization WHERE id_user = ?", Integer.class,
        idUser)).isOne();
    mcp(fresh.get("access_token").asString()).expectStatus().isOk();

    jdbcTemplate.update("UPDATE oauth_authorization SET expires_at = ? WHERE id_user = ?",
        LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1), idUser);
    mcp(fresh.get("access_token").asString()).expectStatus().isUnauthorized();

    String verifier2 = verifier();
    JsonNode again = token(codeParams(authorize(verifier2), verifier2));
    Integer idAuthorization = jdbcTemplate.queryForObject(
        "SELECT id_oauth_authorization FROM oauth_authorization WHERE id_user = ?", Integer.class, idUser);
    authenticatedClient(RestTestHelper.ALLEDIT).delete()
        .uri("/api/useraccesstoken/oauthauthorization/" + idAuthorization).exchange().expectStatus().isNoContent();
    mcp(again.get("access_token").asString()).expectStatus().isUnauthorized();
    assertThat(token(refreshParams(again.get("refresh_token").asString())).get("error").asString())
        .isEqualTo("invalid_grant");
  }

  @Test
  @Order(6)
  @DisplayName("The anonymous validation is limited per client address")
  void validationRateLimit() throws Exception {
    int status = 0;
    for (int i = 0; i < OAuthValidateRateLimiter.MAX_REQUESTS_PER_HOUR + 1 && status != 429; i++) {
      status = restTestClient.post().uri("/api/oauth/authorize/validate").contentType(MediaType.APPLICATION_JSON)
          .body(request(CLIENT_ID, REDIRECT_URI, RESOURCE, challenge("v"))).exchange().returnResult(String.class)
          .getStatus().value();
    }
    assertThat(status).isEqualTo(429);
  }

  @Test
  @Order(7)
  @DisplayName("Without a public base URL OAuth disappears at once; closing the server hides the metadata too")
  void runtimeSwitch() {
    McpTestSwitch.open(jdbcTemplate, entityManagerFactory, null);
    String header = restTestClient.post().uri("/api/mcp").contentType(MediaType.APPLICATION_JSON).body("{}").exchange()
        .expectStatus().isUnauthorized().returnResult(String.class).getResponseHeaders()
        .getFirst(HttpHeaders.WWW_AUTHENTICATE);
    assertThat(header).isEqualTo("Bearer");
    restTestClient.get().uri("/.well-known/oauth-authorization-server/gt").exchange().expectStatus().isNotFound();

    McpTestSwitch.set(jdbcTemplate, entityManagerFactory, 0, ISSUER);
    restTestClient.get().uri("/.well-known/oauth-authorization-server/gt").exchange().expectStatus().isNotFound();
    restTestClient.post().uri("/api/oauth/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .body(refreshParams("gpat_unknown")).exchange().expectStatus().isNotFound();
  }

  // -----------------------------------------------------------------------------------------------------------------

  private String authorize(String verifier) throws Exception {
    JsonNode validation = validate(request(CLIENT_ID, REDIRECT_URI, RESOURCE, challenge(verifier)), 200);
    assertThat(validation.get("clientName").asString()).isEqualTo("Spike Assistant");
    assertThat(validation.get("clientHost").asString()).isEqualTo("client.example");
    String body = authenticatedClient(RestTestHelper.ALLEDIT).post().uri("/api/oauth/consent")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("authorizationRequestId", validation.get("authorizationRequestId").asString(), "approve", true))
        .exchange().expectStatus().isOk().expectBody(String.class).returnResult().getResponseBody();
    Map<String, String> redirect = query(JSON.readTree(body).get("redirect").asString());
    assertThat(redirect).containsEntry("state", "s 1&x").containsEntry("iss", ISSUER).containsKey("code");
    return redirect.get("code");
  }

  private JsonNode validate(Map<String, String> request, int expectedStatus) throws Exception {
    EntityExchangeResult<String> result = restTestClient.post().uri("/api/oauth/authorize/validate")
        .contentType(MediaType.APPLICATION_JSON).body(request).exchange().expectStatus().isEqualTo(expectedStatus)
        .expectBody(String.class).returnResult();
    return JSON.readTree(result.getResponseBody());
  }

  private JsonNode token(MultiValueMap<String, String> params) throws Exception {
    EntityExchangeResult<String> result = restTestClient.post().uri("/api/oauth/token")
        .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(params).exchange().expectBody(String.class)
        .returnResult();
    assertThat(result.getResponseHeaders().getCacheControl()).contains("no-store");
    return JSON.readTree(result.getResponseBody());
  }

  private org.springframework.test.web.servlet.client.RestTestClient.ResponseSpec mcp(String token) {
    return restTestClient.post().uri("/api/mcp").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
        .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM).contentType(MediaType.APPLICATION_JSON)
        .body("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\",\"params\":{}}").exchange();
  }

  private void allowRefresh() {
    jdbcTemplate.update("UPDATE oauth_authorization SET last_refresh_at = ? WHERE id_user = ?",
        LocalDateTime.now(ZoneOffset.UTC).minusMinutes(2), idUser);
  }

  private static Map<String, String> request(String clientId, String redirectUri, String resource, String challenge) {
    Map<String, String> request = new HashMap<>();
    request.put("response_type", "code");
    request.put("client_id", clientId);
    request.put("redirect_uri", redirectUri);
    request.put("state", "s 1&x");
    request.put("code_challenge", challenge);
    request.put("code_challenge_method", "S256");
    request.put("scope", "mcp.read");
    request.put("resource", resource);
    return request;
  }

  private static MultiValueMap<String, String> codeParams(String code, String verifier) {
    MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
    params.add("grant_type", "authorization_code");
    params.add("code", code);
    params.add("redirect_uri", REDIRECT_URI);
    params.add("client_id", CLIENT_ID);
    params.add("code_verifier", verifier);
    params.add("resource", RESOURCE);
    return params;
  }

  private static MultiValueMap<String, String> refreshParams(String refreshToken) {
    MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
    params.add("grant_type", "refresh_token");
    params.add("refresh_token", refreshToken);
    params.add("client_id", CLIENT_ID);
    params.add("resource", RESOURCE);
    return params;
  }

  private JsonNode getJson(String path) throws Exception {
    return JSON.readTree(restTestClient.get().uri(path).exchange().expectStatus().isOk().expectBody(String.class)
        .returnResult().getResponseBody());
  }

  private static Map<String, String> query(String url) {
    Map<String, String> values = new HashMap<>();
    UriComponentsBuilder.fromUri(URI.create(url)).build().getQueryParams()
        .forEach((k, v) -> values.put(k, java.net.URLDecoder.decode(v.getFirst(), StandardCharsets.UTF_8)));
    return values;
  }

  private static String verifier() {
    return OAuthRequestStore.randomToken();
  }

  private static String challenge(String verifier) throws Exception {
    return Base64.getUrlEncoder().withoutPadding()
        .encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
  }
}
