package grafiosch.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request payload for a tenant owner granting another person read access to their own portfolio (home tenant). The
 * owner supplies the recipient's e-mail and, only when that e-mail is not yet registered, an initial password.
 *
 * <p>
 * Two branches result from this request: if the e-mail already belongs to a registered user, a read-only
 * {@code tenant_access} grant to the owner's tenant is created and the password is ignored; if the e-mail is unknown, a
 * new read-only viewer login is created (its home tenant is the owner's tenant) and the password is e-mailed to the
 * recipient as entered.
 * </p>
 */
@Schema(description = "Data a tenant owner enters to grant read access to their portfolio: the recipient's e-mail and, "
    + "for a not-yet-registered recipient, an initial password.")
public class ShareReadAccessRequest {

  @Schema(description = "The recipient's e-mail address; also used as the login name")
  @NotBlank
  @Size(min = 4, max = 255)
  private String email;

  @Schema(description = "Initial password, required only when the recipient is not yet registered; e-mailed as entered "
      + "and ignored for an already-registered recipient")
  @Size(max = 70)
  private String password;

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }
}
