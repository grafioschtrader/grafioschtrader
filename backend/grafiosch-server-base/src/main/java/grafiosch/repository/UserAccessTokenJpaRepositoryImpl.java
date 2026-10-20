package grafiosch.repository;

import java.lang.annotation.Annotation;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Collections;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.config.LimitKeyBaseConfig;
import grafiosch.dto.UserAccessTokenCreateRequest;
import grafiosch.dto.UserAccessTokenCreatedDto;
import grafiosch.dto.UserAccessTokenDto;
import grafiosch.entities.User;
import grafiosch.entities.UserAccessToken;
import grafiosch.exceptions.DataViolationException;
import grafiosch.service.DailyLimitService;
import grafiosch.service.EntityLimitService;
import grafiosch.types.AccessTokenKind;
import grafiosch.types.AccessTokenScope;
import grafiosch.types.OperationType;

/**
 * Creation and validation of personal access tokens. Every way a token comes into existence passes
 * {@link #saveOnlyAttributes}, which is where the rules on name, lifetime and scope are enforced.
 */
public class UserAccessTokenJpaRepositoryImpl implements UserAccessTokenJpaRepositoryCustom {

  /** Number of random bytes of a token; base64url without padding turns 32 bytes into 43 characters. */
  private static final int TOKEN_RANDOM_BYTES = 32;

  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  @Autowired
  @Lazy
  private UserAccessTokenJpaRepository userAccessTokenJpaRepository;

  @Autowired
  private EntityLimitService entityLimitService;

  @Autowired
  private DailyLimitService dailyLimitService;

  @Override
  @Transactional
  public UserAccessTokenCreatedDto createToken(User user, UserAccessTokenCreateRequest request) {
    if (!user.getIdTenant().equals(user.getActualIdTenant())) {
      // A token always reads the home tenant; creating one while working in another tenant would suggest otherwise.
      throw new DataViolationException("token.name", "g.access.token.switched.tenant", null);
    }
    String entityName = UserAccessToken.class.getSimpleName();
    dailyLimitService.check(user, entityName, 1);
    if (!entityLimitService.fitsWithinLimit(user, LimitKeyBaseConfig.KEY_USER_ACCESS_TOKEN, null, 1)) {
      throw new DataViolationException("token.name", "MAX_USER_ACCESS_TOKEN", null);
    }
    String plainToken = generatePlainToken();
    LocalDateTime nowUtc = LocalDateTime.now(ZoneOffset.UTC).withNano(0);
    UserAccessToken token = new UserAccessToken(user.getIdUser(), request.getTokenName().trim(),
        UserAccessToken.hashToken(plainToken), plainToken.substring(0, UserAccessToken.DISPLAY_PREFIX_LENGTH),
        request.getScope(), nowUtc, nowUtc.plusDays(request.getExpiresInDays()));
    token = saveOnlyAttributes(token, null, Collections.emptySet());
    dailyLimitService.log(user.getIdUser(), entityName, OperationType.ADD, 1);
    return new UserAccessTokenCreatedDto(UserAccessTokenDto.of(token, nowUtc), plainToken);
  }

  /**
   * Validates and persists a new token. Tokens are never changed, so an existing entity is rejected.
   *
   * <p>
   * Checked are the uniqueness of the name per user, the lifetime of at most
   * {@link UserAccessToken#MAX_EXPIRES_IN_DAYS} days, and the scope: {@link AccessTokenScope#WRITE} is not offered yet
   * and would never be granted to a user whose home tenant is read-only.
   * </p>
   */
  @Override
  public UserAccessToken saveOnlyAttributes(UserAccessToken newEntity, UserAccessToken existingEntity,
      Set<Class<? extends Annotation>> updatePropertyLevelClasses) {
    if (existingEntity != null || newEntity.getIdUserAccessToken() != null) {
      throw new DataViolationException("token.name", "g.access.token.immutable", null);
    }
    validateToken(newEntity);
    return userAccessTokenJpaRepository.save(newEntity);
  }

  private void validateToken(UserAccessToken token) {
    if (token.getTokenKind() == AccessTokenKind.PERSONAL
        && userAccessTokenJpaRepository.existsByIdUserAndTokenNameAndKind(token.getIdUser(), token.getTokenName(),
            AccessTokenKind.PERSONAL.getValue())) {
      throw new DataViolationException("token.name", "g.access.token.name.exists",
          new Object[] { token.getTokenName() });
    }
    if (!token.getExpiresAt().isAfter(token.getCreatedAt())
        || token.getExpiresAt().isAfter(token.getCreatedAt().plusDays(UserAccessToken.MAX_EXPIRES_IN_DAYS))) {
      throw new DataViolationException("expires.in.days", "g.access.token.expires.range",
          new Object[] { UserAccessToken.MAX_EXPIRES_IN_DAYS });
    }
    if (token.getScope() != AccessTokenScope.READ) {
      // WRITE becomes available together with proposals that are approved in the user interface; a user with a
      // read-only home tenant will never receive it.
      throw new DataViolationException("scope", "g.access.token.scope.not.allowed", null);
    }
  }

  @Override
  public Set<Class<? extends Annotation>> getUpdatePropertyLevels(UserAccessToken existingEntity) {
    return Collections.emptySet();
  }

  private static String generatePlainToken() {
    byte[] randomBytes = new byte[TOKEN_RANDOM_BYTES];
    SECURE_RANDOM.nextBytes(randomBytes);
    return UserAccessToken.TOKEN_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
  }
}
