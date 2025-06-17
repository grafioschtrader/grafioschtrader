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

/**
 * JPA entity representing email verification tokens used in user registration workflows.
 * 
 * <p>
 * This entity manages verification tokens that are created during user registration to confirm email address ownership.
 * The verification process follows a three-step workflow:
 * </p>
 * 
 * <ol>
 * <li>A verification token is created during user registration</li>
 * <li>The token is sent to the user via email as a verification link</li>
 * <li>The user must click the link to verify their email within the expiration period</li>
 * </ol>
 * 
 * <h3>Token Management:</h3>
 * <ul>
 * <li><strong>Automatic Expiration:</strong> Tokens automatically expire after a configured time period to ensure
 * security</li>
 * <li><strong>User Association:</strong> Each token is uniquely associated with a specific user</li>
 * <li><strong>Secure Generation:</strong> Tokens should be cryptographically secure strings</li>
 * </ul>
 */
@Entity
@Table(name = "verificationtoken")
public class VerificationToken extends BaseID<Integer> {

  /**
   * Primary key identifier for the verification token.
   * 
   * <p>
   * Auto-generated using database identity strategy to ensure uniqueness across all verification tokens in the system.
   * </p>
   */
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Basic(optional = false)
  @Column(name = "id_verificationtoken")
  private Integer idVerificationToken;

  /**
   * The verification token string used for email verification.
   * 
   * <p>
   * This string is sent to users via email and must be provided back to the system to complete email verification. The
   * token should be cryptographically secure and difficult to guess.
   * </p>
   */
  private String token;

  /** The user to whom this token belongs */
  @OneToOne(targetEntity = User.class, fetch = FetchType.EAGER)
  @JoinColumn(nullable = false, name = "id_user", foreignKey = @ForeignKey(name = "FK_Verify_User"))
  private User user;

  /** The date and time when the token expires */
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

  /**
   * Calculates the expiration date for a verification token.
   * 
   * <p>
   * This method determines when a verification token should expire based on the current time and the specified
   * expiration period in minutes. The calculation adds the specified minutes to the current timestamp.
   * </p>
   * 
   * @param expiryTimeInMinutes the number of minutes from now when the token should expire
   * @return the calculated expiration date
   */
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
