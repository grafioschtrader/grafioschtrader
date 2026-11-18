package grafiosch.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;
import java.util.OptionalLong;

import javax.imageio.ImageIO;

import org.jasypt.exceptions.EncryptionOperationNotPossibleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import grafiosch.entities.User;
import grafiosch.entities.UserAccessToken;
import grafiosch.entities.UserTotp;
import grafiosch.exceptions.GeneralNotTranslatedWithArgumentsException;
import grafiosch.repository.UserJpaRepository;
import grafiosch.repository.UserPasskeyJpaRepository;
import grafiosch.repository.UserRecoveryCodeJpaRepository;
import grafiosch.repository.UserTotpJpaRepository;
import grafiosch.security.totp.SecondFactorCode;
import grafiosch.security.totp.SecondFactorCode.Kind;
import grafiosch.security.totp.TotpGenerator;
import grafiosch.service.CredentialChangeService.RecoveryCodesChange;

/**
 * The authenticator-app second factor (TOTP) with its recovery codes: the check that the password sign-in runs before
 * it completes, and the proofs and steps of enrolling, renewing and disabling it. Writes go through
 * {@link CredentialChangeService}; wrong proofs count toward the per-user lock of {@link ProofAttemptService}.
 *
 * <p>
 * A code is spent by one conditional statement, so of two overlapping requests with the same code only one succeeds,
 * and a spent code stays spent when the request is refused afterwards for another reason.
 * </p>
 */
@Service
public class SecondFactorService {
  private static final Logger log = LoggerFactory.getLogger(SecondFactorService.class);
  /** A setup must be confirmed within this time, otherwise it is discarded. */
  public static final Duration SETUP_LIFETIME = Duration.ofMinutes(10);
  private static final int QR_SIZE = 240;

  private final UserTotpJpaRepository totps;
  private final UserRecoveryCodeJpaRepository recoveryCodes;
  private final UserPasskeyJpaRepository passkeys;
  private final UserJpaRepository users;
  private final CredentialChangeService credentials;
  private final ProofAttemptService proofs;
  private final TotpGenerator generator;
  private final String issuer;
  private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

  public SecondFactorService(UserTotpJpaRepository totps, UserRecoveryCodeJpaRepository recoveryCodes,
      UserPasskeyJpaRepository passkeys, UserJpaRepository users, CredentialChangeService credentials, ProofAttemptService proofs,
      TotpGenerator generator, @Value("${g.auth.display.name}") String issuer) {
    this.totps = totps;
    this.recoveryCodes = recoveryCodes;
    this.passkeys = passkeys;
    this.users = users;
    this.credentials = credentials;
    this.proofs = proofs;
    this.generator = generator;
    this.issuer = issuer;
  }

  /** Result of checking one entered code; a fault is refused like a wrong code but not counted. */
  private enum Proof {
    VALID, INVALID, FAULT
  }

  /** What the password sign-in has to do after the password was verified. */
  public enum SignInCheck {
    /** The account has no second factor. */
    NOT_REQUIRED,
    /** A valid code was given and is spent now. */
    PASSED,
    /** No code came with the request; nothing was counted. */
    ABSENT,
    /** The code was wrong, malformed or spent. */
    INVALID,
    /** Too many wrong proofs; nothing was checked. */
    LOCKED
  }

  /** What a new setup shows the user once: the secret as text, as URI and as QR code. */
  public record TotpSetup(String secretBase32, String otpauthUri, String qrCodePng) {
  }

  /** The state the sign-in security dialog shows. */
  public record SecondFactorState(boolean totpEnabled, int recoveryCodesLeft, int passkeyCount) {
  }

  /**
   * Checks the second factor of a password sign-in. Called after the password was verified and before the sign-in
   * completes; until it passes, no token is issued.
   *
   * @param user the user whose password was verified
   * @param code the code of the request, may be absent
   */
  public SignInCheck checkAtSignIn(User user, String code) {
    Integer idUser = user.getIdUser();
    if (!totps.isEnabled(idUser)) {
      return SignInCheck.NOT_REQUIRED;
    } else if (proofs.isLocked(idUser)) {
      return SignInCheck.LOCKED;
    } else if (SecondFactorCode.isAbsent(code)) {
      return SignInCheck.ABSENT;
    }
    return switch (spend(user, code, true)) {
      case VALID -> {
        proofs.succeeded(idUser);
        yield SignInCheck.PASSED;
      }
      case INVALID -> {
        proofs.failed(idUser);
        yield SignInCheck.INVALID;
      }
      case FAULT -> SignInCheck.INVALID;
    };
  }

