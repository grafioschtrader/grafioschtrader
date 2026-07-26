import {BaseID} from '../../entities/base.id';
import {TaskTypeBase} from './task.type.base';

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

export class TaskDataChangeFormConstraints {
  taskTypeConfig: { [key: string]: string[] };
  canBeInterruptedList: string[];
  /**
   * Task-scoped selectable ID options: task type name -> entity name -> option list
   * (e.g., CREATE_STOCK_EXCHANGE_CALENDAR_BY_RULE_SET -> Stockexchange -> exchange list).
   */
  entityIdOptions: { [taskName: string]: { [entity: string]: EntityIdOption[] } };
  maxUserCreateTask: number;
  maxDaysInFuture: number;
}
