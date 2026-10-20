package grafioschtrader.mcp.oauth;

import static org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED_VALUE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import grafioschtrader.mcp.oauth.OAuthRequestStore.CodeGrant;
import grafioschtrader.mcp.oauth.OAuthTokenService.Failure;
import grafioschtrader.mcp.oauth.OAuthTokenService.Issued;
import grafioschtrader.mcp.oauth.OAuthTokenService.TokenResult;
import io.swagger.v3.oas.annotations.Operation;

/**
 * The token endpoint of the OAuth authorization (RFC 6749 with PKCE, RFC 7636, and resource indicators, RFC 8707).
 * Clients are public and authenticate with nothing but their {@code client_id}; the code is bound to it, to the
 * redirect URI and to the PKCE challenge, and a refresh token to its family.
 *
 * <ul>
 * <li>{@code authorization_code}: {@code code}, {@code redirect_uri}, {@code client_id}, {@code code_verifier}, and
 * optionally {@code resource}, which must then be the resource of the authorization request. The code is taken out of
 * the store before anything is checked, so a second exchange fails whatever the first one did.</li>
 * <li>{@code refresh_token}: {@code refresh_token}, {@code client_id}, optionally {@code resource} and
 * {@code scope}.</li>
 * </ul>
 * Every answer carries {@code Cache-Control: no-store}. A repeated parameter is refused.
 */
@RestController
@ConditionalOnProperty(prefix = "gt.use", name = "mcp", havingValue = "true")
public class OAuthTokenResource {

  private final McpOAuthEndpoints endpoints;
  private final OAuthRequestStore requestStore;
  private final OAuthTokenService tokenService;

  public OAuthTokenResource(McpOAuthEndpoints endpoints, OAuthRequestStore requestStore,
      OAuthTokenService tokenService) {
    this.endpoints = endpoints;
    this.requestStore = requestStore;
    this.tokenService = tokenService;
  }

  @Operation(summary = "OAuth token endpoint for the MCP server", tags = { "oauth" })
  @PostMapping(value = McpOAuthEndpoints.TOKEN_PATH, consumes = APPLICATION_FORM_URLENCODED_VALUE,
      produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Map<String, Object>> token(@RequestParam MultiValueMap<String, String> parameters) {
    if (!endpoints.isOAuthAvailable()) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }
    if (parameters.values().stream().anyMatch(values -> values.size() > 1)) {
      return answer(new Failure("invalid_request", "repeated parameter"));
    }
    Map<String, String> p = parameters.toSingleValueMap();
    String grantType = p.get("grant_type");
    if ("authorization_code".equals(grantType)) {
      return answer(exchangeCode(p));
    }
    if ("refresh_token".equals(grantType)) {
      return answer(tokenService.refresh(p.get("refresh_token"), p.get("client_id"), p.get("resource"),
          p.get("scope")));
    }
    return answer(new Failure("unsupported_grant_type", null));
  }

  private TokenResult exchangeCode(Map<String, String> p) {
    CodeGrant grant = requestStore.takeCode(p.get("code"));
    if (grant == null) {
      return new Failure("invalid_grant", "unknown, used or expired code");
    }
    if (!grant.clientId().equals(p.get("client_id"))) {
      return new Failure("invalid_grant", "code issued to another client");
    }
    if (!grant.redirectUri().equals(p.get("redirect_uri"))) {
      return new Failure("invalid_grant", "redirect_uri differs from the authorization request");
    }
    if (p.get("resource") != null && !grant.resource().equals(p.get("resource"))) {
      return new Failure("invalid_target", "resource differs from the authorization request");
    }
    if (!Pkce.verify(p.get("code_verifier"), grant.codeChallenge())) {
      return new Failure("invalid_grant", "code_verifier does not match");
    }
    return tokenService.issueForCode(grant);
  }

  private static ResponseEntity<Map<String, Object>> answer(TokenResult result) {
    Map<String, Object> body = new LinkedHashMap<>();
    HttpStatus status;
    if (result instanceof Issued issued) {
      body.put("access_token", issued.accessToken());
      body.put("token_type", "Bearer");
      body.put("expires_in", issued.expiresInSeconds());
      body.put("refresh_token", issued.refreshToken());
      body.put("scope", issued.scope());
      status = HttpStatus.OK;
    } else {
      Failure failure = (Failure) result;
      body.put("error", failure.error());
      if (failure.description() != null) {
        body.put("error_description", failure.description());
      }
      status = HttpStatus.BAD_REQUEST;
    }
    return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).header("Pragma", "no-cache")
        .body(body);
  }
}
