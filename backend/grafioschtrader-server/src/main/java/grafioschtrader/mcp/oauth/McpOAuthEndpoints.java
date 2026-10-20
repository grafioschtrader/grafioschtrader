package grafioschtrader.mcp.oauth;

import java.net.URI;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import grafioschtrader.mcp.McpAvailability;
import grafioschtrader.mcp.McpSecurityConfig;

/**
 * Every address of the OAuth authorization of the MCP server, derived from the global parameter
 * {@code gt.mcp.public.base.url} alone and read anew on every call, so a change by the administrator takes effect
 * without a restart. None is ever built from the incoming request, and the two metadata locations are not appended to
 * the base URL: RFC 9728 and RFC 8414 put the well-known part before the path, so under a path prefix both lie outside
 * of it.
 *
 * <table>
 * <tr>
 * <th>Address</th>
 * <th>at the domain root</th>
 * <th>under the prefix /gt</th>
 * </tr>
 * <tr>
 * <td>issuer</td>
 * <td>https://host</td>
 * <td>https://host/gt</td>
 * </tr>
 * <tr>
 * <td>resource</td>
 * <td>https://host/api/mcp</td>
 * <td>https://host/gt/api/mcp</td>
 * </tr>
 * <tr>
 * <td>protected resource metadata</td>
 * <td>/.well-known/oauth-protected-resource/api/mcp</td>
 * <td>/.well-known/oauth-protected-resource/gt/api/mcp</td>
 * </tr>
 * <tr>
 * <td>authorization server metadata</td>
 * <td>/.well-known/oauth-authorization-server</td>
 * <td>/.well-known/oauth-authorization-server/gt</td>
 * </tr>
 * </table>
 *
 * <p>
 * Without a usable public base URL, or while the MCP server is closed ({@code gt.mcp.use}), OAuth is not offered: the
 * metadata answer 404 and the token endpoint refuses. Personal access tokens need no base URL.
 * </p>
 */
@Component
@ConditionalOnProperty(prefix = "gt.use", name = "mcp", havingValue = "true")
public class McpOAuthEndpoints {

  public static final String PROTECTED_RESOURCE_WELL_KNOWN = "/.well-known/oauth-protected-resource";
  public static final String AUTHORIZATION_SERVER_WELL_KNOWN = "/.well-known/oauth-authorization-server";
  public static final String TOKEN_PATH = "/api/oauth/token";
  public static final String VALIDATE_PATH = "/api/oauth/authorize/validate";
  public static final String CONSENT_PATH = "/api/oauth/consent";
  public static final String SCOPE_READ = "mcp.read";
  public static final String SCOPE_WRITE = "mcp.write";

  private final McpAvailability mcpAvailability;
  private final String authorizePagePath;

  /**
   * @param mcpAvailability   supplies the switch {@code gt.mcp.use} and the public base URL
   * @param authorizePagePath {@code gt.mcp.oauth.authorize.page}, the path of the Angular authorization page below the
   *                          issuer; {@code /grafioschtrader/oauth/authorize} for the built frontend
   */
  public McpOAuthEndpoints(McpAvailability mcpAvailability,
      @Value("${gt.mcp.oauth.authorize.page:/grafioschtrader/oauth/authorize}") String authorizePagePath) {
    this.mcpAvailability = mcpAvailability;
    this.authorizePagePath = authorizePagePath;
  }

  /** The issuer, which is the public base URL; null while OAuth is not offered. */
  private String issuer() {
    String base = mcpAvailability.isOpen() ? mcpAvailability.getPublicBaseUrl() : null;
    return isUsableIssuer(base) ? base : null;
  }

  /** HTTPS, or HTTP to a loopback name for development; no query, no fragment. */
  private static boolean isUsableIssuer(String base) {
    if (base == null || base.isEmpty()) {
      return false;
    }
    try {
      URI uri = URI.create(base);
      boolean loopback = "localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost());
      return uri.getHost() != null && uri.getQuery() == null && uri.getFragment() == null
          && ("https".equals(uri.getScheme()) || ("http".equals(uri.getScheme()) && loopback));
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  /** True while the MCP server is open and a usable public base URL is set, which OAuth needs for its addresses. */
  public boolean isOAuthAvailable() {
    return issuer() != null;
  }

  public String getIssuer() {
    return issuer();
  }

  /**
   * The canonical URI of the MCP endpoint, the audience of every OAuth access token. Null while OAuth is not offered,
   * which makes the access token filter accept personal tokens only.
   */
  public String getResource() {
    String issuer = issuer();
    return issuer == null ? null : issuer + McpSecurityConfig.MCP_PATH;
  }

  public String getProtectedResourceMetadataUrl() {
    String issuer = issuer();
    return issuer == null ? null : origin(issuer) + protectedResourceMetadataPath(issuer);
  }

  /** The request path GT answers the protected resource metadata under. */
  public String getProtectedResourceMetadataPath() {
    String issuer = issuer();
    return issuer == null ? null : protectedResourceMetadataPath(issuer);
  }

  private static String protectedResourceMetadataPath(String issuer) {
    return PROTECTED_RESOURCE_WELL_KNOWN + path(issuer + McpSecurityConfig.MCP_PATH);
  }

  /** The request path GT answers the authorization server metadata under. */
  public String getAuthorizationServerMetadataPath() {
    String issuer = issuer();
    return issuer == null ? null : AUTHORIZATION_SERVER_WELL_KNOWN + path(issuer);
  }

  public String getAuthorizationEndpoint() {
    String issuer = issuer();
    return issuer == null ? null : issuer + authorizePagePath;
  }

  public String getTokenEndpoint() {
    String issuer = issuer();
    return issuer == null ? null : issuer + TOKEN_PATH;
  }

  /**
   * The {@code WWW-Authenticate} value of a 401 answer of the MCP endpoint. It names the protected resource metadata,
   * from which a client discovers the authorization server; without OAuth it is a bare {@code Bearer}.
   */
  public String getWwwAuthenticate() {
    String metadataUrl = getProtectedResourceMetadataUrl();
    return metadataUrl == null ? "Bearer"
        : "Bearer resource_metadata=\"" + metadataUrl + "\", scope=\"" + SCOPE_READ + "\"";
  }

  private static String origin(String url) {
    URI uri = URI.create(url);
    return uri.getScheme() + "://" + uri.getRawAuthority();
  }

  private static String path(String url) {
    String path = URI.create(url).getRawPath();
    return path == null ? "" : path;
  }
}
