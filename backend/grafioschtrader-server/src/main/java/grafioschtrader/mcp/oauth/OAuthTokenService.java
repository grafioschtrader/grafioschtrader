package grafioschtrader.mcp.oauth;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.entities.OAuthAuthorization;
import grafiosch.entities.UserAccessToken;
import grafiosch.repository.OAuthAuthorizationJpaRepository;
import grafiosch.repository.UserAccessTokenJpaRepository;
import grafiosch.repository.UserJpaRepository;
import grafiosch.types.AccessTokenKind;
import grafioschtrader.mcp.oauth.OAuthRequestStore.CodeGrant;

/**
 * Issues and rotates the tokens of OAuth clients. Every token is a {@code gpat_} token like a personal one and is
 * stored only as its hash, in {@code user_access_token} with a reference to its family in {@code oauth_authorization}.
 *
 * <ul>
 * <li>A code exchange creates the family of the consent; an earlier family of the same user and client is deleted with
 * its tokens, so a new consent replaces the old one.</li>
 * <li>A refresh locks the refresh token and its family, marks the token consumed and issues a new pair. A consumed
 * token presented again is a replay (RFC 9700, section 4.14): the family is revoked and all its tokens are deleted.</li>
 * <li>An access token lives one hour, a refresh token 30 days, neither beyond the end of the family, which lies at
 * most 90 days after the consent.</li>
 * <li>A family is refreshed at most once a minute. Of its consumed refresh tokens the newest 100 stay recognizable;
 * older ones and expired access tokens are deleted in the same transaction as the rotation.</li>
 * </ul>
 *
 * <p>
 * The methods return a {@link TokenResult} instead of throwing, so that the revocation of a replayed family is
 * committed although the client receives an error.
 * </p>
 */
@Service
@ConditionalOnProperty(prefix = "gt.use", name = "mcp", havingValue = "true")
public class OAuthTokenService {

  public static final Duration ACCESS_TOKEN_LIFETIME = Duration.ofHours(1);
  public static final Duration REFRESH_TOKEN_LIFETIME = Duration.ofDays(30);
  public static final Duration MIN_REFRESH_INTERVAL = Duration.ofMinutes(1);
  public static final int KEEP_CONSUMED_REFRESH_TOKENS = 100;

  private static final SecureRandom RANDOM = new SecureRandom();

  /** The outcome of a token request: either a token pair or an OAuth error. */
  public sealed interface TokenResult permits Issued, Failure {
  }

  public record Issued(String accessToken, String refreshToken, long expiresInSeconds, String scope)
      implements TokenResult {
  }

  public record Failure(String error, String description) implements TokenResult {
  }

  private final OAuthAuthorizationJpaRepository oAuthAuthorizationJpaRepository;
  private final UserAccessTokenJpaRepository userAccessTokenJpaRepository;
  private final UserJpaRepository userJpaRepository;

  public OAuthTokenService(OAuthAuthorizationJpaRepository oAuthAuthorizationJpaRepository,
      UserAccessTokenJpaRepository userAccessTokenJpaRepository, UserJpaRepository userJpaRepository) {
    this.oAuthAuthorizationJpaRepository = oAuthAuthorizationJpaRepository;
    this.userAccessTokenJpaRepository = userAccessTokenJpaRepository;
    this.userJpaRepository = userJpaRepository;
  }

  /**
   * Creates the family of a redeemed code and issues its first token pair. The code itself was checked and consumed
   * by the caller.
   */
  @Transactional
  public TokenResult issueForCode(CodeGrant grant) {
    if (!userJpaRepository.existsById(grant.idUser())) {
      return new Failure("invalid_grant", "the user no longer exists");
    }
    LocalDateTime nowUtc = now();
    oAuthAuthorizationJpaRepository.findByIdUserAndClientId(grant.idUser(), grant.clientId())
        .ifPresent(previous -> {
          oAuthAuthorizationJpaRepository.delete(previous);
          oAuthAuthorizationJpaRepository.flush();
        });
    OAuthAuthorization family = oAuthAuthorizationJpaRepository.save(new OAuthAuthorization(grant.idUser(),
        grant.clientId(), grant.clientName(), grant.scope(), grant.resource(), nowUtc));
    return issuePair(family, nowUtc);
  }

