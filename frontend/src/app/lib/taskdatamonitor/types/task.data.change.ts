import { BaseID } from '../../entities/base.id';
import { TaskTypeBase } from './task.type.base';

export class TaskDataChange implements BaseID {
  idTaskDataChange: number;
  idTask: TaskTypeBase | number | string = null;
  executionPriority: number;
  entity: string = null;
  idEntity: number = null;
  creationTime: number;
  earliestStartTime: Date | string = null;
  oldValueString: string;
  oldValueNumber: number;
  progressStateType: number;
  execStartTime: string;
  execEndTime: string;
  failedMessageCode: string;
  failedStackTrace: string;
  taskAsId: number;

  getId(): number {
    return this.idTaskDataChange;
  }
}

export enum ProgressStateType {
  PROG_WAITING = 0,
  PROG_PROCESSED = 1,
  PROG_FAILED = 2,
  PROG_TASK_NOT_FOUND = 3,
  PROG_RUNNING = 4,
  PROG_INTERRUPTED = 5
}

export enum TaskDataExecPriority {
  PRIO_VERY_LOW = 40,
  PRIO_LOW = 30,
  PRIO_NORMAL = 20,
  PRIO_HIGH = 10,
  PRIO_VERY_HIGH = 5
}

/** Backend response format for entity ID options */
export interface EntityIdOption {
  key: string;
  value: string;
}

/** A queue of background tasks with its own workers; the task monitor shows one tab per queue. */
export interface TaskQueueInfo {
  /** Name of the queue; MAIN is the main queue */
  name: string;
  /** How many tasks of this queue run at the same time */
  parallel: number;
  /** Text key of the tab */
  labelKey: string;
}

/** Name of the main queue, which holds every task type no other queue claims. */
export const MAIN_TASK_QUEUE = 'MAIN';

export class TaskDataChangeFormConstraints {
  taskTypeConfig: { [key: string]: string[] };
  canBeInterruptedList: string[];
  /** The queues, the main queue first */
  queues: TaskQueueInfo[];
  /** The queue of every task type, by task type name */
  taskTypeQueue: { [taskName: string]: string };
  /**
   * Task-scoped selectable ID options: task type name -> entity name -> option list
   * (e.g., CREATE_STOCK_EXCHANGE_CALENDAR_BY_RULE_SET -> Stockexchange -> exchange list).
   */
  entityIdOptions: { [taskName: string]: { [entity: string]: EntityIdOption[] } };
  maxUserCreateTask: number;
  maxDaysInFuture: number;
}
