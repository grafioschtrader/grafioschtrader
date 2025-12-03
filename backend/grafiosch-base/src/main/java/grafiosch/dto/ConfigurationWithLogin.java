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
 * Configuration data transfer object returned with successful login responses.
 * 
 * <p>
 * This DTO contains comprehensive configuration information that frontend applications need for proper operation after
 * successful authentication. It provides essential metadata about entities, field constraints, user preferences,
 * authorization levels, and system configuration that enables the client to operate correctly with the backend
 * services.
 * </p>
 * 
 * <h3>Configuration Categories:</h3>
 * <ul>
 * <li><strong>Entity Metadata:</strong> JPA entity names and primary key information</li>
 * <li><strong>Field Constraints:</strong> Size limits and validation parameters</li>
 * <li><strong>User Preferences:</strong> UI settings and display options</li>
 * <li><strong>Security Context:</strong> User roles and password policy status</li>
 * <li><strong>User-Defined Fields:</strong> Custom field configuration and support</li>
 * </ul>
 * 
 * <h3>Frontend Integration:</h3>
 * <p>
 * This data is very general and can be used in different places in the frontend for form validation, entity
 * manipulation, UI customization, and feature availability determination.
 * </p>
 */
public class ConfigurationWithLogin {
  /** The front end may need to know the key field of the individual information classes. */
  public final List<EntityNameWithKeyName> entityNameWithKeyNameList;

  /** Return of all members with the prefix “FIELD_SIZE” from the BaseConstants class and its derived classes. */
  public final Map<String, Integer> fieldSize;

  /** Should an entity's ownership of shared data be visible. For example, a bold font in the table view. */
  public final boolean uiShowMyProperty;

  /** The user's highest privilege role for authorization decisions. */
  public final String mostPrivilegedRole;

  /**
   * Regular expression to check the password for the security aspect. For example, minimum length of required
   * characters.
   */
  public final boolean passwordRegexOk;

  /**
   * Configuration for User-Defined Fields (UDF) functionality.
   * 
   * <p>
   * Contains comprehensive configuration for custom field support including which entities support user-defined fields
   * and the formatting rules for different UDF data types. This enables dynamic field management and customization
   * capabilities.
   * </p>
   */
  public final UDFConfig udfConfig = new UDFConfig();

  /**
   * Set of enabled features for controlling partially implemented functionality.
   *
   * <p>
   * Certain functionality is only partially implemented. Therefore, this should not be visible in the frontend. This
   * can be switched on or off through feature configuration to control user access to incomplete features.
   * </p>
   */
  public Set<? extends FeatureType> useFeatures;

  /**
   * Standard precision configuration for numeric field formatting.
   *
   * <p>
   * Filled with the standard decimal places of numbers, see definition of "FID_*" constants.
   * This mapping provides formatting precision for various numeric fields throughout the application.
   * </p>
   */
  public final Map<String, Integer> standardPrecision;

  /**
   * Creates a configuration object with essential login and system information.
   *
   * <p>
   * Constructs the configuration DTO with all necessary data for frontend operation including entity metadata, field
   * constraints, user preferences, authorization context, password policy status, and numeric formatting precision.
   * </p>
   *
   * @param entityNameWithKeyNameList list of entity names and their primary key fields
   * @param fieldSize                 map of field size constraints from application constants
   * @param uiShowMyProperty          flag for showing entity ownership in UI
   * @param mostPrivilegedRole        user's highest privilege role for authorization
   * @param passwordRegexOk           current password policy compliance status
   * @param standardPrecision         standard precision constants for field formatting
   */
  public ConfigurationWithLogin(List<EntityNameWithKeyName> entityNameWithKeyNameList, Map<String, Integer> fieldSize,
      boolean uiShowMyProperty, String mostPrivilegedRole, boolean passwordRegexOk, Map<String, Integer> standardPrecision) {
    this.entityNameWithKeyNameList = entityNameWithKeyNameList;
    this.fieldSize = fieldSize;
    this.uiShowMyProperty = uiShowMyProperty;
    this.mostPrivilegedRole = mostPrivilegedRole;
    this.passwordRegexOk = passwordRegexOk;
    this.standardPrecision = standardPrecision;
  }

  public static class EntityNameWithKeyName {
    /** The JPA entity name for database and API operations */
    public String entityName;
    /** The primary key field name for the entity. */
    public String keyName;

    public EntityNameWithKeyName(String entityName, String keyName) {
      this.entityName = entityName;
      this.keyName = keyName;
    }
  }

  /**
   * Configuration class for User-Defined Fields (UDF) functionality.
   * 
   * <p>
   * This class encapsulates all configuration related to the application's User-Defined Fields feature, including which
   * entities support custom fields and the formatting rules for different data types. This enables dynamic field
   * management and customization capabilities throughout the application.
   * </p>
   * 
   * <h3>UDF Capabilities:</h3>
   * <ul>
   * <li><strong>Entity Support:</strong> Identifies which entities can have custom fields</li>
   * <li><strong>Data Type Formatting:</strong> Provides formatting rules for different UDF types</li>
   * <li><strong>Dynamic Customization:</strong> Enables runtime field definition and management</li>
   * </ul>
   */
  public static class UDFConfig {
    /** Set of entity names that support User-Defined Fields. */
    public Set<String> udfGeneralSupportedEntities = UDFData.UDF_GENERAL_ENTITIES.stream().map(c -> c.getSimpleName())
        .collect(Collectors.toSet());

    /** Mapping of UDF data types to their formatting prefix and suffix rules. */
    public Map<UDFDataType, UDFPrefixSuffix> uDFPrefixSuffixMap = BaseConstants.uDFPrefixSuffixMap;
  }
  
  public static interface FeatureType {
    
  }
}