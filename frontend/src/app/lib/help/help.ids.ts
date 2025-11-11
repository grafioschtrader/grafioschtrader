/**
 * Help IDs for the application.
 * This object starts with library-specific help IDs and can be extended
 * at application startup with application-specific IDs.
 *
 * To extend with application-specific help IDs, use:
 * Object.assign(HelpIds, YourAppHelpIds);
 */
export const HelpIds: Record<string, string> = {
  // Intro
  HELP_INTRO: 'intro',
  HELP_INTRO_REGISTER: 'intro/register',
  HELP_INTRO_NAVIGATION: 'intro/userinterface',
  HELP_INTRO_PROPOSE_CHANGE_ENTITY: 'basedata',

  // Base data
  HELP_BASEDATA_UDF_METADATA_GENERAL: 'basedata/udfmetadata',

  // Admin data
  HELP_MESSAGE_SYSTEM: 'admindata',
  HELP_GLOBAL_SETTINGS: 'admindata/globalsettings',
  HELP_TASK_DATA_CHANGE_MONITOR: 'admindata/taskdatachangemonitor',
  HELP_CONNECTOR_API_KEY: 'admindata/connectorapikey',
  HELP_USER: 'admindata/user'
};

/**
 * Registers additional help IDs.
 * This should be called at application startup to add application-specific help IDs.
 *
 * @param additionalIds Object containing additional help ID mappings
 */
export function registerHelpIds(additionalIds: Record<string, string>): void {
  Object.assign(HelpIds, additionalIds);
}
