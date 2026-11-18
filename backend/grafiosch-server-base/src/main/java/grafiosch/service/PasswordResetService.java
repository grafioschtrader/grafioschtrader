package grafiosch.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.entities.User;
import grafiosch.entities.UserAccessToken;
import grafiosch.entities.UserPasswordReset;
import grafiosch.repository.GlobalparametersJpaRepository;
import grafiosch.repository.UserJpaRepository;
import grafiosch.repository.UserPasswordResetJpaRepository;

/** Creates a bounded, short-lived mailbox proof without revealing whether the address belongs to an account. */
@Service
public class PasswordResetService {
  private static final SecureRandom RANDOM = new SecureRandom();
  private final UserJpaRepository users;
  private final UserPasswordResetJpaRepository resets;
  private final CredentialChangeService credentials;
  private final GlobalparametersJpaRepository parameters;
  private final SecurityNoticeService notices;
  private final MessageSource messages;
  private final String frontendPath;

  public PasswordResetService(UserJpaRepository users, UserPasswordResetJpaRepository resets,
      CredentialChangeService credentials, GlobalparametersJpaRepository parameters, SecurityNoticeService notices,
      MessageSource messages, @Value("${g.frontend.path}") String frontendPath) {
    this.users = users;
    this.resets = resets;
    this.credentials = credentials;
    this.parameters = parameters;
    this.notices = notices;
    this.messages = messages;
    this.frontendPath = frontendPath.replaceAll("/+$", "");
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public void request(String email) {
    var found = email == null ? Optional.<User>empty() : users.findByEmail(email);
    if (found.isEmpty()) {
      return;
    }
    User user = users.lockById(found.get().getIdUser()).orElseThrow();
    if (!credentials.isResetEligible(user)) {
      return;
    }
    LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
    var pending = resets.findById(user.getIdUser());
    if (pending.isPresent() && pending.get().getCreatedAt().isAfter(now.minusMinutes(5))) {
      return;
    }
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    resets.save(new UserPasswordReset(user.getIdUser(), UserAccessToken.hashToken(token), now));
    var base = parameters.getPublicBaseUrl();
    String link = base == null ? "" : base + frontendPath + "/passwordreset#token=" + token;
    var locale = user.createAndGetJavaLocale();
    notices.sendAfterCommit(user.getUsername(), messages.getMessage("password.reset.subject", null, locale),
        messages.getMessage("password.reset.body", new Object[] { token, link }, locale));
  }
}
