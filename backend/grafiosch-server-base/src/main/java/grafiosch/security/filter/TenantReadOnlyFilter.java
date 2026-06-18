package grafiosch.security.filter;

import java.io.IOException;
import java.util.Set;

import org.springframework.context.MessageSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import grafiosch.entities.User;
import grafiosch.error.SingleNativeMsgError;
import grafiosch.rest.helper.RestErrorHandler;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Rejects write requests (POST/PUT/PATCH/DELETE) when the authenticated user operates in a tenant that is read-only for
 * them. "Read-only" applies to the tenant's portfolio data, not to the user's own account, so a small allow-list of
 * account-self paths (for example changing the own password) and the tenant switch-back path is exempt.
 *
 * <p>
 * Must run after {@code StatelessAuthenticationFilter}, which populates the security context with the {@link User} whose
 * {@link User#isTenantAccessReadOnly()} flag was resolved during token parsing. This filter is the hard guarantee that a
 * read-only user mutates nothing; the frontend additionally hides write actions for usability only.
 * </p>
 *
 * <p>
 * The allow-list patterns are passed in by the application so this reusable filter does not need to know
 * application-specific request mappings. Patterns use Ant-style matching against the request servlet path.
 * </p>
 */
public class TenantReadOnlyFilter extends OncePerRequestFilter {

  private final MessageSource messages;
  private final Set<String> writeAllowListAntPatterns;
  private final AntPathMatcher pathMatcher = new AntPathMatcher();

  /**
   * @param messages                  message source for the localized rejection message ({@code gt.tenant.access.readonly})
   * @param writeAllowListAntPatterns Ant-style path patterns that remain writable for a read-only user (account-self
   *                                  endpoints such as the change-password path, and the tenant switch-back path)
   */
  public TenantReadOnlyFilter(final MessageSource messages, final Set<String> writeAllowListAntPatterns) {
    this.messages = messages;
    this.writeAllowListAntPatterns = writeAllowListAntPatterns;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (isBlockedWrite(request)) {
      User user = currentUser();
      String message = messages.getMessage("gt.tenant.access.readonly", null,
          user != null ? user.createAndGetJavaLocale() : null);
      RestErrorHandler.createErrorResponseForServlet(response, HttpStatus.FORBIDDEN, new SingleNativeMsgError(message));
      return;
    }
    chain.doFilter(request, response);
  }

  private boolean isBlockedWrite(HttpServletRequest request) {
    if (!isWriteMethod(request.getMethod())) {
      return false;
    }
    User user = currentUser();
    if (user == null || !user.isTenantAccessReadOnly()) {
      return false;
    }
    return !isAllowListed(request);
  }

  private boolean isWriteMethod(String method) {
    return HttpMethod.POST.matches(method) || HttpMethod.PUT.matches(method) || HttpMethod.PATCH.matches(method)
        || HttpMethod.DELETE.matches(method);
  }

  private boolean isAllowListed(HttpServletRequest request) {
    String path = request.getServletPath();
    return writeAllowListAntPatterns.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
  }

  private User currentUser() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.getDetails() instanceof User user) {
      return user;
    }
    return null;
  }
}
