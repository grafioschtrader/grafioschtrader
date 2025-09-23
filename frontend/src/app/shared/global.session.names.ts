export enum GlobalSessionNames {



  ID_TENANT = 'idTenant',
  ID_USER = 'idUser',
  LOCALE = 'locale',
  DECIMAL_SEPARATOR = 'decimalSeparator',
  THOUSANDS_SEPARATOR = 'thousandsSeparator',
  LANGUAGE = 'language',
  JWT = 'jwt',
  ROLES = 'roles',
  /** In certain reports there is a “to date”, this can be set by the user. It is stored in the session storage so that
   *  it works across all reports.
   */
  REPORT_UNTIL_DATE = 'untilDate',
  ENTITY_KEY_MAPPING = 'entityKeyMapping',
  USE_FEATURES = 'useFeatures',

  /** All members with the prefix “FIELD_SIZE” from the BaseConstants class and its derived classes. */
  FIELD_SIZE = 'fieldSize',

  /**
   * Some currencies will not have two decimal places. These will be stored under this key.
   */
  CURRENCY_PRECISION = 'currencyPrecision',

  /** Should an entity's ownership of shared data be visible. For example, a bold font in the table view. */
  UI_SHOW_MY_PROPERTY = 'uiShowMyProperty',
  USER_FORM_DEFINITION = 'userFormDefinition',

  /** The user's highest privilege role for authorization decisions. */
  MOST_PRIVILEGED_ROLE = 'mostPrivilegedRole',
  UDF_FORM_DESCRIPTOR_SECURITY = 'udfFormDescriptorSecurity',
  UDF_FORM_DESCRIPTOR_GENERAL = 'udfFormDescriptorGeneral',
  UDF_CONFIG = 'udfConfig'
}


