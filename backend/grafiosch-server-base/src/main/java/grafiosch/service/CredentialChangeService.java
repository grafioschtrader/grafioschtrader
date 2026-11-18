package grafiosch.service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AccountStatusUserDetailsChecker;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.web.webauthn.api.CredentialRecord;

import grafiosch.BaseConstants;
import grafiosch.config.LimitKeyBaseConfig;
import grafiosch.entities.User;
import grafiosch.entities.UserAccessToken;
import grafiosch.entities.UserPasskey;
import grafiosch.entities.UserRecoveryCode;
import grafiosch.entities.UserTotp;
import grafiosch.exceptions.DataViolationException;
import grafiosch.exceptions.GeneralNotTranslatedWithArgumentsException;
import grafiosch.repository.GlobalparametersJpaRepository;
import grafiosch.repository.OAuthAuthorizationJpaRepository;
import grafiosch.repository.UserAccessTokenJpaRepository;
import grafiosch.repository.UserJpaRepository;
import grafiosch.repository.UserPasskeyJpaRepository;
import grafiosch.repository.UserPasswordResetJpaRepository;
import grafiosch.repository.UserRecoveryCodeJpaRepository;
import grafiosch.repository.UserTotpJpaRepository;
import grafiosch.rest.helper.RestHelper;
import grafiosch.security.totp.SecondFactorCode;
import grafiosch.types.OperationType;
import jakarta.persistence.EntityManager;

/** Atomic credential writes, session invalidation and notices, addressed by user id rather than the caller's tenant. */
@Service
public class CredentialChangeService {
  private final UserJpaRepository users;
  private final UserPasswordResetJpaRepository resets;
  private final UserAccessTokenJpaRepository tokens;
  private final OAuthAuthorizationJpaRepository authorizations;
  private final UserTotpJpaRepository totps;
  private final UserRecoveryCodeJpaRepository recoveryCodes;
  private final UserPasskeyJpaRepository passkeys;
  private final DailyLimitService dailyLimits;
  private final EntityLimitService entityLimits;
  private final GlobalparametersJpaRepository parameters;
  private final SecurityNoticeService notices;
  private final EntityManager entityManager;
  private final Pattern demoDE;
  private final Pattern demoEN;
  private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

  public CredentialChangeService(UserJpaRepository users, UserPasswordResetJpaRepository resets,
      UserAccessTokenJpaRepository tokens, OAuthAuthorizationJpaRepository authorizations,
      UserTotpJpaRepository totps, UserRecoveryCodeJpaRepository recoveryCodes, UserPasskeyJpaRepository passkeys,
      DailyLimitService dailyLimits, EntityLimitService entityLimits, GlobalparametersJpaRepository parameters,
      SecurityNoticeService notices, EntityManager entityManager,
      @Value("${g.demo.account.pattern.de}") String demoDE, @Value("${g.demo.account.pattern.en}") String demoEN) {
    this.users = users;
    this.resets = resets;
    this.tokens = tokens;
    this.authorizations = authorizations;
    this.totps = totps;
    this.recoveryCodes = recoveryCodes;
    this.passkeys = passkeys;
    this.dailyLimits = dailyLimits;
    this.entityLimits = entityLimits;
    this.parameters = parameters;
    this.notices = notices;
    this.entityManager = entityManager;
    this.demoDE = Pattern.compile(demoDE);
    this.demoEN = Pattern.compile(demoEN);
  }

  public boolean isResetEligible(User user) {
    return user.isEnabled() && !demoDE.matcher(user.getUsername()).matches()
        && !demoEN.matcher(user.getUsername()).matches();
  }

