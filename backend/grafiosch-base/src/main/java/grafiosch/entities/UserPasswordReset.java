package grafiosch.entities;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One pending, hashed password-reset credential per user. Never exposed through a generic REST resource. */
@Entity
@Table(name = UserPasswordReset.TABNAME)
public class UserPasswordReset {
  public static final String TABNAME = "user_password_reset";

  @Id
  @Column(name = "id_user")
  private Integer idUser;

  @JsonIgnore
  @Column(name = "token_hash", nullable = false, length = 64)
  private String tokenHash;

  @Column(name = "expiry_date", nullable = false)
  private LocalDateTime expiryDate;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  protected UserPasswordReset() {
  }

  public UserPasswordReset(Integer idUser, String tokenHash, LocalDateTime now) {
    this.idUser = idUser;
    this.tokenHash = tokenHash;
    this.createdAt = now;
    this.expiryDate = now.plusMinutes(30);
  }

  public Integer getIdUser() {
    return idUser;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }
}
