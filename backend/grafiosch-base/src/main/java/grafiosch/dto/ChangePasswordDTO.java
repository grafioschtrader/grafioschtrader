package grafiosch.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = """
    Request object for changing a user's password, containing both the current "
    password for verification and the new password to be applied""")
public class ChangePasswordDTO {
  @Schema(description = "The user's current password for verification")
  public String passwordOld;
  @Schema(description = "The new password to be set for the user")
  public String passwordNew;
}
