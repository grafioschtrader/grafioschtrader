package grafiosch.security;

import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import grafiosch.entities.User;
import grafiosch.service.UserService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

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
@ConfigurationProperties(prefix = "gt.jwt")
public final class JwtTokenHandler {

  private static final String ID_USER = "idUser";
  
  /** HS256 is used, the secret should at least be 32 characters long */
  private SecretKey secretKey;

  @Autowired
  private UserService userService;

  /**
   * Sets the JWT secret and generates the signing key.
   * 
   * <p>
   * Converts the provided secret string into an HMAC SHA-256 secret key for JWT token signing and verification. The
   * secret should be at least 32 characters long to meet HS256 algorithm requirements.
   * </p>
   * 
   * @param secret the JWT secret string, must be at least 32 characters for HS256
   */
  public void setSecret(String secret) {
    this.secretKey = Keys.hmacShaKeyFor(secret.getBytes());
  }

  /**
   * Parses and validates a JWT token to extract user details.
   * 
   * <p>
   * This method validates the token signature, extracts user information, and loads the complete user details from the
   * database. It performs both token validation and user verification to ensure the token represents a valid, current
   * user session.
   * </p>
   * 
   * <h3>Validation Process:</h3>
   * <ul>
   * <li><strong>Signature Verification:</strong> Validates token integrity</li>
   * <li><strong>User Loading:</strong> Retrieves current user details</li>
   * <li><strong>Username Verification:</strong> Ensures token and user data match</li>
   * </ul>
   * 
   * @param token the JWT token to parse and validate
   * @return Optional containing UserDetails if token is valid, empty if invalid
   * @throws ExpiredJwtException if the token has expired
   */
  public Optional<UserDetails> parseUserFromToken(final String token) {
    try {
      final Claims jwsClaims = Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();
      Integer userId = (Integer) jwsClaims.get(ID_USER);
      return Optional.ofNullable(userService.loadUserByUserIdAndCheckUsername(userId, jwsClaims.getSubject()));
    } catch (ExpiredJwtException e) {
      throw e;
    }
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
    final Claims jwsClaims = Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();
    return (Integer) jwsClaims.get(ID_USER);
  }

  /**
   * Creates a new JWT token for the specified user with configurable expiration.
   * 
   * <p>
   * Generates a comprehensive JWT token containing user identity, authorization information, tenant context, and locale
   * preferences. The token is signed with the configured secret key and includes an expiration time based on the
   * specified duration.
   * </p>
   * 
   * <h3>Token Claims:</h3>
   * <ul>
   * <li><strong>Subject:</strong> Username for user identification</li>
   * <li><strong>idUser:</strong> Unique user identifier</li>
   * <li><strong>idTenant:</strong> Tenant ID for multi-tenant isolation</li>
   * <li><strong>localeStr:</strong> User's locale preference</li>
   * <li><strong>roles:</strong> List of user authorities and permissions</li>
   * <li><strong>exp:</strong> Token expiration timestamp</li>
   * </ul>
   * 
   * @param user              the UserDetails object containing user information
   * @param expirationMinutes number of minutes until token expires
   * @return signed JWT token string ready for client use
   */
  public String createTokenForUser(final UserDetails user, int expirationMinutes) {
    final ZonedDateTime afterSomeMinutes = ZonedDateTime.now().plusMinutes(expirationMinutes);

    List<String> roles = user.getAuthorities().stream().map(GrantedAuthority::getAuthority)
        .collect(Collectors.toList());

    return Jwts.builder().subject(user.getUsername()).claim(ID_USER, ((User) user).getIdUser())
        .claim("idTenant", ((User) user).getIdTenant()).claim("localeStr", ((User) user).getLocaleStr())
        .claim("roles", roles).signWith(secretKey).expiration(Date.from(afterSomeMinutes.toInstant())).compact();
  }

}
