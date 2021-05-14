package grafioschtrader.entities.projection;

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
