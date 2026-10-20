package grafioschtrader.mcp.oauth;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import grafiosch.types.AccessTokenScope;

/**
 * Translates between the OAuth scopes of the MCP server and {@link AccessTokenScope}. {@code mcp.read} is
 * {@link AccessTokenScope#READ}; {@code mcp.write} would be {@link AccessTokenScope#WRITE}, which includes reading, but
 * is not granted before proposals can be approved in the user interface, so a request for it receives reading only.
 */
public final class OAuthScopes {

  private static final Set<String> KNOWN = Set.of(McpOAuthEndpoints.SCOPE_READ, McpOAuthEndpoints.SCOPE_WRITE);

  private OAuthScopes() {
  }

  /**
   * Parses the space separated scope parameter of a request.
   *
   * @param scope the parameter; null or blank means reading
   * @return the granted scope, or empty when the parameter names an unknown scope
   */
  public static Optional<AccessTokenScope> toScope(String scope) {
    if (scope == null || scope.isBlank()) {
      return Optional.of(AccessTokenScope.READ);
    }
    Set<String> requested = Arrays.stream(scope.trim().split("\\s+")).collect(Collectors.toSet());
    return KNOWN.containsAll(requested) ? Optional.of(AccessTokenScope.READ) : Optional.empty();
  }

  public static String toOAuthScope(AccessTokenScope scope) {
    return scope == AccessTokenScope.WRITE ? McpOAuthEndpoints.SCOPE_READ + " " + McpOAuthEndpoints.SCOPE_WRITE
        : McpOAuthEndpoints.SCOPE_READ;
  }
}