  private void checkPassword(String password) throws Exception {
    if (password == null || password.length() > 70
        || !password.matches(parameters.getPasswordRegexProperties().regex)) {
      throw new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH);
    }
  }

  @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
  public int changePassword(Integer idUser, String passwordOld, String passwordNew) throws Exception {
    // OSIV may contain the request's switched/stale User. Read a fresh locked home-user snapshot instead.
    User user = lockFresh(idUser);
    checkNotDemo(user);
    if (!encoder.matches(passwordOld, user.getPassword())) {
      throw new DataViolationException("oldpassword", "password.changed.old.wrong", null);
    }
    checkPassword(passwordNew);
    return writePassword(user, passwordNew, "security.event.password.changed");
  }

  /**
   * Null means unknown, expired, spent or ineligible. A failed policy check leaves the token intact. TOTP, recovery
   * codes and passkeys are not touched: a mailbox proof revokes bearer credentials only.
   */
  @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
  public Integer resetPassword(String token, String passwordNew) throws Exception {
    checkPassword(passwordNew);
    String hash = UserAccessToken.hashToken(token == null ? "" : token);
    var reset = resets.findByTokenHash(hash);
    if (reset.isEmpty()) {
      return null;
    }
    Integer idUser = reset.get().getIdUser();
    entityManager.clear();
    User user = users.lockById(idUser).orElse(null);
    if (user == null || !isResetEligible(user) || resets.consume(hash, LocalDateTime.now(ZoneOffset.UTC)) != 1) {
      return null;
    }
    int version = writePassword(user, passwordNew, "security.event.password.reset");
    tokens.deleteByIdUser(idUser);
    authorizations.deleteByIdUser(idUser);
    return version;
  }

  private int writePassword(User user, String password, String event) {
    users.updatePassword(user.getIdUser(), encoder.encode(password));
    return invalidateSessions(user, event);
  }

  /**
   * Ends every session of the user after a change to the set of credentials: the authentication version moves on, a
   * pending mailbox proof becomes void and the user receives a notice after the commit.
   *
   * @return the authentication version read in the same transaction, for the caller's replacement token
   */
  private int invalidateSessions(User user, String event) {
    // The version update clears the persistence context; the credential rows written before must reach it first
    entityManager.flush();
    users.incrementAuthVersion(user.getIdUser());
    resets.deleteByIdUser(user.getIdUser());
    notices.credentialChanged(user, event);
    return users.findById(user.getIdUser()).orElseThrow().getAuthVersion();
  }

  /** Demo accounts may neither change their sign-in nor enable a second factor. */
  public void checkNotDemo(User user) {
    RestHelper.isDemoAccount(demoDE.pattern(), user.getUsername());
    RestHelper.isDemoAccount(demoEN.pattern(), user.getUsername());
  }

  /** A fresh, locked snapshot of the user; the request's own user object may carry a switched tenant. */
  private User lockFresh(Integer idUser) {
    entityManager.clear();
    return users.lockById(idUser).orElseThrow();
  }

  /** Recovery codes in clear text, shown once, and the authentication version after the change. */
  public record RecoveryCodesChange(List<String> recoveryCodes, int authVersion) {
  }

  /**
   * Starts or replaces a TOTP setup. Only the pending secret changes, so the sessions stay and no notice is sent.
   *
   * @param idUser       the user
   * @param secretBase32 the new secret, shown to the user once
   */
  @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
  public void writePendingTotp(Integer idUser, String secretBase32) {
    User user = lockFresh(idUser);
    checkNotDemo(user);
    UserTotp totp = totps.findById(idUser).orElseGet(() -> new UserTotp(idUser));
    totp.replacePending(secretBase32, LocalDateTime.now(ZoneOffset.UTC));
    totps.save(totp);
  }

  /** Discards an expired setup; an enabled factor stays as it is. */
  @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
  public void clearPendingTotp(Integer idUser) {
    lockFresh(idUser);
    totps.findById(idUser).ifPresent(totp -> {
      if (totp.isEnabled()) {
        totp.clearPending();
        totps.save(totp);
      } else {
        totps.delete(totp);
      }
    });
  }

  /**
   * Makes the pending secret the active factor and replaces the recovery codes.
   *
   * @param idUser                the user
   * @param verifiedPendingSecret the pending secret the code was verified against; when a setup started meanwhile in
   *                              another window replaced it, nothing is enabled
   * @param confirmedStep         time step of the confirming code, which is spent with the enrolment
   * @return the new recovery codes and the authentication version after the change
   */
  @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
  public RecoveryCodesChange enableTotp(Integer idUser, String verifiedPendingSecret, long confirmedStep) {
    User user = lockFresh(idUser);
    checkNotDemo(user);
    UserTotp totp = totps.findById(idUser).orElse(null);
    if (totp == null || !verifiedPendingSecret.equals(totp.decryptPendingSecret())) {
      throw new GeneralNotTranslatedWithArgumentsException("secondfactor.setup.replaced", null);
    }
    totp.activatePending(confirmedStep, LocalDateTime.now(ZoneOffset.UTC));
    totps.save(totp);
    List<String> codes = replaceRecoveryCodes(idUser);
    return new RecoveryCodesChange(codes, invalidateSessions(user, "security.event.totp.enabled"));
  }

  /** Replaces all recovery codes of a user with an enabled factor; the previous ones become invalid. */
  @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
  public RecoveryCodesChange renewRecoveryCodes(Integer idUser) {
    User user = lockFresh(idUser);
    requireTotpEnabled(idUser);
    List<String> codes = replaceRecoveryCodes(idUser);
    return new RecoveryCodesChange(codes, invalidateSessions(user, "security.event.recovery.codes.renewed"));
  }

  /**
   * Removes the authenticator-app factor and the recovery codes on the user's own request.
   *
   * @return the authentication version after the change
   */
  @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
  public int disableTotp(Integer idUser) {
    User user = lockFresh(idUser);
    requireTotpEnabled(idUser);
    return removeTotp(user, "security.event.totp.disabled");
  }

  /**
   * Removes the second factor of another user on behalf of an administrator. Passkeys stay; the user's sessions end.
   * The caller receives no token.
   */
  @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
  public void removeSecondFactorByAdmin(Integer idUser) {
    User user = lockFresh(idUser);
    requireTotpEnabled(idUser);
    removeTotp(user, "security.event.secondfactor.removed.admin");
  }

  /**
   * Spends a recovery code and tells the user how many are left. The authentication version stays: spending a factor
   * is no change to the set of credentials.
   *
   * @param user     the user signing in or confirming an action
   * @param codeHash SHA-256 hash of the normalized code
   * @return true when the code was valid and is spent now
   */
  @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
  public boolean consumeRecoveryCode(User user, String codeHash) {
    if (recoveryCodes.consume(user.getIdUser(), codeHash, LocalDateTime.now(ZoneOffset.UTC)) != 1) {
      return false;
    }
    notices.credentialChanged(user, "security.event.recovery.code.used", recoveryCodes.countUnused(user.getIdUser()));
    return true;
  }

  /** A registered passkey and the authentication version after the change. */
  public record PasskeyChange(UserPasskey passkey, int authVersion) {
  }

  /**
   * Registers a passkey. Demo account, account status and the limits are checked again inside the transaction that
   * inserts the row, under the lock of the user, so parallel completions cannot exceed {@code UserPasskey}.
   *
   * @param idUser   the user
   * @param register verifies the credential and inserts it ({@code registerCredential} of the relying party)
   * @return the new passkey and the authentication version for the replacement token
   */
  @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
  public PasskeyChange addPasskey(Integer idUser, Supplier<CredentialRecord> register) {
    User user = lockFresh(idUser);
    checkNotDemo(user);
    new AccountStatusUserDetailsChecker().check(user);
    String entityName = UserPasskey.class.getSimpleName();
    dailyLimits.check(user, entityName, 1);
    if (!entityLimits.fitsWithinLimit(user, LimitKeyBaseConfig.KEY_USER_PASSKEY, null, 1)) {
      throw new GeneralNotTranslatedWithArgumentsException("MAX_USER_PASSKEY", null);
    }
    CredentialRecord record = register.get();
    dailyLimits.log(idUser, entityName, OperationType.ADD, 1);
    int authVersion = invalidateSessions(user, "security.event.passkey.added");
    return new PasskeyChange(passkeys.findByCredentialId(record.getCredentialId().getBytes()).orElseThrow(),
        authVersion);
  }

  /**
   * Deletes a passkey of the user. It removes access and leaves the password, so it needs no proof; it ends the other
   * sessions and sends a notice.
   *
   * @return the authentication version after the change
   */
  @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
  public int deletePasskey(Integer idUser, Integer idUserPasskey) {
    User user = lockFresh(idUser);
    String entityName = UserPasskey.class.getSimpleName();
    dailyLimits.check(user, entityName, 1);
    if (passkeys.deleteOwn(idUserPasskey, idUser) != 1) {
      throw new GeneralNotTranslatedWithArgumentsException("passkey.not.found", null);
    }
    dailyLimits.log(idUser, entityName, OperationType.DELETE, 1);
    return invalidateSessions(user, "security.event.passkey.deleted");
  }

  private void requireTotpEnabled(Integer idUser) {
    if (!totps.isEnabled(idUser)) {
      throw new GeneralNotTranslatedWithArgumentsException("secondfactor.not.enabled", null);
    }
  }

  private int removeTotp(User user, String event) {
    recoveryCodes.deleteByIdUser(user.getIdUser());
    totps.deleteByIdUser(user.getIdUser());
    return invalidateSessions(user, event);
  }

  private List<String> replaceRecoveryCodes(Integer idUser) {
    recoveryCodes.deleteByIdUser(idUser);
    List<String> codes = SecondFactorCode.newRecoveryCodes();
    recoveryCodes.saveAll(codes.stream()
        .map(code -> new UserRecoveryCode(idUser, UserAccessToken.hashToken(SecondFactorCode.parse(code).normalized())))
        .toList());
    return codes;
  }
}
