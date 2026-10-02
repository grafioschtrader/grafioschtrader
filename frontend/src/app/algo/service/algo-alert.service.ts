import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { catchError } from 'rxjs/operators';
import { AuthServiceWithLogout } from '../../lib/login/service/base.auth.service.with.logout';
import { LoginService } from '../../lib/login/service/log-in.service';
import { MessageToastService } from '../../lib/message/message.toast.service';
import { BaseSettings } from '../../lib/base.settings';
export interface StrategyAssignment {
  idAlgoRuleStrategy: number;
  name: string;
}

export interface AlertNotification {
  id: number;
  contextName: string;
  securityName: string;
  alertTime: string;
  deliveryStatus: string;
  deliveryChannels: string;
  deliveryAttempts: number;
  nextAttemptAt: string;
  internalCompletedAt: string;
  externalCompletedAt: string;
  deliveryError: string;
  alarmDetails: string;
  /**
   * Whether the user may delete the notification now. Decided by the backend: the delivery has finished and the
   * notification is older than the window in which it still prevents the same alert from being sent again.
   */
  deletable: boolean;
}
export interface AlertEvaluation {
  strategyId: number;
  securityId: number;
  contextName: string;
  securityName: string;
  strategyType: string;
  active: boolean;
  outcome: string;
  reason: string;
  lastAttempt: string;
  lastSuccess: string;
  quoteTimestamp: string;
}
/** One alert strategy of a hierarchy and whether the live evaluation considers it. */
export interface HierarchyStrategyAlert {
  idAlgoRuleStrategy: number;
  algoStrategyImplementations: string;
  alertEnabled: boolean;
  effectiveActive: boolean;
}

/** A hierarchy node carrying alerts; the level decides which instruments its alerts apply to. */
export interface HierarchyNodeAlerts {
  idNode: number;
  nodeName: string;
  nodeLevel: 'TOP' | 'ASSETCLASS' | 'SECURITY';
  alerts: HierarchyStrategyAlert[];
}

/** The alerts of one AlgoTop hierarchy; only the assigned hierarchy is evaluated live. */
export interface AlgoTopAlertGroup {
  idAlgoTop: number;
  name: string;
  assigned: boolean;
  nodes: HierarchyNodeAlerts[];
}

/** Tenant-scoped live alert diagnostics and explicit retry actions. */
@Injectable({ providedIn: 'root' })
export class AlgoAlertService extends AuthServiceWithLogout<AlertNotification> {
  private endpoint = `${BaseSettings.API_ENDPOINT}algoalerts`;
  constructor(login: LoginService, http: HttpClient, toast: MessageToastService) {
    super(login, http, toast);
  }
  evaluations() {
    return this.httpClient
      .get<AlertEvaluation[]>(`${this.endpoint}/evaluations`, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }
  /** The alerts of every AlgoTop hierarchy of the tenant, each marked as live or dormant. */
  hierarchyAlerts() {
    return this.httpClient
      .get<AlgoTopAlertGroup[]>(`${this.endpoint}/hierarchies`, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }
  trading() {
    return this.httpClient
      .get<any[]>(`${this.endpoint}/trading`, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }
  assignmentOptions(security: number) {
    return this.httpClient
      .get<StrategyAssignment[]>(`${this.endpoint}/strategies/${security}`, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }
  /** Every recorded notification of the tenant, newest first; the table pages and filters them on the client. */
  notifications() {
    return this.httpClient
      .get<AlertNotification[]>(`${this.endpoint}/notifications`, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }
  evaluate() {
    return this.httpClient
      .post<void>(`${BaseSettings.API_ENDPOINT}algotop/evaluatealarms`, null, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }
  retry(id: number) {
    return this.httpClient
      .post<void>(`${this.endpoint}/notifications/${id}/retry`, null, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }
  /**
   * Deletes the selected notifications. The backend deletes all or none of them and refuses a selection that holds a
   * notification which is not deletable.
   *
   * @param ids - The ids of the selected notifications
   */
  deleteNotifications(ids: number[]) {
    return this.httpClient
      .post<void>(`${this.endpoint}/notifications/deletes`, ids, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }
}
