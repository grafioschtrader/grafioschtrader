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

@Component
@ConfigurationProperties(prefix = "gt.jwt")
public final class JwtTokenHandler {

  private static final String ID_USER = "idUser";
  /**
   * HS256 is used, the secret should at least be 32 characters long
   */
  private SecretKey secretKey;

  @Autowired
  private UserService userService;

  public void setSecret(String secret) {
    this.secretKey = Keys.hmacShaKeyFor(secret.getBytes());
  }

  public Optional<UserDetails> parseUserFromToken(final String token) {
    try {
      final Claims jwsClaims = Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token)
          .getPayload();
      Integer userId = (Integer) jwsClaims.get(ID_USER);
      return Optional.ofNullable(userService.loadUserByUserIdAndCheckUsername(userId, jwsClaims.getSubject()));
    } catch (ExpiredJwtException e) {
      throw e;
    }
  }

  public Integer getUserId(final String token) {
    final Claims jwsClaims = Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token)
        .getPayload();
    return (Integer) jwsClaims.get(ID_USER);
  }

  public String createTokenForUser(final UserDetails user, int expirationMinutes) {
    final ZonedDateTime afterSomeMinutes = ZonedDateTime.now().plusMinutes(expirationMinutes);

    List<String> roles = user.getAuthorities().stream().map(GrantedAuthority::getAuthority)
        .collect(Collectors.toList());

    return Jwts.builder().subject(user.getUsername()).claim(ID_USER, ((User) user).getIdUser())
        .claim("idTenant", ((User) user).getIdTenant()).claim("localeStr", ((User) user).getLocaleStr())
        .claim("roles", roles).signWith(secretKey)
        .expiration(Date.from(afterSomeMinutes.toInstant())).compact();
  }

}
