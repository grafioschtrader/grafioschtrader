import { HelpIds } from '../help/help.ids';
import { TaskTypeBase } from './types/task.type.base';

/**
 * Help page of every task type that can be created in the batch processing monitor, keyed by the numeric task
 * type. The user manual describes the task types on a main page and groups related ones on pages of their own;
 * every description carries the anchor `JOB<id>`. The library registers its own task types here, the application
 * adds its types with {@link registerTaskTypeHelpIds} at startup.
 */
export const TaskTypeHelpIds: Record<number, string> = {
  [TaskTypeBase.TOKEN_USER_REGISTRATION_PURGE]: HelpIds.HELP_TASK_DESCRIPTION,
  [TaskTypeBase.PURGE_EXPIRED_ACCESS_TOKEN]: HelpIds.HELP_TASK_DESCRIPTION,
  [TaskTypeBase.GTNET_SERVER_STATUS_CHECK]: HelpIds.HELP_TASK_DESCRIPTION_GTNET,
  [TaskTypeBase.GTNET_EXCHANGE_LOG_AGGREGATION]: HelpIds.HELP_TASK_DESCRIPTION_GTNET,
  [TaskTypeBase.GTNET_EXCHANGE_SYNC]: HelpIds.HELP_TASK_DESCRIPTION_GTNET,
  [TaskTypeBase.GTNET_SETTINGS_BROADCAST]: HelpIds.HELP_TASK_DESCRIPTION_GTNET,
  [TaskTypeBase.GTNET_FUTURE_MESSAGE_DELIVERY]: HelpIds.HELP_TASK_DESCRIPTION_GTNET,
  [TaskTypeBase.GTNET_ADMIN_MESSAGE_DELIVERY]: HelpIds.HELP_TASK_DESCRIPTION_GTNET,
  [TaskTypeBase.MAIL_ROLE_MESSAGE_PURGE]: HelpIds.HELP_TASK_DESCRIPTION
};

/**
 * Registers the help pages of application-specific task types.
 *
 * @param helpIdByTaskType help ID of the describing page, keyed by the numeric task type
 */
export function registerTaskTypeHelpIds(helpIdByTaskType: Record<number, string>): void {
  Object.assign(TaskTypeHelpIds, helpIdByTaskType);
}

/**
 * Returns the help ID that opens the description of a task type at its own section. A task type without a
 * registered page is looked up on the main description page.
 *
 * @param idTask the numeric task type, or null when none is selected
 * @returns the help ID of the description page, with the anchor of the task type when one is given
 */
export function getTaskTypeHelpId(idTask: number | null | undefined): string {
  if (idTask == null) {
    return HelpIds.HELP_TASK_DESCRIPTION;
  }
  return `${TaskTypeHelpIds[idTask] ?? HelpIds.HELP_TASK_DESCRIPTION}/#JOB${idTask}`;
}
