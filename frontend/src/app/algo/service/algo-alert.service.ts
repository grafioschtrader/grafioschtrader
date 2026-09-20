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
  notifications(page: number, status: string) {
    return this.httpClient
      .get<{ content: AlertNotification[]; totalElements: number }>(
        `${this.endpoint}/notifications?page=${page}&size=25&status=${encodeURIComponent(status || '')}`,
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }
  statuses() {
    return this.httpClient
      .get<string[]>(`${this.endpoint}/statuses`, this.getHeaders())
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
}
