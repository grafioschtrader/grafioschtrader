package grafiosch.entities.projection;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Reduced internal and external representation of user data.")
public class UserOwnProjection {
  @Schema(description = "Unique identifier of the user")
  public final Integer idUser;
  @Schema(description = "User's email address used for authentication and communication")
  public final String email;
  @Schema(description = "Contains the nickname. Could possibly be used as a name for other users of this instance. It is unambiguous.")
  public final String nickname;
  @Schema(description = "User's preferred locale for internationalization (language and region)")
  public final String localeStr;
  @Schema(description = "Show if an entity was created by my in the user interface")
  public final boolean uiShowMyProperty;

  public UserOwnProjection(Integer idUser, String email, String nickname, String localeStr, boolean uiShowMyProperty) {
    this.idUser = idUser;
    this.email = email;
    this.nickname = nickname;
    this.localeStr = localeStr;
    this.uiShowMyProperty = uiShowMyProperty;
  }

}
