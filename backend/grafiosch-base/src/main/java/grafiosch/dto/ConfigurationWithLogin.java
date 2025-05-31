package grafiosch.dto;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import grafiosch.BaseConstants;
import grafiosch.BaseConstants.UDFPrefixSuffix;
import grafiosch.entities.UDFData;
import grafiosch.types.UDFDataType;

/**
 * This data is returned with a successful login. It is very general data that can be used in different places in the
 * front end.
 */
public class ConfigurationWithLogin {
  /**
   * The front end may need to know the key field of the individual information classes.
   */
  public final List<EntityNameWithKeyName> entityNameWithKeyNameList;
  /**
   * Return of all members with the prefix “FIELD_SIZE” from the BaseConstants class and its derived classes.
   */
  public final Map<String, Integer> fieldSize;
  /**
   * Should an entity's ownership of shared data be visible. For example, a bold font in the table view.
   */
  public final boolean uiShowMyProperty;
  /**
   * The user role with the most rights.
   */
  public final String mostPrivilegedRole;
  /**
   * Regular expression to check the password for the security aspect. For example, minimum length of required
   * characters.
   */
  public final boolean passwordRegexOk;
  public final UDFConfig udfConfig = new UDFConfig();

  public ConfigurationWithLogin(List<EntityNameWithKeyName> entityNameWithKeyNameList, Map<String, Integer> fieldSize,
      boolean uiShowMyProperty, String mostPrivilegedRole, boolean passwordRegexOk) {
    this.entityNameWithKeyNameList = entityNameWithKeyNameList;
    this.fieldSize = fieldSize;
    this.uiShowMyProperty = uiShowMyProperty;
    this.mostPrivilegedRole = mostPrivilegedRole;
    this.passwordRegexOk = passwordRegexOk;
  }

  public static class EntityNameWithKeyName {
    public String entityName;
    public String keyName;

    public EntityNameWithKeyName(String entityName, String keyName) {
      this.entityName = entityName;
      this.keyName = keyName;
    }
  }

  public static class UDFConfig {
    public Set<String> udfGeneralSupportedEntities = UDFData.UDF_GENERAL_ENTITIES.stream().map(c -> c.getSimpleName())
        .collect(Collectors.toSet());
    public Map<UDFDataType, UDFPrefixSuffix> uDFPrefixSuffixMap = BaseConstants.uDFPrefixSuffixMap;
  }
}