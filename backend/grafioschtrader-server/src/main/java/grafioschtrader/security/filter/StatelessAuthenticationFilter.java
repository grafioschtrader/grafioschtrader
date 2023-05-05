package grafioschtrader.security.filter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.GenericFilterBean;

import grafioschtrader.GlobalConstants;
import grafioschtrader.entities.User;
import grafioschtrader.error.ErrorWithLogout;
import grafioschtrader.error.SingleNativeMsgError;
import grafioschtrader.exceptions.RequestLimitAndSecurityBreachException;
import grafioschtrader.rest.helper.RestErrorHandler;
import grafioschtrader.security.TokenAuthenticationService;
import grafioschtrader.security.UserRightLimitCounter;
import grafioschtrader.service.UserService;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Servlet filter for every request after authentication process.
 *
 */
public class StatelessAuthenticationFilter extends GenericFilterBean {

  private final TokenAuthenticationService tokenAuthenticationService;
  private final MessageSource messages;
  private final Map<Integer, Bucket> limitRateMap = new ConcurrentHashMap<>();
  private final UserService userService;
  private final boolean limitRequest;

  public StatelessAuthenticationFilter(final TokenAuthenticationService tokenAuthenticationService,
      final MessageSource messages, final UserService userService, final boolean limitRequest) {
    this.tokenAuthenticationService = tokenAuthenticationService;
    this.messages = messages;
    this.userService = userService;
    this.limitRequest = limitRequest;
  }

  private Bucket createNewBucket() {
    Bandwidth limitMinute = Bandwidth.classic(GlobalConstants.BANDWITH_MINUTE_BUCKET_SIZE,
        Refill.greedy(GlobalConstants.BANDWITH_MINUTE_REFILL, Duration.ofMinutes(1)));
    Bandwidth limitHour = Bandwidth.classic(GlobalConstants.BANDWITH_HOOUR_BUCKET_SIZE,
        Refill.greedy(GlobalConstants.BANDWITH_HOUR_REFILL, Duration.ofHours((1))));
    return Bucket.builder().addLimit(limitMinute).addLimit(limitHour).build();
  }

  @Override
  public void doFilter(final ServletRequest servletRequest, final ServletResponse servletResponse,
      final FilterChain chain) throws IOException, ServletException {

    try {
  //    System.out.println(((HttpServletRequest) servletRequest).getServletPath());

      Authentication authentication = tokenAuthenticationService
          .generateAuthenticationFromRequest((HttpServletRequest) servletRequest);
      SecurityContextHolder.getContext().setAuthentication(authentication);
      if (authentication != null) {
        if (limitRequest) {
          doFilterWithlimitWatcher(servletRequest, servletResponse, chain, authentication);
        } else {
          chain.doFilter(servletRequest, servletResponse);
        }
      } else {
        chain.doFilter(servletRequest, servletResponse);
      }

//      SecurityContextHolder.getContext().setAuthentication(null);
    } catch (RequestLimitAndSecurityBreachException lee) {
      // User has to many times misused the limits of GT
      createErrorMessage(servletResponse, lee, HttpStatus.TOO_MANY_REQUESTS);
    } catch (AuthenticationException | JwtException e) {
      createErrorMessage(servletResponse, e, HttpStatus.UNAUTHORIZED);

    }
  }

  private void createErrorMessage(final ServletResponse servletResponse, Exception e, HttpStatus httpStatus)
      throws IOException {
    SecurityContextHolder.clearContext();
    RestErrorHandler.createErrorResponseForServlet((HttpServletResponse) servletResponse, httpStatus,
        new ErrorWithLogout(e.getMessage()));
  }

  private void doFilterWithlimitWatcher(final ServletRequest servletRequest, final ServletResponse servletResponse,
      final FilterChain chain, final Authentication authentication) throws IOException, ServletException {
    Integer idUser = ((User) authentication.getDetails()).getIdUser();
    Bucket bucket = limitRateMap.computeIfAbsent(idUser, k -> createNewBucket());
    if (bucket.tryConsume(1)) {
      // the limit is not exceeded
      chain.doFilter(servletRequest, servletResponse);
    } else {
      // limit is exceeded
      userService.incrementRightsLimitCount(idUser, UserRightLimitCounter.LIMIT_EXCEEDED_TENANT_DATA);
      String message = messages.getMessage("too.many.request", null,
          ((User) authentication.getDetails()).createAndGetJavaLocale());
      RestErrorHandler.createErrorResponseForServlet((HttpServletResponse) servletResponse,
          HttpStatus.TOO_MANY_REQUESTS, new SingleNativeMsgError(message));
    }
  }

}
