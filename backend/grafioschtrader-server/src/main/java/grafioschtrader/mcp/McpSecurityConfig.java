package grafioschtrader.mcp;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.ai.mcp.annotation.provider.tool.SyncStatelessMcpToolProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

import grafiosch.config.BaseFeatureConfig;
import grafiosch.dto.AccessTokenClientInfo;
import grafiosch.entities.Role;
import grafiosch.error.SingleNativeMsgError;
import grafiosch.rest.RequestMappings;
import grafiosch.rest.helper.RestErrorHandler;
import grafiosch.security.AccessTokenAuthenticationService;
import grafiosch.security.AccessTokenClientInfoProvider;
import grafiosch.security.filter.AccessTokenAuthenticationFilter;
import grafiosch.service.LoginAttemptServiceIpAddress;
import grafioschtrader.mcp.oauth.McpOAuthEndpoints;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification;

/**
 * The security chain and the tool registration of the MCP server under {@code /api/mcp}. Everything here exists only
 * with the deployment switch {@code gt.use.mcp=true}, which also switches the Spring AI starter on; whether the server
 * answers at all is decided at run time by the administrator through the global parameter {@code gt.mcp.use}.
 *
 * <p>
 * The chain matches only the MCP paths and is ordered before the main chain of {@code SecurityGTConfig}, which matches
 * every request. Its only authentication filter is the access token filter, so a browser JWT authenticates nothing
 * here, and the main chain never sees the access token filter, so a token is never accepted elsewhere. There is no
 * read-only tenant filter: it decides by HTTP method, and every MCP call is a POST.
 * </p>
 */
@Configuration
@ConditionalOnProperty(prefix = "gt.use", name = "mcp", havingValue = "true")
public class McpSecurityConfig {

  public static final String MCP_PATH = RequestMappings.API + "mcp";

  private final Set<String> allowedOrigins;
  private final McpAvailability mcpAvailability;

  /**
   * @param allowedOrigins  {@code gt.mcp.allowed.origins}, a deployment property because it describes the network
   *                        around the instance rather than a business decision
   * @param mcpAvailability the administrator's switch {@code gt.mcp.use} and the public base URL
   */
  public McpSecurityConfig(BaseFeatureConfig baseFeatureConfig,
      @Value("${gt.mcp.allowed.origins:}") String allowedOrigins, McpAvailability mcpAvailability) {
    if (!baseFeatureConfig.isAccesstoken()) {
      throw new IllegalStateException(
          "gt.use.mcp=true requires g.use.accesstoken=true: the MCP server authenticates with personal access tokens");
    }
    this.allowedOrigins = Arrays.stream(allowedOrigins.split(",")).map(String::trim).filter(o -> !o.isEmpty())
        .collect(Collectors.toUnmodifiableSet());
    this.mcpAvailability = mcpAvailability;
  }

  @Bean
  @Order(0)
  SecurityFilterChain mcpFilterChain(HttpSecurity http,
      AccessTokenAuthenticationService accessTokenAuthenticationService,
      LoginAttemptServiceIpAddress loginAttemptServiceIpAddress, McpOAuthEndpoints oauthEndpoints) throws Exception {
    http.securityMatcher(MCP_PATH, MCP_PATH + "/**");
    http.csrf(csrf -> csrf.disable());
    http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
    http.httpBasic(basic -> basic.disable());
    http.formLogin(form -> form.disable());
    http.authorizeHttpRequests(authz -> authz.anyRequest().hasAnyRole(Role.USER, Role.LIMIT_EDIT));
    // A 401 names the protected resource metadata, from which an OAuth client discovers the authorization server
    http.exceptionHandling(exceptions -> exceptions.authenticationEntryPoint((_, response, _) -> {
      response.setHeader(HttpHeaders.WWW_AUTHENTICATE, oauthEndpoints.getWwwAuthenticate());
      RestErrorHandler.createErrorResponseForServlet(response, HttpStatus.UNAUTHORIZED,
          new SingleNativeMsgError("A valid access token is required"));
    }));
    McpOriginFilter originFilter = new McpOriginFilter(allowedOrigins);
    http.addFilterBefore(originFilter, AnonymousAuthenticationFilter.class);
    http.addFilterBefore(new McpClosedFilter(mcpAvailability), McpOriginFilter.class);
    // Header and audience follow the global parameters, so the filter asks for them on every request
    http.addFilterAfter(new AccessTokenAuthenticationFilter(accessTokenAuthenticationService,
        loginAttemptServiceIpAddress, oauthEndpoints::getWwwAuthenticate, oauthEndpoints::getResource),
        McpOriginFilter.class);
    return http.build();
  }

  /**
   * The anonymous part of the OAuth authorization: the two metadata documents, the validation of an authorization
   * request and the token endpoint. A client calls them before it holds a token, so they cannot belong to the MCP
   * chain; they get a chain of their own, stateless and without CSRF, which exists only with the MCP server. The
   * consent stays in the main chain, because it needs the user's GT login.
   */
  @Bean
  @Order(0)
  SecurityFilterChain mcpOAuthPublicFilterChain(HttpSecurity http) throws Exception {
    http.securityMatcher(McpOAuthEndpoints.PROTECTED_RESOURCE_WELL_KNOWN,
        McpOAuthEndpoints.PROTECTED_RESOURCE_WELL_KNOWN + "/**", McpOAuthEndpoints.AUTHORIZATION_SERVER_WELL_KNOWN,
        McpOAuthEndpoints.AUTHORIZATION_SERVER_WELL_KNOWN + "/**", McpOAuthEndpoints.VALIDATE_PATH,
        McpOAuthEndpoints.TOKEN_PATH);
    http.csrf(csrf -> csrf.disable());
    http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
    http.httpBasic(basic -> basic.disable());
    http.formLogin(form -> form.disable());
    http.authorizeHttpRequests(authz -> authz.anyRequest().permitAll());
    http.addFilterBefore(new McpClosedFilter(mcpAvailability), AnonymousAuthenticationFilter.class);
    return http.build();
  }

  /**
   * The tools offered by the server. They are registered explicitly from the one tool bean, with the annotation scanner
   * and the conversion of {@code ToolCallback} beans switched off, so that no other bean can become a public tool.
   */
  @Bean
  List<SyncToolSpecification> gtMcpToolSpecifications(GTMcpReadTools gtMcpReadTools) {
    return new SyncStatelessMcpToolProvider(List.of(gtMcpReadTools)).getToolSpecifications();
  }

  /** Tells the token dialog where clients connect and how a configuration looks. */
  @Bean
  AccessTokenClientInfoProvider mcpAccessTokenClientInfoProvider() {
    return () -> {
      String publicBaseUrl = mcpAvailability.getPublicBaseUrl();
      String endpoint = publicBaseUrl == null ? null : publicBaseUrl + MCP_PATH;
      String snippet = endpoint == null ? null : """
          claude mcp add --transport http grafioschtrader %s \\
            --header "Authorization: Bearer <TOKEN>" \\
            --header "x-time-zone: ${TIME_ZONE}"\
          """.formatted(endpoint);
      return new AccessTokenClientInfo(endpoint, snippet);
    };
  }
}
