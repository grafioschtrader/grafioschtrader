package grafioschtrader.gtnet.model.msg;

public class ApplicationInfo {
  public String name;
  public String description;
  public String version;
  public Users Users;
  
  private static class Users {
    public int allowed;
    public int active;
  }
  
}