  public SecondFactorState state(Integer idUser) {
    return new SecondFactorState(totps.isEnabled(idUser), recoveryCodes.countUnused(idUser),
        passkeys.countByIdUser(idUser));
  }

  /**
   * The proof for adding a sign-in method: the password and, while an authenticator app is enabled, a TOTP or recovery
   * code. Wrong passwords and codes count toward the per-user lock.
   *
   * @param idUser   the signed-in user
   * @param password the password of the user
   * @param code     a TOTP or recovery code, needed while a factor is enabled
   */
  public void requireProofForNewMethod(Integer idUser, String password, String code) {
    User user = users.findById(idUser).orElseThrow();
    boolean totpEnabled = totps.isEnabled(idUser);
    requirePassword(user, password, totpEnabled ? code : null, totpEnabled);
  }

  /**
   * Starts a setup or replaces an earlier one that was not confirmed. An enabled factor stays in force until the new
   * secret is confirmed.
   *
   * @param idUser   the signed-in user
   * @param password the password of the user
   * @param code     a TOTP or recovery code, needed while a factor is enabled
   */
  public TotpSetup startSetup(Integer idUser, String password, String code) {
    User user = users.findById(idUser).orElseThrow();
    credentials.checkNotDemo(user);
    requireProofForNewMethod(idUser, password, code);
    String secret = TotpGenerator.newSecret();
    credentials.writePendingTotp(idUser, secret);
    String uri = TotpGenerator.otpauthUri(issuer, user.getUsername(), secret);
    return new TotpSetup(secret, uri, qrCodePng(uri));
  }

  /**
   * Confirms a setup with a code of the new secret and enables it. The recovery codes are replaced.
   *
   * @param idUser the signed-in user
   * @param code   six digits computed from the pending secret
   * @return the new recovery codes and the authentication version for the replacement token
   */
  public RecoveryCodesChange enable(Integer idUser, String code) {
    User user = users.findById(idUser).orElseThrow();
    credentials.checkNotDemo(user);
    requireNotLocked(idUser);
    UserTotp totp = totps.findById(idUser).filter(UserTotp::hasPending)
        .orElseThrow(() -> new GeneralNotTranslatedWithArgumentsException("secondfactor.setup.missing", null));
    if (totp.getPendingCreatedAt().isBefore(LocalDateTime.now(ZoneOffset.UTC).minus(SETUP_LIFETIME))) {
      credentials.clearPendingTotp(idUser);
      throw new GeneralNotTranslatedWithArgumentsException("secondfactor.setup.expired", null);
    }
    String pending = decrypt(totp, true);
    SecondFactorCode parsed = SecondFactorCode.parse(code);
    OptionalLong step = pending == null || parsed.kind() != Kind.TOTP ? OptionalLong.empty()
        : generator.matchingStep(pending, parsed.normalized());
    if (step.isEmpty()) {
      if (pending != null && !SecondFactorCode.isAbsent(code)) {
        proofs.failed(idUser);
      }
      throw new GeneralNotTranslatedWithArgumentsException("secondfactor.code.invalid", null);
    }
    proofs.succeeded(idUser);
    return credentials.enableTotp(idUser, pending, step.getAsLong());
  }

  /** Replaces the recovery codes after a current code of the authenticator app. */
  public RecoveryCodesChange renewRecoveryCodes(Integer idUser, String code) {
    User user = users.findById(idUser).orElseThrow();
    requireEnabled(idUser);
    if (!SecondFactorCode.isAbsent(code) && SecondFactorCode.parse(code).kind() == Kind.RECOVERY) {
      throw new GeneralNotTranslatedWithArgumentsException("secondfactor.code.totp.required", null);
    }
    requireCode(user, code);
    return credentials.renewRecoveryCodes(idUser);
  }

  /** Disables the factor after the password and a TOTP or recovery code. */
  public int disable(Integer idUser, String password, String code) {
    User user = users.findById(idUser).orElseThrow();
    requireEnabled(idUser);
    requirePassword(user, password, code, true);
    return credentials.disableTotp(idUser);
  }

