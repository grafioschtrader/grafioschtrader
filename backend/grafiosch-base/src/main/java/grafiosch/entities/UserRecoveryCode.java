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
 * A one-time recovery code that replaces the authenticator-app code when the device is lost. Only the SHA-256 hash of
 * the normalized code ({@link UserAccessToken#hashToken(String)}) is stored; the clear text is shown once when the
 * codes are created. A spent code keeps its row with {@code used_at} set, so the number left can be told.
 */
@Entity
@Table(name = UserRecoveryCode.TABNAME)
public class UserRecoveryCode {
  public static final String TABNAME = "user_recovery_code";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id_user_recovery_code")
  private Integer idUserRecoveryCode;

  @Column(name = "id_user", nullable = false)
  private Integer idUser;

  @JsonIgnore
  @Column(name = "code_hash", nullable = false, length = 64)
  private String codeHash;

  @Column(name = "used_at")
  private LocalDateTime usedAt;

  protected UserRecoveryCode() {
  }

  public UserRecoveryCode(Integer idUser, String codeHash) {
    this.idUser = idUser;
    this.codeHash = codeHash;
  }

  public Integer getIdUser() {
    return idUser;
  }

  public LocalDateTime getUsedAt() {
    return usedAt;
  }
}
