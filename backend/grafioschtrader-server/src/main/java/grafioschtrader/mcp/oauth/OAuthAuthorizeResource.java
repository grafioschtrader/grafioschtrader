package grafioschtrader.mcp.oauth;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import grafiosch.config.LimitKeyBaseConfig;
import grafiosch.entities.User;
import grafiosch.exceptions.DataViolationException;
import grafiosch.repository.OAuthAuthorizationJpaRepository;
import grafiosch.service.EntityLimitService;
import grafiosch.types.AccessTokenScope;
import grafioschtrader.mcp.oauth.ClientMetadataService.ClientMetadata;
import grafioschtrader.mcp.oauth.OAuthRequestStore.CodeGrant;
import grafioschtrader.mcp.oauth.OAuthRequestStore.PendingRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.servlet.http.HttpServletRequest;

/**
 * The two server steps behind the Angular authorization page. The page receives the authorization request of a client
 * as query parameters, has it validated here, lets the user log in if necessary, shows the consent and finally sends
 * the browser to the address this resource returns.
 *
 * <ul>
 * <li>{@code validate} is anonymous and bounded per client address. An unknown client or a redirect URI that its
 * metadata document does not list is reported to the page only, never redirected, so GT cannot be used as an open
 * redirector. Every other refusal is returned as the client's redirect URI with {@code error}, {@code state} and
 * {@code iss}.</li>
 * <li>{@code consent} needs the GT login of the user in the home tenant. An approval issues a one-time code bound to
 * client, redirect URI, code challenge, scope, resource and user; a refusal answers {@code access_denied}.</li>
 * </ul>
 */
@RestController
@ConditionalOnProperty(prefix = "gt.use", name = "mcp", havingValue = "true")
public class OAuthAuthorizeResource {

  private final McpOAuthEndpoints endpoints;
  private final ClientMetadataService clientMetadataService;
  private final OAuthRequestStore requestStore;
  private final OAuthValidateRateLimiter rateLimiter;
  private final OAuthAuthorizationJpaRepository oAuthAuthorizationJpaRepository;
  private final EntityLimitService entityLimitService;

  public OAuthAuthorizeResource(McpOAuthEndpoints endpoints, ClientMetadataService clientMetadataService,
      OAuthRequestStore requestStore, OAuthValidateRateLimiter rateLimiter,
      OAuthAuthorizationJpaRepository oAuthAuthorizationJpaRepository, EntityLimitService entityLimitService) {
    this.endpoints = endpoints;
    this.clientMetadataService = clientMetadataService;
    this.requestStore = requestStore;
    this.rateLimiter = rateLimiter;
    this.oAuthAuthorizationJpaRepository = oAuthAuthorizationJpaRepository;
    this.entityLimitService = entityLimitService;
  }

  @Schema(description = "The query parameters of an OAuth authorization request, as the authorization page received them")
  public record AuthorizationRequest(@JsonProperty("response_type") String responseType,
      @JsonProperty("client_id") String clientId, @JsonProperty("redirect_uri") String redirectUri,
      @JsonProperty("state") String state, @JsonProperty("code_challenge") String codeChallenge,
      @JsonProperty("code_challenge_method") String codeChallengeMethod, @JsonProperty("scope") String scope,
      @JsonProperty("resource") String resource) {
  }

  @Schema(description = """
      Outcome of the validation. Either the request is valid and waits for the consent (authorizationRequestId with the
      client's name), or it is refused: with redirect when the client may be told, without when the page has to show
      the error itself.""")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record ValidationResponse(String authorizationRequestId, String clientName, String clientHost, String scope,
      String error, String redirect) {
  }

  @Schema(description = "The user's decision on a validated authorization request")
  public record ConsentRequest(String authorizationRequestId, boolean approve) {
  }

  @Schema(description = "Where the browser goes next: back to the client, with a code or with an error")
  public record RedirectResponse(String redirect) {
  }

