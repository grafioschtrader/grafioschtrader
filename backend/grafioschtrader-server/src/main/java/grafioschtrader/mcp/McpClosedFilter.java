package grafioschtrader.mcp;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import grafiosch.error.SingleNativeMsgError;
import grafiosch.rest.helper.RestErrorHandler;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Answers 404 for every path of the MCP server and its OAuth authorization while the administrator keeps it closed
 * ({@code gt.mcp.use=0}), exactly as if the server were not deployed. It runs first in both MCP chains, before the
 * origin check and before any token is looked at, so a closed server reveals nothing about tokens or clients.
 */
public class McpClosedFilter extends OncePerRequestFilter {

  private final McpAvailability mcpAvailability;

  public McpClosedFilter(McpAvailability mcpAvailability) {
    this.mcpAvailability = mcpAvailability;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (!mcpAvailability.isOpen()) {
      // Written directly: sendError would dispatch to /error, which the main chain answers with 403
      RestErrorHandler.createErrorResponseForServlet(response, HttpStatus.NOT_FOUND,
          new SingleNativeMsgError("Not found"));
      return;
    }
    chain.doFilter(request, response);
  }
}
