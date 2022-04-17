import {BaseID} from './base.id';

export class TaskDataChange implements BaseID {
  idTaskDataChange: number;
  idTask: TaskType | string = null;
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

export enum TaskType {
  PRICE_AND_SPLIT_DIV_CALENDAR_UPDATE_THRU = 0,
  SECURITY_DIVIDEND_UPDATE_FOR_SECURITY = 1,
  SECURITY_SPLIT_UPDATE_FOR_SECURITY = 2,
  CURRENCY_CHANGED_ON_TENANT_OR_PORTFOLIO = 3,
  CURRENCY_CHANGED_ON_TENANT_AND_PORTFOLIO = 4,
  SECURITY_LOAD_HISTORICAL_INTRA_PRICE_DATA = 5,
  HOLDINGS_SECURITY_REBUILD = 6,
  REBUILD_HOLDING_CASHACCOUNT_DEPOSIT_OUT_DATED_CURRENCY_PAIR_PRICE = 7,
  CHECK_RELOAD_SECURITY_ADJUSTED_HISTORICAL_PRICES = 8,
  REBUILD_HOLDINGS_ALL_OR_SINGLE_TENANT = 9,
  LOAD_EMPTY_CURRENCYPAIR_HISTORYQOUTES = 10,
  COPY_DEMO_ACCOUNTS = 11,
  CREATE_STOCK_EXCHANGE_CALENDAR_BY_INDEX = 12,
  PERIODICALLY_DIVIDEND_UPDATE_CHECK = 13,

  MOVE_CREATED_BY_USER_TO_OTHER_USER = 31,
  UPD_V_0_11_0 = 51,
  UNOFFICIAL_CREATE_TRANSACTION_FROM_DIVIDENDS_TABLE = 100,
}

export class TaskDataChangeFormConstraints {
  taskTypeConfig: { [key: string]: string[] };
  canBeInterruptedList: string[];
  maxUserCreateTask: number;
  maxDaysInFuture: number;
}