  @Operation(summary = "Validates an OAuth authorization request before the consent", tags = { "oauth" })
  @PostMapping(value = McpOAuthEndpoints.VALIDATE_PATH, produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<ValidationResponse> validate(@RequestBody AuthorizationRequest request,
      HttpServletRequest httpRequest) {
    if (!endpoints.isOAuthAvailable()) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }
    if (!rateLimiter.tryAcquire(httpRequest)) {
      return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(error("temporarily_unavailable", null));
    }
    ClientMetadata client = clientMetadataService.findClient(request.clientId());
    if (client == null) {
      return ResponseEntity.badRequest().body(error("invalid_client", null));
    }
    if (request.redirectUri() == null || !client.redirectUris().contains(request.redirectUri())) {
      return ResponseEntity.badRequest().body(error("invalid_redirect_uri", null));
    }
    String refusal = refusal(request);
    if (refusal != null) {
      return ResponseEntity.ok(error(refusal, redirectWithError(request.redirectUri(), refusal, request.state())));
    }
    AccessTokenScope scope = OAuthScopes.toScope(request.scope()).orElseThrow();
    String id = requestStore.putRequest(new PendingRequest(client.clientId(), client.clientName(), client.clientHost(),
        request.redirectUri(), request.state(), request.codeChallenge(), scope, request.resource(), System.nanoTime()));
    if (id == null) {
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error("temporarily_unavailable", null));
    }
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new ValidationResponse(id, client.clientName(),
        client.clientHost(), OAuthScopes.toOAuthScope(scope), null, null));
  }

  /** The checks whose failure may be reported to the client, which is known and its redirect URI registered. */
  private String refusal(AuthorizationRequest request) {
    if (!"code".equals(request.responseType())) {
      return "unsupported_response_type";
    }
    if (!"S256".equals(request.codeChallengeMethod()) || !Pkce.isValidChallenge(request.codeChallenge())) {
      return "invalid_request";
    }
    if (OAuthScopes.toScope(request.scope()).isEmpty()) {
      return "invalid_scope";
    }
    String resource = endpoints.getResource();
    if (resource == null || !resource.equals(request.resource())) {
      return "invalid_target";
    }
    return null;
  }

  @Operation(summary = "Records the user's consent and returns the redirect back to the client", tags = { "oauth" })
  @PostMapping(value = McpOAuthEndpoints.CONSENT_PATH, produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<RedirectResponse> consent(@RequestBody ConsentRequest consent) {
    if (!endpoints.isOAuthAvailable()) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }
    User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    if (!user.getIdTenant().equals(user.getActualIdTenant())) {
      // Tokens always read the home tenant; consenting from another tenant would suggest otherwise
      throw new DataViolationException("oauth.client", "g.access.token.switched.tenant", null);
    }
    PendingRequest pending = requestStore.takeRequest(consent.authorizationRequestId());
    if (pending == null) {
      throw new DataViolationException("oauth.client", "gt.mcp.oauth.request.expired", null);
    }
    if (!consent.approve()) {
      return ResponseEntity
          .ok(new RedirectResponse(redirectWithError(pending.redirectUri(), "access_denied", pending.state())));
    }
    if (oAuthAuthorizationJpaRepository.findByIdUserAndClientId(user.getIdUser(), pending.clientId()).isEmpty()
        && !entityLimitService.fitsWithinLimit(user, LimitKeyBaseConfig.KEY_OAUTH_AUTHORIZATION, null, 1)) {
      throw new DataViolationException("oauth.client", "MAX_OAUTH_AUTHORIZATION", null);
    }
    String code = requestStore.putCode(new CodeGrant(user.getIdUser(), pending.clientId(), pending.clientName(),
        pending.redirectUri(), pending.codeChallenge(), pending.scope(), pending.resource(), System.nanoTime()));
    if (code == null) {
      return ResponseEntity.ok(
          new RedirectResponse(redirectWithError(pending.redirectUri(), "temporarily_unavailable", pending.state())));
    }
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
        .body(new RedirectResponse(redirect(pending.redirectUri(), "code", code, pending.state())));
  }

  private String redirectWithError(String redirectUri, String error, String state) {
    return redirect(redirectUri, "error", error, state);
  }

  /**
   * Appends the answer to the registered redirect URI, which is used exactly as registered. The state goes back
   * unchanged, and iss (RFC 9207) lets the client check which server answered.
   */
  private String redirect(String redirectUri, String name, String value, String state) {
    StringBuilder url = new StringBuilder(redirectUri).append(redirectUri.contains("?") ? '&' : '?');
    url.append(name).append('=').append(encode(value));
    if (state != null) {
      url.append("&state=").append(encode(state));
    }
    return url.append("&iss=").append(encode(endpoints.getIssuer())).toString();
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private static ValidationResponse error(String error, String redirect) {
    return new ValidationResponse(null, null, null, null, error, redirect);
  }
}
