package grafiosch.security;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.AccountStatusUserDetailsChecker;
import org.springframework.stereotype.Service;

import grafiosch.config.BaseFeatureConfig;
import grafiosch.entities.User;
import grafiosch.entities.UserAccessToken;
import grafiosch.repository.OAuthAuthorizationJpaRepository;
import grafiosch.repository.UserAccessTokenJpaRepository;
import grafiosch.repository.UserJpaRepository;
import grafiosch.service.UserService;
import grafiosch.types.TenantAccessLevel;

/**
 * Resolves a personal access token to the user it belongs to. It is the counterpart of the JWT parsing for the paths an
 * application opens for machine clients, and it deliberately mirrors that parsing: the account status and the request
 * limit checks apply as for a browser request, and the read-only state of the tenant is decided by
 * {@link TenantAccessResolver}.
 *
 * <p>
 * A token reaches nothing but the home tenant of its user. The freshly loaded user carries the home tenant and no other
 * tenant is ever set, so neither a header nor an argument of the client can redirect the request.
 * </p>
 */
@Service
public class AccessTokenAuthenticationService {

  private static final Logger log = LoggerFactory.getLogger(AccessTokenAuthenticationService.class);

  private final UserAccessTokenJpaRepository userAccessTokenJpaRepository;
  private final OAuthAuthorizationJpaRepository oAuthAuthorizationJpaRepository;
  private final UserJpaRepository userJpaRepository;
  private final UserService userService;
  private final TenantAccessResolver tenantAccessResolver;
  private final BaseFeatureConfig baseFeatureConfig;
  private final ObjectProvider<AccessTokenAvailability> availability;

  public AccessTokenAuthenticationService(UserAccessTokenJpaRepository userAccessTokenJpaRepository,
      OAuthAuthorizationJpaRepository oAuthAuthorizationJpaRepository, UserJpaRepository userJpaRepository,
      UserService userService, TenantAccessResolver tenantAccessResolver, BaseFeatureConfig baseFeatureConfig,
      ObjectProvider<AccessTokenAvailability> availability) {
    this.userAccessTokenJpaRepository = userAccessTokenJpaRepository;
    this.oAuthAuthorizationJpaRepository = oAuthAuthorizationJpaRepository;
    this.userJpaRepository = userJpaRepository;
    this.userService = userService;
    this.tenantAccessResolver = tenantAccessResolver;
    this.baseFeatureConfig = baseFeatureConfig;
    this.availability = availability;
  }

  /**
   * Tells whether tokens may be used now: the deployment switch is on and the application, when it decides this at run
   * time, allows them.
   */
  public boolean isAccessTokenAllowed() {
    AccessTokenAvailability decision = availability.getIfAvailable();
    return baseFeatureConfig.isAccesstoken() && (decision == null || decision.isAccessTokenAllowed());
  }

  /** The user and the token behind an authenticated request. */
  public record AuthenticatedToken(User user, AccessTokenPrincipal principal) {
  }

  /**
   * Looks the token up by its hash and loads its user.
   *
   * <p>
   * An unknown and an expired token give the same empty answer, so the caller cannot tell them apart. A disabled or
   * locked account, or one blocked for exceeding the request limits, raises the exception the JWT path raises as well.
   * </p>
   *
   * <p>
   * Personal tokens and OAuth access tokens are accepted; a refresh token never is, it is valid at the token endpoint
   * only. An OAuth access token must be issued for the expected resource, and its family must be neither revoked nor
   * ended, even while the token itself is still valid.
   * </p>
   *
   * @param plainToken       the token in clear text as sent by the client
   * @param expectedAudience the canonical URI of the resource the request is for; null accepts personal tokens only
   * @return the authenticated user and token, or empty when the feature is off or the token is not accepted
   */
  public Optional<AuthenticatedToken> authenticate(String plainToken, String expectedAudience) {
    if (!isAccessTokenAllowed()) {
      return Optional.empty();
    }
    LocalDateTime nowUtc = LocalDateTime.now(ZoneOffset.UTC);
    Optional<UserAccessToken> tokenOpt = userAccessTokenJpaRepository
        .findByTokenHash(UserAccessToken.hashToken(plainToken)).filter(t -> !t.isExpired(nowUtc));
    if (tokenOpt.isEmpty()) {
      return Optional.empty();
    }
    UserAccessToken token = tokenOpt.get();
    if (!isAcceptedKind(token, expectedAudience, nowUtc)) {
      return Optional.empty();
    }
    User user = userJpaRepository.findById(token.getIdUser()).orElse(null);
    if (user == null) {
      return Optional.empty();
    }
    new AccountStatusUserDetailsChecker().check(user);
    userService.checkUserLimits(user);
    TenantAccessDecision decision = tenantAccessResolver.decide(user, user.getIdTenant(), user.getIdTenant());
    user.setTenantAccessForbidden(!decision.isAllowed());
    user.setTenantAccessBlockMessageKey(decision.refusalMessageKey());
    user.setTenantAccessReadOnly(!decision.isAllowed() || decision.level() == TenantAccessLevel.READ);
    recordUse(token, nowUtc);
    return Optional.of(new AuthenticatedToken(user,
        new AccessTokenPrincipal(user.getIdUser(), token.getIdUserAccessToken(), token.getScope())));
  }

  private boolean isAcceptedKind(UserAccessToken token, String expectedAudience, LocalDateTime nowUtc) {
    return switch (token.getTokenKind()) {
    case PERSONAL -> true;
    case OAUTH_ACCESS -> expectedAudience != null && expectedAudience.equals(token.getAudience())
        && token.getIdOAuthAuthorization() != null && oAuthAuthorizationJpaRepository
            .findById(token.getIdOAuthAuthorization()).filter(a -> a.isActive(nowUtc)).isPresent();
    case OAUTH_REFRESH -> false;
    };
  }

  /**
   * Writes the time of use at most once per hour. A failure is only logged: the request itself is valid.
   */
  private void recordUse(UserAccessToken token, LocalDateTime nowUtc) {
    if (token.getLastUsedAt() == null || token.getLastUsedAt().isBefore(nowUtc.minusHours(1))) {
      try {
        userAccessTokenJpaRepository.updateLastUsedAt(token.getIdUserAccessToken(), nowUtc.withNano(0),
            nowUtc.minusHours(1));
      } catch (RuntimeException e) {
        log.warn("Could not record the use of access token {}", token.getIdUserAccessToken(), e);
      }
    }
  }
}
