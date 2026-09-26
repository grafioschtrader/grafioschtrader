package grafiosch.gtnet.model.msg;

/**
 * Application information of a remote GTNet server instance, read from its actuator info endpoint.
 *
 * Provides basic metadata about the instance: name, description and version. The response only serves as a
 * reachability check before the first GTNet entry is stored, so further properties of the endpoint, such as the user
 * capacity under "users", are deliberately not mapped and ignored during deserialization.
 */
public class ApplicationInfo {
  public String name;
  public String description;
  public String version;
}
