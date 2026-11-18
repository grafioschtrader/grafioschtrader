package grafiosch.entities;

import java.time.LocalDateTime;

import org.jasypt.encryption.StringEncryptor;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * The authenticator-app second factor (RFC 6238) of one user. It holds the active secret, which the password sign-in
 * checks while {@code enabled} is set, and beside it a pending secret of a setup that has not been confirmed yet. Only
 * a confirmed enrolment or a disabling changes the active secret, so an abandoned setup never weakens a protected
 * sign-in.
 *
 * <p>
 * Both secrets must be readable to compute codes and are therefore encrypted with
 * {@link ConnectorApiKey#stringEncryptor()}, keyed by {@code JASYPT_ENCRYPTOR_PASSWORD}. No getter exposes them to
 * Jackson and the class has no {@code toString} of its own. The entity is never sent over REST.
 * </p>
 */
@Entity
@Table(name = UserTotp.TABNAME)
public class UserTotp {
  public static final String TABNAME = "user_totp";

  private static final StringEncryptor ENCRYPTOR = ConnectorApiKey.stringEncryptor();

  @Id
  @Column(name = "id_user")
  private Integer idUser;

  @JsonIgnore
  @Column(name = "secret", length = 512)
  private String secret;

  @Column(name = "enabled", nullable = false)
  private boolean enabled;

  /** Time step of the last accepted code; a code of this or an earlier step is spent. */
  @Column(name = "last_used_step")
  private Long lastUsedStep;

  @Column(name = "enabled_at")
  private LocalDateTime enabledAt;

  @JsonIgnore
  @Column(name = "pending_secret", length = 512)
  private String pendingSecret;

  @Column(name = "pending_created_at")
  private LocalDateTime pendingCreatedAt;

  protected UserTotp() {
  }

  public UserTotp(Integer idUser) {
    this.idUser = idUser;
  }

  public Integer getIdUser() {
    return idUser;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public LocalDateTime getPendingCreatedAt() {
    return pendingCreatedAt;
  }

  /** Whether a setup waits for its confirmation. */
  public boolean hasPending() {
    return pendingSecret != null;
  }

  /**
   * Decrypts the active secret.
   *
   * @return the Base32 secret, or null when no factor is enabled
   * @throws org.jasypt.exceptions.EncryptionOperationNotPossibleException when the encryption key changed
   */
  public String decryptSecret() {
    return secret == null ? null : ENCRYPTOR.decrypt(secret);
  }

  /**
   * Decrypts the secret of a pending setup.
   *
   * @return the Base32 secret, or null when no setup is pending
   * @throws org.jasypt.exceptions.EncryptionOperationNotPossibleException when the encryption key changed
   */
  public String decryptPendingSecret() {
    return pendingSecret == null ? null : ENCRYPTOR.decrypt(pendingSecret);
  }

  /** Starts or replaces a setup; the active secret stays in force. */
  public void replacePending(String secretBase32, LocalDateTime now) {
    pendingSecret = ENCRYPTOR.encrypt(secretBase32);
    pendingCreatedAt = now;
  }

  public void clearPending() {
    pendingSecret = null;
    pendingCreatedAt = null;
  }

  /**
   * Makes the pending secret the active one. The code that confirmed it is spent with its step.
   *
   * @param confirmedStep time step of the confirming code
   * @param now           time of the enrolment, UTC
   */
  public void activatePending(long confirmedStep, LocalDateTime now) {
    secret = pendingSecret;
    enabled = true;
    lastUsedStep = confirmedStep;
    enabledAt = now;
    clearPending();
  }
}
