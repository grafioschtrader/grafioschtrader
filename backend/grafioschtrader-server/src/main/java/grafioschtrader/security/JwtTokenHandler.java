package grafioschtrader.security;

import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import grafioschtrader.entities.User;
import grafioschtrader.service.UserService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

@Component
@ConfigurationProperties(prefix = "gt.jwt")
public final class JwtTokenHandler {

  private static final String ID_USER = "idUser";
  /**
   * HS256 is used, the secret should at least be 32 characters long
   */
  private String secret;

  @Autowired
  private UserService userService;

  public void setSecret(String secret) {
    this.secret = secret;
  }

  Optional<UserDetails> parseUserFromToken(final String token) {
    final Claims jwsClaims = Jwts.parserBuilder().setSigningKey(secret.getBytes()).build().parseClaimsJws(token)
        .getBody();
    Integer userId = (Integer) jwsClaims.get(ID_USER);
    return Optional.ofNullable(userService.loadUserByUserIdAndCheckUsername(userId, jwsClaims.getSubject()));
  }

  public Integer getUserId(final String token) {
    final Claims jwsClaims = Jwts.parserBuilder().setSigningKey(secret.getBytes()).build().parseClaimsJws(token)
        .getBody();
    return (Integer) jwsClaims.get(ID_USER);
  }

  public String createTokenForUser(final UserDetails user) {
    final ZonedDateTime afterOneWeek = ZonedDateTime.now().plusWeeks(1);

    List<String> roles = user.getAuthorities().stream().map(GrantedAuthority::getAuthority)
        .collect(Collectors.toList());

    return Jwts.builder().setSubject(user.getUsername()).claim(ID_USER, ((User) user).getIdUser())
        .claim("idTenant", ((User) user).getIdTenant()).claim("localeStr", ((User) user).getLocaleStr())
        .claim("roles", roles).signWith(Keys.hmacShaKeyFor(secret.getBytes()), SignatureAlgorithm.HS256)
        .setExpiration(Date.from(afterOneWeek.toInstant())).compact();
  }

}
