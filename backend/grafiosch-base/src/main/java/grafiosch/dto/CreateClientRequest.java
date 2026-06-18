package grafiosch.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request payload for an advisor creating a managed client: a new tenant with a read-only client login. The advisor
 * supplies the client's login e-mail and the initial password; the password is e-mailed to the client as entered.
 */
@Schema(description = "Data an advisor enters to create a managed client: the client's login e-mail and password.")
public class CreateClientRequest {

  @Schema(description = "The client's e-mail address; also used as the login name")
  @NotBlank
  @Size(min = 4, max = 255)
  private String email;

  @Schema(description = "Initial password for the client login; e-mailed to the client as entered")
  @NotBlank
  @Size(min = 1, max = 70)
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
