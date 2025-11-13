import {TaskTypeBase} from '../../lib/taskdatamonitor/types/task.type.base';
import {TaskTypeExtended} from './task.type.extended';

/**
 * Combined task type enum for Grafioschtrader application.
 * Merges base framework task types with application-specific extended types.
 * This combined enum is provided to lib components via dependency injection.
 */
export const TaskType = {
  ...TaskTypeBase,
  ...TaskTypeExtended
};

// Type alias for type safety
export type TaskTypeValue = TaskTypeBase | TaskTypeExtended;
