import { describe, expect, it } from 'vitest';
import { AppTaskTypeHelpIds } from './help.ids';
import { TaskType } from '../types/task.type';
import { getTaskTypeHelpId, registerTaskTypeHelpIds, TaskTypeHelpIds } from '../../lib/taskdatamonitor/task.type.help';

/** Task types from 80 upwards are system tasks that cannot be created in the monitor (GitHub issue #205). */
const FIRST_SYSTEM_TASK = 80;

/**
 * The help button of the dialog creating a background task opens the description of the selected task type. A task
 * type added to an enum without a decision on its manual page would silently land on the overview page; this guard
 * makes that decision mandatory.
 */
describe('task type help IDs', () => {
  registerTaskTypeHelpIds(AppTaskTypeHelpIds);

  it('names a manual page for every task type that can be created in the monitor', () => {
    const creatable = Object.values(TaskType).filter(
      (value): value is number => typeof value === 'number' && value < FIRST_SYSTEM_TASK
    );
    const withoutPage = creatable.filter((idTask) => TaskTypeHelpIds[idTask] == null);
    expect(withoutPage).toEqual([]);
  });

  it('opens the group page at the anchor of the task type', () => {
    expect(getTaskTypeHelpId(TaskType.SECURITY_SPLIT_UPDATE_FOR_SECURITY)).toBe(
      'admindata/taskdatachangemonitor/taskdescription/splitdividend/#JOB32'
    );
    expect(getTaskTypeHelpId(null)).toBe('admindata/taskdatachangemonitor/taskdescription');
  });
});
