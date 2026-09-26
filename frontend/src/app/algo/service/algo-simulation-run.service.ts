import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { AuthServiceWithLogout } from '../../lib/login/service/base.auth.service.with.logout';
import { LoginService } from '../../lib/login/service/log-in.service';
import { MessageToastService } from '../../lib/message/message.toast.service';
import { BaseSettings } from '../../lib/base.settings';
import { SimulationRunEvent, SimulationRunResult, SimulationRunSettings } from '../model/simulation.run';

/**
 * The historical replay of a simulation environment.
 *
 * A replay is started from the main tenant, never from inside a switched-in simulation: the backend books its fills
 * as the owner of the environment and refuses a request that arrives with the simulation's own identity.
 */
@Injectable({ providedIn: 'root' })
export class AlgoSimulationRunService extends AuthServiceWithLogout<SimulationRunResult> {
  private endpoint = `${BaseSettings.API_ENDPOINT}tenant/simulation`;

  constructor(login: LoginService, http: HttpClient, toast: MessageToastService) {
    super(login, http, toast);
  }

  /** Starts a replay up to the given completed end date and returns the run in its running state. */
  start(
    idTenant: number,
    endDate: string,
    applyTaxModels = false,
    generateBondCoupons = false,
    custodyOpeningYaml?: string
  ): Observable<SimulationRunResult> {
    return this.httpClient
      .post<SimulationRunResult>(
        `${this.endpoint}/${idTenant}/run`,
        { endDate, applyTaxModels, generateBondCoupons, custodyOpeningYaml },
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  /** The run of an environment, or an empty body when it has never been replayed. */
  status(idTenant: number): Observable<SimulationRunResult> {
    return this.httpClient
      .get<SimulationRunResult>(`${this.endpoint}/${idTenant}/run`, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }

  /** What the latest run was based on, or an empty body when the environment has never been replayed. */
  settings(idTenant: number): Observable<SimulationRunSettings> {
    return this.httpClient
      .get<SimulationRunSettings>(`${this.endpoint}/${idTenant}/run/settings`, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }

  events(
    idTenant: number,
    page: number,
    size: number
  ): Observable<{
    content: SimulationRunEvent[];
    totalElements: number;
  }> {
    return this.httpClient
      .get<{ content: SimulationRunEvent[]; totalElements: number }>(
        `${this.endpoint}/${idTenant}/run/events?page=${page}&size=${size}`,
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  cancel(idTenant: number): Observable<void> {
    return this.httpClient
      .post<void>(`${this.endpoint}/${idTenant}/run/cancel`, null, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }
}
