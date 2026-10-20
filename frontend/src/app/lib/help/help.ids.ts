/**
 * Help IDs for the application.
 * This object starts with library-specific help IDs and can be extended
 * at application startup with application-specific IDs.
 *
 * To extend with application-specific help IDs, use:
 * Object.assign(HelpIds, YourAppHelpIds);
 */
export const HelpIds: Record<string, string> = {
  HELP_DASHBOARD: 'dashboard',
  // Intro
  HELP_INTRO: 'intro',
  HELP_INTRO_REGISTER: 'intro/register',
  HELP_INTRO_NAVIGATION: 'intro/userinterface',
  /** The settings of the menu bar: password, nickname and locale, language-model keys, export and deletion. */
  HELP_INTRO_SETTINGS: 'intro/settings',
  /** Access of AI assistants: personal access tokens and applications authorized by OAuth. */
  HELP_INTRO_SETTINGS_AI_ASSISTANT: 'intro/settings/aiassistant',
  HELP_INTRO_PROPOSE_CHANGE_ENTITY: 'basedata',

  // Base data
  HELP_BASEDATA_UDF_METADATA_GENERAL: 'basedata/udfmetadata',

  // Admin data
  HELP_MESSAGE_SYSTEM: 'admindata',
  HELP_GLOBAL_SETTINGS: 'admindata/globalsettings',
  HELP_TASK_DATA_CHANGE_MONITOR: 'admindata/taskdatachangemonitor',
  /** Description of the background task types; also the page of every task type without a group page of its own. */
  HELP_TASK_DESCRIPTION: 'admindata/taskdatachangemonitor/taskdescription',
  HELP_TASK_DESCRIPTION_GTNET: 'admindata/taskdatachangemonitor/taskdescription/gtnet',
  HELP_CONNECTOR_API_KEY: 'admindata/connectorapikey',
  HELP_USER: 'admindata/user',
  HELP_ENTITY_LIMIT: 'admindata/entitylimit',

  // Manage client (library feature g.use.manageclient)
  HELP_MANAGE_CLIENT: 'tenantportfolio/client/managedclients'
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