  /**
   * Rotates a refresh token.
   *
   * @param refreshToken the refresh token in clear text
   * @param clientId     the client_id of the token request, which must be the family's client
   * @param resource     the resource of the token request, or null
   * @param scope        the scope of the token request, or null; it can never widen the family's scope
   */
  @Transactional
  public TokenResult refresh(String refreshToken, String clientId, String resource, String scope) {
    LocalDateTime nowUtc = now();
    Optional<UserAccessToken> tokenOpt = refreshToken == null ? Optional.empty()
        : userAccessTokenJpaRepository.lockByTokenHash(UserAccessToken.hashToken(refreshToken));
    if (tokenOpt.isEmpty() || tokenOpt.get().getTokenKind() != AccessTokenKind.OAUTH_REFRESH) {
      return new Failure("invalid_grant", "unknown refresh token");
    }
    UserAccessToken token = tokenOpt.get();
    OAuthAuthorization family = oAuthAuthorizationJpaRepository.lockById(token.getIdOAuthAuthorization())
        .orElse(null);
    if (family == null || !family.isActive(nowUtc)) {
      return new Failure("invalid_grant", "the authorization was revoked or has ended");
    }
    if (token.getConsumedAt() != null) {
      revoke(family, nowUtc);
      return new Failure("invalid_grant", "refresh token reused; the authorization is revoked");
    }
    Failure refused = checkRefreshRequest(token, family, clientId, resource, scope, nowUtc);
    if (refused != null) {
      return refused;
    }
    token.setConsumedAt(nowUtc);
    userAccessTokenJpaRepository.save(token);
    family.setLastRefreshAt(nowUtc);
    oAuthAuthorizationJpaRepository.save(family);
    TokenResult issued = issuePair(family, nowUtc);
    userAccessTokenJpaRepository.deleteExpiredAccessTokensOfFamily(family.getIdOAuthAuthorization(), nowUtc);
    userAccessTokenJpaRepository.deleteConsumedBeyond(family.getIdOAuthAuthorization(), KEEP_CONSUMED_REFRESH_TOKENS);
    return issued;
  }

  private Failure checkRefreshRequest(UserAccessToken token, OAuthAuthorization family, String clientId,
      String resource, String scope, LocalDateTime nowUtc) {
    if (token.isExpired(nowUtc)) {
      return new Failure("invalid_grant", "refresh token expired");
    }
    if (!family.getClientId().equals(clientId)) {
      return new Failure("invalid_grant", "refresh token issued to another client");
    }
    if (resource != null && !family.getResource().equals(resource)) {
      return new Failure("invalid_target", "resource differs from the authorization");
    }
    if (scope != null && !scope.isBlank() && !OAuthScopes.toScope(scope).map(s -> s == family.getScope())
        .orElse(false)) {
      return new Failure("invalid_scope", "a refresh cannot change the scope");
    }
    if (family.getLastRefreshAt() != null && family.getLastRefreshAt().plus(MIN_REFRESH_INTERVAL).isAfter(nowUtc)) {
      return new Failure("invalid_grant", "refreshed less than a minute ago");
    }
    return null;
  }

  private void revoke(OAuthAuthorization family, LocalDateTime nowUtc) {
    family.setRevokedAt(nowUtc);
    oAuthAuthorizationJpaRepository.save(family);
    userAccessTokenJpaRepository.deleteByIdOAuthAuthorization(family.getIdOAuthAuthorization());
  }

  private Issued issuePair(OAuthAuthorization family, LocalDateTime nowUtc) {
    LocalDateTime accessExpires = earlier(nowUtc.plus(ACCESS_TOKEN_LIFETIME), family.getExpiresAt());
    LocalDateTime refreshExpires = earlier(nowUtc.plus(REFRESH_TOKEN_LIFETIME), family.getExpiresAt());
    String access = newToken();
    String refresh = newToken();
    userAccessTokenJpaRepository.save(tokenRow(family, AccessTokenKind.OAUTH_ACCESS, access, nowUtc, accessExpires));
    userAccessTokenJpaRepository.save(tokenRow(family, AccessTokenKind.OAUTH_REFRESH, refresh, nowUtc,
        refreshExpires));
    return new Issued(access, refresh, Duration.between(nowUtc, accessExpires).toSeconds(),
        OAuthScopes.toOAuthScope(family.getScope()));
  }

  private static UserAccessToken tokenRow(OAuthAuthorization family, AccessTokenKind kind, String token,
      LocalDateTime nowUtc, LocalDateTime expiresAt) {
    return UserAccessToken.forOAuth(family, kind, UserAccessToken.hashToken(token),
        token.substring(0, UserAccessToken.DISPLAY_PREFIX_LENGTH), nowUtc, expiresAt);
  }

  private static LocalDateTime earlier(LocalDateTime a, LocalDateTime b) {
    return a.isBefore(b) ? a : b;
  }

  private static LocalDateTime now() {
    return LocalDateTime.now(ZoneOffset.UTC).withNano(0);
  }

  /** A new token in the format of personal tokens, so the filter and secret scanners treat both alike. */
  static String newToken() {
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    return UserAccessToken.TOKEN_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }
}
