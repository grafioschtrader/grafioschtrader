package grafioschtrader.mcp;

import java.io.IOException;
import java.util.Set;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import grafiosch.error.SingleNativeMsgError;
import grafiosch.rest.helper.RestErrorHandler;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Protects the MCP endpoint against DNS rebinding: a browser always sends an {@code Origin} header, so a request with
 * an origin that is not explicitly allowed is refused with 403 before any authentication. A request without the header
 * comes from a command line or server client and passes. Disabling CSRF or configuring CORS does not replace this check.
 */
public class McpOriginFilter extends OncePerRequestFilter {

  private final Set<String> allowedOrigins;

  /**
   * @param allowedOrigins exact origins such as {@code http://localhost:6274}; empty refuses every browser origin
   */
  public McpOriginFilter(Set<String> allowedOrigins) {
    this.allowedOrigins = allowedOrigins;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String origin = request.getHeader(HttpHeaders.ORIGIN);
    if (origin != null && !allowedOrigins.contains(origin)) {
      RestErrorHandler.createErrorResponseForServlet(response, HttpStatus.FORBIDDEN,
          new SingleNativeMsgError("Origin not allowed"));
      return;
    }
    chain.doFilter(request, response);
  }
}
