package grafiosch.entities;

import java.util.Calendar;
import java.util.Date;

import grafiosch.BaseConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Schema(description = """
    1. a verification token is created with the registration.
    2. this verification token is sent to the user by e-mail as a link.
    3. this token must be verified with this link within a certain period of time.""")
@Entity
@Table(name = "verificationtoken")
public class VerificationToken extends BaseID<Integer> {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Basic(optional = false)
  @Column(name = "id_verificationtoken")
  private Integer idVerificationToken;

  private String token;

  @Schema(description = "The user to whom this token belongs")
  @OneToOne(targetEntity = User.class, fetch = FetchType.EAGER)
  @JoinColumn(nullable = false, name = "id_user", foreignKey = @ForeignKey(name = "FK_Verify_User"))
  private User user;

  @Schema(description = "The date and time when the token expires")
  @Column(name = "expiry_date")
  private Date expiryDate;

  public VerificationToken() {
    super();
  }

  public VerificationToken(final String token) {
    super();
    this.token = token;
    this.expiryDate = calculateExpiryDate(BaseConstants.EMAIL_VERIFICATION_EXPIRATION_MINUTES);
  }

  public VerificationToken(final String token, final User user) {
    super();
    this.token = token;
    this.user = user;
    this.expiryDate = calculateExpiryDate(BaseConstants.EMAIL_VERIFICATION_EXPIRATION_MINUTES);
  }

  public Integer getIdVerificationToken() {
    return idVerificationToken;
  }

  public String getToken() {
    return token;
  }

  public void setToken(final String token) {
    this.token = token;
  }

  public User getUser() {
    return user;
  }

  public void setUser(final User user) {
    this.user = user;
  }

  public Date getExpiryDate() {
    return expiryDate;
  }

  public void setExpiryDate(final Date expiryDate) {
    this.expiryDate = expiryDate;
  }

  private Date calculateExpiryDate(final int expiryTimeInMinutes) {
    final Calendar cal = Calendar.getInstance();
    cal.setTimeInMillis(new Date().getTime());
    cal.add(Calendar.MINUTE, expiryTimeInMinutes);
    return new Date(cal.getTime().getTime());
  }

  public void updateToken(final String token) {
    this.token = token;
    this.expiryDate = calculateExpiryDate(BaseConstants.EMAIL_VERIFICATION_EXPIRATION_MINUTES);
  }

  @Override
  public Integer getId() {
    return idVerificationToken;
  }

  @Override
  public String toString() {
    final StringBuilder builder = new StringBuilder();
    builder.append("Token [String=").append(token).append("]").append("[Expires").append(expiryDate).append("]");
    return builder.toString();
  }

}
