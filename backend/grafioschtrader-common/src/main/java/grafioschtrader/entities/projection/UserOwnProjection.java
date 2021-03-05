package grafioschtrader.entities.projection;

public class UserOwnProjection {
  public Integer idUser;
  public String email;
  public String nickname;
  public String localeStr;

  public UserOwnProjection(Integer idUser, String email, String nickname, String localeStr) {
    super();
    this.idUser = idUser;
    this.email = email;
    this.nickname = nickname;
    this.localeStr = localeStr;
  }

}
