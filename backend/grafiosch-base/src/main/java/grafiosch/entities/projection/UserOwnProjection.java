package grafiosch.entities.projection;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Reduced internal and external representation of user data.")
public class UserOwnProjection {
  public final Integer idUser;
  public final String email;
  public final String nickname;
  public final String localeStr;
  public final boolean uiShowMyProperty;

  public UserOwnProjection(Integer idUser, String email, String nickname, String localeStr, boolean uiShowMyProperty) {
    this.idUser = idUser;
    this.email = email;
    this.nickname = nickname;
    this.localeStr = localeStr;
    this.uiShowMyProperty = uiShowMyProperty;
  }

}
