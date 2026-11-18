package grafioschtrader.mcp;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import grafiosch.common.PublicBaseUrl;
import grafiosch.repository.GlobalparametersJpaRepository;
import grafiosch.security.AccessTokenAvailability;
import grafioschtrader.service.GlobalparametersService;

/**
 * Whether the MCP server is open right now. The deployment switch {@code gt.use.mcp} only loads the code; the
 * administrator opens and closes the access with the global parameter {@code gt.mcp.use}, without a restart. While it
 * is closed, {@code /api/mcp} and the OAuth endpoints answer 404, no access token authenticates and no personal token
 * can be created.
 */
@Component
@ConditionalOnProperty(prefix = "gt.use", name = "mcp", havingValue = "true")
public class McpAvailability implements AccessTokenAvailability {

  private final GlobalparametersService globalparametersService;
  private final GlobalparametersJpaRepository parameters;

  public McpAvailability(GlobalparametersService globalparametersService, GlobalparametersJpaRepository parameters) {
    this.globalparametersService = globalparametersService;
    this.parameters = parameters;
  }

  public boolean isOpen() {
    return globalparametersService.isMcpUse();
  }

  @Override
  public boolean isAccessTokenAllowed() {
    return isOpen();
  }

  /** The public base URL from {@code g.public.base.url}, or null when the administrator has not set one. */
  public PublicBaseUrl getPublicBaseUrl() {
    return parameters.getPublicBaseUrl();
  }
}
