package grafiosch.security;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.stereotype.Component;

import grafiosch.entities.User;
import grafiosch.service.UserService;

/**
 * JWT token handler for creating and parsing authentication tokens.
 *
 * <p>
 * This component provides comprehensive JWT token management including token creation, parsing, and validation for
 * stateless authentication. It integrates with Spring Security to create tokens containing user identity, roles, tenant
 * information, and locale preferences for complete authentication context.
 * </p>
 *
 * <h3>Token Features:</h3>
 * <ul>
 * <li><strong>User Identity:</strong> Username and user ID for authentication</li>
 * <li><strong>Authorization:</strong> User roles and permissions</li>
 * <li><strong>Multi-tenancy:</strong> Tenant ID for data isolation</li>
 * <li><strong>Localization:</strong> User locale preferences</li>
 * <li><strong>Expiration:</strong> Configurable token lifetime</li>
 * </ul>
 *
 * <h3>Security:</h3>
 * <p>
 * Uses HMAC SHA-256 algorithm with configurable secret keys. The secret must be at least 32 characters long to meet
 * HS256 security requirements. Tokens are signed and verified to ensure integrity and authenticity.
 * </p>
 *
 * <h3>Configuration:</h3>
 * <p>
 * Configured via Spring Boot properties with "gt.jwt" prefix, allowing environment-specific JWT settings for different
 * deployment scenarios.
 * </p>
 */
@Component
public final class JwtTokenHandler {

  private static final String ID_USER = "idUser";

  @Autowired
  private UserService userService;

  @Autowired
  private JwtEncoder jwtEncoder;

  @Autowired
  private JwtDecoder jwtDecoder;

  /**
   * Parses and validates a JWT token to extract user details.
   *
   * <p>
   * This method validates the token signature, extracts user information, and loads the complete user details from the
   * database. It performs both token validation and user verification to ensure the token represents a valid, current
   * user session.
   * </p>
   *
   * @param token the JWT token to parse and validate
   * @return Optional containing UserDetails if token is valid, empty if invalid
   * @throws JwtValidationException if the token has expired or is otherwise invalid
   */
  public Optional<UserDetails> parseUserFromToken(final String token) {
    final Jwt jwt = jwtDecoder.decode(token);
    Integer userId = ((Number) jwt.getClaim(ID_USER)).intValue();
    User user = (User) userService.loadUserByUserIdAndCheckUsername(userId, jwt.getSubject());
    if (user != null) {
      Number jwtIdTenantNum = jwt.getClaim("idTenant");
      Integer jwtIdTenant = jwtIdTenantNum != null ? jwtIdTenantNum.intValue() : null;
      if (jwtIdTenant != null && !jwtIdTenant.equals(user.getIdTenant())) {
        user.setActualIdTenant(user.getIdTenant());
        user.setIdTenant(jwtIdTenant);
      }
    }
    return Optional.ofNullable(user);
  }

  /**
   * Extracts the user ID from a JWT token.
   *
   * <p>
   * Parses the token and returns the user ID claim without performing full user validation. This method is useful for
   * lightweight user identification without database lookups.
   * </p>
   *
   * @param token the JWT token to extract user ID from
   * @return the user ID contained in the token
   */
  public Integer getUserId(final String token) {
    final Jwt jwt = jwtDecoder.decode(token);
    return ((Number) jwt.getClaim(ID_USER)).intValue();
  }

  /**
   * Creates a new JWT token for the specified user with configurable expiration.
   *
   * @param user              the UserDetails object containing user information
   * @param expirationMinutes number of minutes until token expires
   * @return signed JWT token string ready for client use
   */
  public String createTokenForUser(final UserDetails user, int expirationMinutes) {
    return createTokenForUser(user, expirationMinutes, null);
  }

  /**
   * Creates a new JWT token for the specified user with an optional tenant ID override.
   *
   * <p>
   * When {@code overrideIdTenant} is non-null, the token's {@code idTenant} claim uses the override value instead of
   * the user's actual tenant ID. This supports tenant switching for simulation environments where the user temporarily
   * operates under a different (simulation) tenant context.
   * </p>
   *
   * @param user              the UserDetails object containing user information
   * @param expirationMinutes number of minutes until token expires
   * @param overrideIdTenant  optional tenant ID to use instead of the user's own tenant ID; null uses the user's tenant
   * @return signed JWT token string ready for client use
   */
  public String createTokenForUser(final UserDetails user, int expirationMinutes, Integer overrideIdTenant) {
    final Instant expiry = ZonedDateTime.now().plusMinutes(expirationMinutes).toInstant();

    List<String> roles = user.getAuthorities().stream().map(GrantedAuthority::getAuthority)
        .collect(Collectors.toList());

    int tenantId = overrideIdTenant != null ? overrideIdTenant : ((User) user).getIdTenant();

    JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
    JwtClaimsSet claims = JwtClaimsSet.builder()
        .subject(user.getUsername())
        .claim(ID_USER, ((User) user).getIdUser())
        .claim("idTenant", tenantId)
        .claim("localeStr", ((User) user).getLocaleStr())
        .claim("roles", roles)
        .expiresAt(expiry)
        .build();

    return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
  }

}