  private void requireEnabled(Integer idUser) {
    if (!totps.isEnabled(idUser)) {
      throw new GeneralNotTranslatedWithArgumentsException("secondfactor.not.enabled", null);
    }
  }

  private void requireNotLocked(Integer idUser) {
    if (proofs.isLocked(idUser)) {
      throw new GeneralNotTranslatedWithArgumentsException("secondfactor.locked", null);
    }
  }

  /**
   * Checks the password and, when demanded, a code. The password comes first, so a wrong password never spends a code.
   * A wrong password counts like a wrong code: a session must not become a way to search the password.
   */
  private void requirePassword(User user, String password, String code, boolean codeRequired) {
    requireNotLocked(user.getIdUser());
    if (password == null || !encoder.matches(password, user.getPassword())) {
      proofs.failed(user.getIdUser());
      throw new GeneralNotTranslatedWithArgumentsException("secondfactor.password.wrong", null);
    }
    if (codeRequired) {
      requireCode(user, code);
    } else {
      proofs.succeeded(user.getIdUser());
    }
  }

  private void requireCode(User user, String code) {
    Integer idUser = user.getIdUser();
    requireNotLocked(idUser);
    if (SecondFactorCode.isAbsent(code)) {
      throw new GeneralNotTranslatedWithArgumentsException("secondfactor.required", null);
    }
    Proof proof = spend(user, code, false);
    if (proof == Proof.VALID) {
      proofs.succeeded(idUser);
      return;
    } else if (proof == Proof.INVALID) {
      proofs.failed(idUser);
    }
    throw new GeneralNotTranslatedWithArgumentsException("secondfactor.code.invalid", null);
  }

  /**
   * Verifies and spends an entered code of the enabled factor.
   *
   * @param user   the user
   * @param input  the code as entered, not absent
   * @param signIn whether the check belongs to a sign-in, which logs a decryption fault with that context
   */
  private Proof spend(User user, String input, boolean signIn) {
    SecondFactorCode code = SecondFactorCode.parse(input);
    if (code.kind() == Kind.RECOVERY) {
      return credentials.consumeRecoveryCode(user, UserAccessToken.hashToken(code.normalized())) ? Proof.VALID
          : Proof.INVALID;
    } else if (code.kind() == Kind.MALFORMED) {
      return Proof.INVALID;
    }
    UserTotp totp = totps.findById(user.getIdUser()).filter(UserTotp::isEnabled).orElse(null);
    if (totp == null) {
      return Proof.INVALID;
    }
    String secret = decrypt(totp, false);
    if (secret == null) {
      log.error("TOTP secret of user {} cannot be decrypted ({}); was JASYPT_ENCRYPTOR_PASSWORD changed?",
          user.getIdUser(), signIn ? "sign-in" : "confirmation");
      return Proof.FAULT;
    }
    OptionalLong step = generator.matchingStep(secret, code.normalized());
    return step.isPresent() && totps.spendStep(user.getIdUser(), step.getAsLong()) == 1 ? Proof.VALID : Proof.INVALID;
  }

  /** Decrypts a secret; null when the encryption key no longer fits, which is an operating fault. */
  private String decrypt(UserTotp totp, boolean pending) {
    try {
      return pending ? totp.decryptPendingSecret() : totp.decryptSecret();
    } catch (EncryptionOperationNotPossibleException e) {
      if (pending) {
        log.error("Pending TOTP secret of user {} cannot be decrypted", totp.getIdUser());
      }
      return null;
    }
  }

  /** Draws the QR code of the URI as PNG and returns it Base64-encoded. */
  static String qrCodePng(String content) {
    try {
      BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, QR_SIZE, QR_SIZE,
          Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M, EncodeHintType.MARGIN, 2));
      BufferedImage image = new BufferedImage(matrix.getWidth(), matrix.getHeight(), BufferedImage.TYPE_BYTE_BINARY);
      for (int y = 0; y < matrix.getHeight(); y++) {
        for (int x = 0; x < matrix.getWidth(); x++) {
          image.setRGB(x, y, matrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF);
        }
      }
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      ImageIO.write(image, "png", out);
      return Base64.getEncoder().encodeToString(out.toByteArray());
    } catch (WriterException e) {
      throw new IllegalStateException("QR code cannot be drawn", e);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
