export class BaseSettings {
  public static readonly API_ENDPOINT = '/api/';

  public static readonly ACTUATOR = 'actuator';

  public static readonly GT_NET = 'GTNet';

  // Native formats
  public static readonly FORMAT_DATE_SHORT_NATIVE = 'YYYY-MM-DD';
  public static readonly FORMAT_DATE_SHORT_US: string  = 'YYYYMMDD';

  // Name for Icons
  public static readonly ICONNAME_SQUARE_EMTPY = 'fa fa-square-o';
  public static readonly ICONNAME_SQUARE_CHECK = 'fa fa-check-square-o';

  /** Some menu items reference a dialog. If this is the case, the menu item should end with "...". */
  public static readonly DIALOG_MENU_SUFFIX = '...';

  /**
   * Shared data may have been created by different users. The own entities should be recognizable in tables.
   * For example, a particular property of one of the entities is displayed in bold.
   */
  public static readonly OWNER_TEMPLATE = 'owner';


  public static FID_MAX_LETTERS = 1000;

  public static readonly FIELD_SUFFIX = '$';

  public static FID_MAX_FRACTION_DIGITS = 8;
  public static FID_STANDARD_FRACTION_DIGITS = 2;

  public static readonly CSV_EXPORT_FORMAT = 'csv_export_format';

  // User Rights
  public static readonly ROLE_ADMIN = 'ROLE_ADMIN';
  public static readonly ROLE_ALL_EDIT = 'ROLE_ALLEDIT';
  public static readonly ROLE_USER = 'ROLE_USER';
  public static readonly ROLE_LIMIT_EDIT = 'ROLE_LIMITEDIT';

  // This is not a real role. This is used to send a message to everyone.
  public static readonly ROLE_EVERY_USER = 'ROLE_EVERY_USER';

  public static readonly TIMESZONES_P_KEY = 'timezones';

  public static readonly LOCALES_P_KEY = 'locales';

  public static readonly MAINVIEW_KEY = 'mainview';
  public static readonly REGISTER_KEY = 'register';
  public static readonly LOGIN_KEY = 'login';

  public static readonly USER = 'User';
  public static readonly USER_KEY = BaseSettings.USER.toLowerCase();
  public static readonly USER_ADMIN_KEY = 'useradmin';

  public static readonly CONNECTOR_API_KEY = 'ConnectorApiKey';

  public static readonly CONNECTOR_API_KEY_KEY = 'connectorapikey';

  public static readonly GLOBALPARAMETERS = 'Globalparameters';
  public static readonly GLOBALPARAMETERS_P_KEY = BaseSettings.GLOBALPARAMETERS.toLowerCase();

  public static readonly UDF_METADATA_GENERAL = 'UDFMetadataGeneral';
  public static readonly UDF_METADATA_GENERAL_KEY = BaseSettings.UDF_METADATA_GENERAL.toLowerCase()

  public static readonly GLOBAL_SETTINGS_KEY = 'globalsettings';
  public static readonly TASK_DATA_CHANGE_MONITOR_KEY = 'taskdatachangemonitor';

  public static readonly GT_NET_KEY = 'gtnet';
  public static readonly GT_NET_MESSAGE_ANSWER = 'GTNetMessageAnswer';
  public static readonly GT_NET_MESSAGE_ANSWER_KEY = 'gtnetmessageanswer';
  public static readonly GT_NET_SETUP_KEY = 'gtnetsetup';
  public static readonly GT_NET_ADMIN_MESSAGES_KEY = 'gtnetadminmessages';
  public static readonly GT_NET_CONFIG_ENTITY_KEY = 'gtnetconfigentity';
  public static readonly GT_NET_MESSAGE_KEY = 'gtnetmessage';

  public static readonly PROPOSE_CHANGE_ENTITY = 'ProposeChangeEntity';
  public static readonly PROPOSE_CHANGE_ENTITY_KEY = BaseSettings.PROPOSE_CHANGE_ENTITY.toLowerCase();

  public static readonly UDF_DATA = 'UDFData';
  public static readonly UDF_DATA_KEY = BaseSettings.UDF_DATA.toLowerCase();
  public static readonly UDF_SPECIAL_TYPE_DISABLE_USER = 'udfspecialtypedisableuser';

  public static readonly USER_CHART_SHAPE_KEY = 'userchartshape';


