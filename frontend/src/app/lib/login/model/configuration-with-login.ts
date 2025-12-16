/**
 * Base configuration data transfer object returned with successful login responses.
 *
 * <p>This interface contains comprehensive configuration information that frontend applications
 * need for proper operation after successful authentication. It provides essential metadata about
 * entities, field constraints, user preferences, authorization levels, and system configuration
 * that enables the client to operate correctly with the backend services.</p>
 */
export interface ConfigurationWithLogin {
  /**
   * Set of enabled features for controlling partially implemented functionality.
   * Certain functionality is only partially implemented and can be switched on or off
   * through feature configuration to control user access to incomplete features.
   */
  useFeatures: FeatureType[];

  /**
   * List of entity names and their primary key field names.
   * The frontend may need to know the key field of individual information classes.
   */
  entityNameWithKeyNameList: EntityNameWithKeyName[];

  /**
   * Standard precision configuration for numeric field formatting.
   * Filled with the standard decimal places of numbers, see definition of "FID_*" constants.
   * This mapping provides formatting precision for various numeric fields throughout the application.
   */
  standardPrecision: { [typename: string]: number };

  /**
   * Map of field size constraints from application constants.
   * Return of all members with the prefix "FIELD_SIZE" from the BaseConstants class and its derived classes.
   */
  fieldSize: { [fieldSize: string]: number };

  /**
   * Flag indicating whether an entity's ownership of shared data should be visible.
   * For example, a bold font in the table view.
   */
  uiShowMyProperty: boolean;

  /**
   * The user's highest privilege role for authorization decisions.
   */
  mostPrivilegedRole: string;

  /**
   * Current password policy compliance status.
   * Regular expression to check the password for the security aspect.
   * For example, minimum length of required characters.
   */
  passwordRegexOk: boolean;

  /**
   * Configuration for User-Defined Fields (UDF) functionality.
   * Contains comprehensive configuration for custom field support including which entities
   * support user-defined fields and the formatting rules for different UDF data types.
   */
  udfConfig: UDFConfig;
}

/**
 * Entity name and primary key field name pair.
 */
export interface EntityNameWithKeyName {
  /** The JPA entity name for database and API operations */
  entityName: string;
  /** The primary key field name for the entity */
  keyName: string;
}

/**
 * Configuration class for User-Defined Fields (UDF) functionality.
 *
 * <p>This interface encapsulates all configuration related to the application's User-Defined Fields
 * feature, including which entities support custom fields and the formatting rules for different
 * data types. This enables dynamic field management and customization capabilities throughout
 * the application.</p>
 */
export interface UDFConfig {
  /** Set of entity names that support User-Defined Fields */
  udfGeneralSupportedEntities: string[];
  /** Mapping of UDF data types to their formatting prefix and suffix rules */
  uDFPrefixSuffixMap: { [udfDatatype: string]: UDFPrefixSuffix };
}

/**
 * Prefix and suffix formatting configuration for User-Defined Fields.
 */
export interface UDFPrefixSuffix {
  /** Prefix formatting rule */
  prefix: number;
  /** Suffix formatting rule */
  suffix: number;
  /** Combined prefix and suffix formatting rule */
  together: number;
}

/**
 * Enum constants for feature types controlling partially implemented functionality.
 * Certain functionality is only partially implemented. Therefore, this should not be visible
 * in the frontend unless explicitly enabled.
 */
export enum FeatureType {
  /** Real-time data transmission. For example, the transmission of stock prices */
  WEBSOCKET,
  /** Algorithm for trading. For example, the automatic execution of a trading */
  ALGO,
  /**
   * Alarm for security and portfolio events. For example, if the price of a security
   * falls below a previously determined value.
   */
  ALERT,
  /**
   * GTNet peer-to-peer network for data sharing between Grafioschtrader instances.
   * Enables discovery of other instances, trust token exchange, data sharing negotiation,
   * and intraday price distribution.
   */
  GTNET
}
