package grafioschtrader.rest;

public class AuthenticationModel {
  public String email;
  public String password;
  public Integer timezoneOffset;

  public AuthenticationModel(String email, String password, Integer timezoneOffset) {
    this.email = email;
    this.password = password;
    this.timezoneOffset = timezoneOffset;
  }

}
