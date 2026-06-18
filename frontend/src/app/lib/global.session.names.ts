/**
 * SessionStorage keys for application-wide session data.
 * Values are stored during login and persist throughout the user session.
 */
export enum GlobalSessionNames {

  /** Tenant ID for multi-tenancy support. */
  ID_TENANT = 'idTenant',

  /** Current user's ID. */
  ID_USER = 'idUser',

  /** User's locale string (e.g., "en_US", "de_DE") for internationalization. */
  LOCALE = 'locale',

  /** Decimal separator character for number formatting (e.g., "." or ","). */
  DECIMAL_SEPARATOR = 'decimalSeparator',

  /** Thousands separator character for number formatting (e.g., "," or "."). */
  THOUSANDS_SEPARATOR = 'thousandsSeparator',

  /** Two-letter language code (e.g., "en", "de"). */
  LANGUAGE = 'language',

  /** JSON Web Token for stateless authentication. */
  JWT = 'jwt',

  /** User's assigned roles as comma-separated string. */
  ROLES = 'roles',

  /** Map of entity names to their primary key field names for dynamic entity manipulation. */
  ENTITY_KEY_MAPPING = 'entityKeyMapping',

  /** Set of enabled features for controlling partially implemented functionality. */
  USE_FEATURES = 'useFeatures',

  /** All members with the prefix "FIELD_SIZE" from the BaseConstants class and its derived classes. */
  FIELD_SIZE = 'fieldSize',

  STANDARD_TIMEZONE = 'standardTimeZone',

  /** Should an entity's ownership of shared data be visible. For example, a bold font in the table view. */
  UI_SHOW_MY_PROPERTY = 'uiShowMyProperty',

  /** User-specific form field definitions and configurations. */
  USER_FORM_DEFINITION = 'userFormDefinition',

  /** The user's highest privilege role for authorization decisions. */
  MOST_PRIVILEGED_ROLE = 'mostPrivilegedRole',

  /** User-defined field form descriptors for security entities. */
  UDF_FORM_DESCRIPTOR_SECURITY = 'udfFormDescriptorSecurity',

  /** User-defined field form descriptors for general entities. */
  UDF_FORM_DESCRIPTOR_GENERAL = 'udfFormDescriptorGeneral',

  /** Configuration for user-defined fields including supported entities and data type formatting. */
  UDF_CONFIG = 'udfConfig',

  /**
   * Stores the limits and definitions of decimal numbers and the maximum length for entering comments.
   * Contains FID constants from backend (e.g., FID_MAX_FRACTION_DIGITS, FID_MAX_DIGITS).
   */
  STANDARD_CURRENCY_PRECISIONS_AND_LIMITS = 'standardPrecision',

  /**
   * Map of currency code to the number of decimal places for amounts in that currency (e.g. BTC=8, JPY=0).
   * Currencies not contained in the map use the standard fraction digits.
   */
  CURRENCY_PRECISION = 'currencyPrecision',

  /** Base URL for external help documentation (e.g., "//APP.github.io/gt-user-manual"). */
  EXTERNAL_HELP_URL = 'externalHelpUrl',

  /** Stores the user's main tenant ID when switched to a simulation tenant. */
  MAIN_ID_TENANT = 'mainIdTenant',

  /**
   * Whether the user has read-only access to the tenant they currently operate in (a managed client account, or an
   * advisor switched into a READ-level tenant). When 'true' the frontend hides create/update/delete actions. Set at
   * login and refreshed on every tenant switch. The backend write-blocking filter is the actual guarantee.
   */
  TENANT_READ_ONLY = 'tenantReadOnly',

  /** Earliest trading day that GT supports for transactions and historical prices (e.g. "2000-01-03"). */
  OLDEST_TRADING_DAY = 'oldestTradingDay',

  /** Whether GTNet exchange logging is globally enabled. */
  GT_NET_LOG_ENABLED = 'gtNetLogEnabled',

  /** Whether at least one GTNet peer is configured to exchange historical price data. */
  GT_NET_HAS_HISTORICAL_PEER = 'gtNetHasHistoricalExchangePeer',

  /** Whether at least one GTNet peer is configured to exchange intraday (last price) data. */
  GT_NET_HAS_LASTPRICE_PEER = 'gtNetHasLastpriceExchangePeer'
}


