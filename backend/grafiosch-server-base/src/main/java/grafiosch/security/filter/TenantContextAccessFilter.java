package grafiosch.security.filter;

import java.io.IOException;
import java.util.Set;

import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import grafiosch.entities.TenantBase;
import grafiosch.entities.User;
import grafiosch.error.SingleNativeMsgError;
import grafiosch.rest.RequestMappings;
import grafiosch.rest.helper.RestErrorHandler;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Rejects every request whose current tenant context the user may no longer use - a revoked or downgraded grant, an
 * unrelated tenant, or a simulation environment that has been deleted meanwhile. The decision itself is made once per
 * request while the token is parsed and reaches this filter as {@link User#isTenantAccessForbidden()}.
 *
 * <p>
 * Unlike {@link TenantReadOnlyFilter} this filter blocks <b>all</b> HTTP methods. A stale context must not be readable
 * either, and several mutating operations are reached through a GET; a method-based rule would let both through.
 * </p>
 *
 * <p>
 * The user stays authenticated, so a small allow-list keeps the way out open: switching back to the home tenant and the
 * account-self endpoints that belong to the user rather than to the tenant. Switching remains safe because the switch
 * endpoint authorizes its target through the same resolver, so a blocked user reaches their home tenant and nothing
 * else.
 * </p>
 *
 * <p>
 * Must run after {@code StatelessAuthenticationFilter}, which populates the security context, and before
 * {@link TenantReadOnlyFilter}, whose answer would be the less accurate one for a context that is not merely read-only.
 * </p>
 */
public class TenantContextAccessFilter extends OncePerRequestFilter {

  /**
   * Paths that stay reachable with an unusable tenant context: the way back to the home tenant, the list of tenants the
   * user may switch to, and the account-self endpoints. Applications pass this to the constructor unless they have to
   * add their own.
   */
  public static final Set<String> RECOVERY_ALLOW_LIST = Set.of(
      RequestMappings.API + TenantBase.TABNAME + "/switchto/**",
      RequestMappings.API + TenantBase.TABNAME + "/accessible", RequestMappings.USER_MAP + "/password",
      RequestMappings.USER_MAP + "/nicknamelocale", RequestMappings.API + "userdashboard");

  private final MessageSource messages;
  private final Set<String> recoveryAllowListAntPatterns;
  private final AntPathMatcher pathMatcher = new AntPathMatcher();

  /**
   * @param messages                     message source for the localized rejection message
   *                                     ({@code g.tenant.access.forbidden})
   * @param recoveryAllowListAntPatterns Ant-style path patterns that stay reachable with a forbidden tenant context,
   *                                     normally {@link #RECOVERY_ALLOW_LIST}
   */
  public TenantContextAccessFilter(final MessageSource messages, final Set<String> recoveryAllowListAntPatterns) {
    this.messages = messages;
    this.recoveryAllowListAntPatterns = recoveryAllowListAntPatterns;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    User user = currentUser();
    if (user != null && user.isTenantAccessForbidden() && !isAllowListed(request)) {
      // The application may name the reason - a simulation environment that is replaying reads differently from a
      // withdrawn access - and falls back to the generic text when it has nothing more specific to say.
      String messageKey = user.getTenantAccessBlockMessageKey() == null ? "g.tenant.access.forbidden"
          : user.getTenantAccessBlockMessageKey();
      String message = messages.getMessage(messageKey, null, user.createAndGetJavaLocale());
      RestErrorHandler.createErrorResponseForServlet(response, HttpStatus.FORBIDDEN, new SingleNativeMsgError(message));
      return;
    }
    chain.doFilter(request, response);
  }

  private boolean isAllowListed(HttpServletRequest request) {
    String path = request.getServletPath();
    return recoveryAllowListAntPatterns.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
  }

  private User currentUser() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.getDetails() instanceof User user) {
      return user;
    }
    return null;
  }
}
