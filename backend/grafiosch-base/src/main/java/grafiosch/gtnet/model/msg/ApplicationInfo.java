package grafiosch.gtnet.model.msg;

/**
 * Application information included in handshake messages.
 *
 * Provides basic metadata about the GTNet server instance including
 * name, description, version, and user capacity information.
 */
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
