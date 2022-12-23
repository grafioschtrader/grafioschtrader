package grafioschtrader.entities;

import java.util.Calendar;
import java.util.Date;

import grafioschtrader.GlobalConstants;
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

@Entity
@Table(name = "verificationtoken")
public class VerificationToken extends BaseID {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Basic(optional = false)
  @Column(name = "id_verificationtoken")
  private Integer idVerificationToken;

  private String token;

  @OneToOne(targetEntity = User.class, fetch = FetchType.EAGER)
  @JoinColumn(nullable = false, name = "id_user", foreignKey = @ForeignKey(name = "FK_Verify_User"))
  private User user;

  @Column(name = "expiry_date")
  private Date expiryDate;

  public VerificationToken() {
    super();
  }

  public VerificationToken(final String token) {
    super();
    this.token = token;
    this.expiryDate = calculateExpiryDate(GlobalConstants.EMAIL_VERIFICATION_EXPIRATION_MINUTES);
  }

  public VerificationToken(final String token, final User user) {
    super();
    this.token = token;
    this.user = user;
    this.expiryDate = calculateExpiryDate(GlobalConstants.EMAIL_VERIFICATION_EXPIRATION_MINUTES);
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
    this.expiryDate = calculateExpiryDate(GlobalConstants.EMAIL_VERIFICATION_EXPIRATION_MINUTES);
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
