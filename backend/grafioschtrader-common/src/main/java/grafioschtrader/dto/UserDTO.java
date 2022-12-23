package grafioschtrader.dto;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.fasterxml.jackson.annotation.JsonProperty;

import grafioschtrader.entities.Role;
import grafioschtrader.entities.User;
import jakarta.validation.constraints.Size;

/**
 * Never send this DTO back to the client, because it contains the password.
 *
 *
 * @author Hugo Graf
 *
 */
public final class UserDTO {

  private final String email;
  @Size(min = 1, max = 100)
  private final String password;
  private final String nickname;
  private final String localeStr;

  private final Integer timezoneOffset;
  private final String note;

  /**
   * It may be used for the login process.
   *
   * @param email
   * @param password
   * @param nickname
   * @param localeStr
   * @param timezoneOffset
   */
  public UserDTO(@JsonProperty("email") final String email, @JsonProperty("password") final String password,
      @JsonProperty("nickname") final String nickname, @JsonProperty("localeStr") final String localeStr,
      @JsonProperty("timezoneOffset") final Integer timezoneOffset, @JsonProperty("note") final String note) {
    this.email = email;
    this.password = password;
    this.nickname = nickname;
    this.localeStr = localeStr;
    this.timezoneOffset = timezoneOffset;
    this.note = note;
  }

  public Optional<String> getEmail() {
    return Optional.ofNullable(email);
  }

  public String getNote() {
    return note;
  }

  public Optional<String> getEncodedPassword() {
    return Optional.ofNullable(password).map(p -> new BCryptPasswordEncoder().encode(p));
  }

  public Optional<String> getNickname() {
    return Optional.ofNullable(nickname);
  }

  public String getLocaleStr() {
    return localeStr;
  }

  public Integer getTimezoneOffset() {
    return timezoneOffset;
  }

  public User toUser(List<Role> roles) {
    final User user = new User();
    user.setUsername(email);
    user.setRoles(roles);
    user.setPassword(new BCryptPasswordEncoder().encode(password));
    user.setNickname(nickname);
    user.setLocaleStr(localeStr);
    user.setTimezoneOffset(timezoneOffset);
    return user;
  }

  public UsernamePasswordAuthenticationToken toAuthenticationToken() {
    return new UsernamePasswordAuthenticationToken(email, password, getAuthorities());
  }

  public Collection<? extends GrantedAuthority> getAuthorities() {
    return Collections.singleton(() -> Role.ROLE_LIMIT_EDIT);
  }

}
