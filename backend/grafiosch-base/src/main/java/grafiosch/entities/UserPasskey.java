package grafiosch.entities;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A passkey (WebAuthn credential) of a user. The public key and the attestation object of the registration are stored;
 * the private key never leaves the authenticator. A passkey is bound to the relying party id, the host name of
 * {@code g.public.base.url}, and becomes unusable when that host name changes.
 *
 * <p>
 * The signature counter is enforced by a conditional update at every sign-in: authenticators that count must count
 * upwards, synchronized passkeys keep the constant zero. The backup flags hold the values of the registration. The
 * entity is never sent over REST; the dialog receives a {@code UserPasskeyDto}.
 * </p>
 */
@Entity
@Table(name = UserPasskey.TABNAME)
public class UserPasskey extends UserBaseID {
  public static final String TABNAME = "user_passkey";
  public static final int LABEL_MAX_LENGTH = 40;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id_user_passkey")
  private Integer idUserPasskey;

  @Column(name = "id_user", nullable = false)
  private Integer idUser;

  @Column(name = "label", nullable = false, length = LABEL_MAX_LENGTH)
  private String label;

  @JsonIgnore
  @Column(name = "credential_id", nullable = false, length = 1023)
  private byte[] credentialId;

  @JsonIgnore
  @Column(name = "public_key", nullable = false)
  private byte[] publicKey;

  @Column(name = "signature_count", nullable = false)
  private long signatureCount;

  @Column(name = "uv_initialized", nullable = false)
  private boolean uvInitialized;

  @Column(name = "backup_eligible", nullable = false)
  private boolean backupEligible;

  @Column(name = "backup_state", nullable = false)
  private boolean backupState;

  /** Comma separated WebAuthn transport hints such as {@code internal,hybrid}. */
  @Column(name = "transports", length = 100)
  private String transports;

  @JsonIgnore
  @Column(name = "attestation_object", nullable = false)
  private byte[] attestationObject;

  @JsonIgnore
  @Column(name = "attestation_client_data_json")
  private byte[] attestationClientDataJson;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "last_used_at")
  private LocalDateTime lastUsedAt;

  protected UserPasskey() {
  }

  public UserPasskey(Integer idUser, String label, byte[] credentialId, byte[] publicKey, long signatureCount,
      boolean uvInitialized, boolean backupEligible, boolean backupState, String transports, byte[] attestationObject,
      byte[] attestationClientDataJson, LocalDateTime createdAt) {
    this.idUser = idUser;
    this.label = label;
    this.credentialId = credentialId;
    this.publicKey = publicKey;
    this.signatureCount = signatureCount;
    this.uvInitialized = uvInitialized;
    this.backupEligible = backupEligible;
    this.backupState = backupState;
    this.transports = transports;
    this.attestationObject = attestationObject;
    this.attestationClientDataJson = attestationClientDataJson;
    this.createdAt = createdAt;
  }

  @Override
  public Integer getId() {
    return idUserPasskey;
  }

  public Integer getIdUserPasskey() {
    return idUserPasskey;
  }

  @Override
  public Integer getIdUser() {
    return idUser;
  }

  @Override
  public void setIdUser(Integer idUser) {
    this.idUser = idUser;
  }

  public String getLabel() {
    return label;
  }

  public byte[] getCredentialId() {
    return credentialId;
  }

  public byte[] getPublicKey() {
    return publicKey;
  }

  public long getSignatureCount() {
    return signatureCount;
  }

  public boolean isUvInitialized() {
    return uvInitialized;
  }

  public boolean isBackupEligible() {
    return backupEligible;
  }

  public boolean isBackupState() {
    return backupState;
  }

  public String getTransports() {
    return transports;
  }

  public byte[] getAttestationObject() {
    return attestationObject;
  }

  public byte[] getAttestationClientDataJson() {
    return attestationClientDataJson;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public LocalDateTime getLastUsedAt() {
    return lastUsedAt;
  }
}
