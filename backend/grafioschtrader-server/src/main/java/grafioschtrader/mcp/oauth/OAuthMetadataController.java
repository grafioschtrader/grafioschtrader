package grafioschtrader.mcp.oauth;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Discovery of the OAuth authorization: the protected resource metadata (RFC 9728) of the MCP endpoint and the
 * authorization server metadata (RFC 8414). Both are anonymous. They are answered only under the paths
 * {@link McpOAuthEndpoints} derives from the public base URL; every other path below the two well-known prefixes, and
 * every path while no public base URL is configured, answers 404.
 *
 * <p>
 * GT offers no OpenID Connect discovery and no dynamic client registration; clients identify themselves with a Client
 * ID Metadata Document.
 * </p>
 */
@RestController
@ConditionalOnProperty(prefix = "gt.use", name = "mcp", havingValue = "true")
public class OAuthMetadataController {

  private final McpOAuthEndpoints endpoints;

  public OAuthMetadataController(McpOAuthEndpoints endpoints) {
    this.endpoints = endpoints;
  }

  @GetMapping(value = { McpOAuthEndpoints.PROTECTED_RESOURCE_WELL_KNOWN,
      McpOAuthEndpoints.PROTECTED_RESOURCE_WELL_KNOWN + "/**" }, produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Map<String, Object>> protectedResourceMetadata(HttpServletRequest request) {
    if (!endpoints.isOAuthAvailable() || !endpoints.getProtectedResourceMetadataPath().equals(path(request))) {
      return ResponseEntity.notFound().build();
    }
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("resource", endpoints.getResource());
    metadata.put("authorization_servers", List.of(endpoints.getIssuer()));
    metadata.put("scopes_supported", List.of(McpOAuthEndpoints.SCOPE_READ));
    metadata.put("bearer_methods_supported", List.of("header"));
    return ResponseEntity.ok(metadata);
  }

  @GetMapping(value = { McpOAuthEndpoints.AUTHORIZATION_SERVER_WELL_KNOWN,
      McpOAuthEndpoints.AUTHORIZATION_SERVER_WELL_KNOWN + "/**" }, produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Map<String, Object>> authorizationServerMetadata(HttpServletRequest request) {
    if (!endpoints.isOAuthAvailable() || !endpoints.getAuthorizationServerMetadataPath().equals(path(request))) {
      return ResponseEntity.notFound().build();
    }
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("issuer", endpoints.getIssuer());
    metadata.put("authorization_endpoint", endpoints.getAuthorizationEndpoint());
    metadata.put("token_endpoint", endpoints.getTokenEndpoint());
    metadata.put("response_types_supported", List.of("code"));
    metadata.put("grant_types_supported", List.of("authorization_code", "refresh_token"));
    metadata.put("code_challenge_methods_supported", List.of("S256"));
    metadata.put("token_endpoint_auth_methods_supported", List.of("none"));
    metadata.put("scopes_supported", List.of(McpOAuthEndpoints.SCOPE_READ));
    metadata.put("client_id_metadata_document_supported", true);
    metadata.put("authorization_response_iss_parameter_supported", true);
    return ResponseEntity.ok(metadata);
  }

  private static String path(HttpServletRequest request) {
    return request.getRequestURI().substring(request.getContextPath().length());
  }
}