  public static readonly PROPOSE_CHANGE_YOUR_PROPOSAL_KEY = 'proposeyourproposal';
  public static readonly PROPOSE_CHANGE_REQUEST_FOR_YOU_KEY = 'proposerequestforyou';

  public static readonly PROPOSE_USER_TASK = 'ProposeUserTask';
  public static readonly PROPOSE_USER_TASK_KEY = BaseSettings.PROPOSE_USER_TASK.toLowerCase();

  public static readonly USER_ENTITY_LIMIT = 'UserEntityChangeLimit';
  public static readonly USER_ENTITY_LIMIT_KEY = BaseSettings.USER_ENTITY_LIMIT.toLowerCase();

  public static readonly TASK_DATE_CHANGE = 'TaskDataChange';
  public static readonly TASK_DATA_CHANGE_KEY = 'taskdatachange';

  public static readonly TENANT = 'Tenant';
  public static readonly TENANT_KEY = BaseSettings.TENANT.toLowerCase();

  public static readonly MAIL_SEND_RECV = 'MailSendRecv';
  public static readonly MAIL_SETTING_FORWARD = 'MailSettingForward';

  public static readonly MAIL_SEND_RECV_KEY = BaseSettings.MAIL_SEND_RECV.toLowerCase();
  public static readonly MAIL_SETTING_FORWARD_KEY = BaseSettings.MAIL_SETTING_FORWARD.toLowerCase();

  public static readonly PATH_ASSET_ICONS = 'assets/icons/';

  // SVG file extension
  public static readonly SVG = '.svg';

  /**
   * Resets integer and fraction limit constants from backend configuration.
   *
   * This method synchronizes frontend validation constants with backend-defined limits received during login.
   * It dynamically overwrites static properties with values from the backend's GlobalConstants and BaseConstants.
   * Properties that exist in BaseSettings are set on BaseSettings, while properties that don't exist in
   * BaseSettings are set on the provided targetObject.
   *
   * Backend Flow:
   * During login, TokenAuthenticationService.getConfigurationWithLogin() extracts all static fields starting
   * with "FID" prefix using reflection:
   * - FID_MAX_LETTERS (1000) - Maximum characters for note fields
   * - FID_MAX_DIGITS (16) - Maximum total digits in numeric fields
   * - FID_MAX_FRACTION_DIGITS (8) - Maximum decimal places for precision
   * - FID_STANDARD_FRACTION_DIGITS (2) - Default decimal places
   * - FID_MAX_CURRENCY_EX_RATE_PRECISION (20) - Currency exchange rate precision
   * - FID_MAX_CURRENCY_EX_RATE_FRACTION (10) - Currency exchange rate decimals
   * These constants are sent to frontend in the standardPrecision map of ConfigurationWithLoginGT.
   *
   * Frontend Flow:
   * LoginService.afterSuccessfulLogin() stores the map in sessionStorage under
   * GlobalSessionNames.STANDARD_CURRENCY_PRECISIONS_AND_LIMITS, then immediately calls this method to apply the values.
   *
   * Dynamic Property Overwriting:
   * YES, this method DOES overwrite properties! The code "BaseSettings[key] = value" or
   * "targetObject[key] = value" dynamically updates static class properties at runtime.
   * For example, if backend sends FID_MAX_FRACTION_DIGITS: 8, then BaseSettings.FID_MAX_FRACTION_DIGITS
   * is set to 8. If it sends FID_MAX_DIGITS: 22, then targetObject.FID_MAX_DIGITS is set to 22.
   *
   * Timing:
   * Called once during login after sessionStorage is populated. Effects last entire user session
   * until logout clears sessionStorage.
   *
   * @param targetObject Object to receive properties that don't exist in BaseSettings (e.g., AppSettings)
   * @param sessionStorageKey SessionStorage key where the standardPrecision map is stored
   */
  public static resetInterFractionLimit(targetObject: any, sessionStorageKey: string): void {
    const standardPrecisionMap: { [typename: string]: number } = JSON.parse(sessionStorage.getItem(sessionStorageKey));
    if (standardPrecisionMap) {
      const keys = Object.keys(standardPrecisionMap);
      keys.forEach(key => {
        const value = standardPrecisionMap[key.toString()];
        if (BaseSettings.hasOwnProperty(key)) {
          BaseSettings[key] = value;
        } else {
          targetObject[key] = value;
        }
      });
    }
  }

}
