import { HttpResourceRequest } from '@angular/common/http';
import { Service } from '@angular/core';
import { Observable } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { BaseSettings } from '../../lib/base.settings';
import { AuthServiceWithLogout } from '../../lib/login/service/base.auth.service.with.logout';
import {
  SimulationRunEquityPoint,
  SimulationRunEventWindow,
  SimulationRunResult,
  SimulationRunSettings
} from '../model/simulation.run';

/**
 * The historical replay of a simulation environment.
 *
 * A replay is started from the main tenant, never from inside a switched-in simulation: the backend books its fills
 * as the owner of the environment and refuses a request that arrives with the simulation's own identity.
 */
@Service()
export class AlgoSimulationRunService extends AuthServiceWithLogout<SimulationRunResult> {
  /** Builds the cancellable read used by signal-driven views and observable callers. */
  getSettingsRequest(idTenant: number) {
    return {
      url: `${this.endpoint}/${idTenant}/run/settings`,
      headers: this.prepareHeaders()
    } satisfies HttpResourceRequest;
  }

  private endpoint = `${BaseSettings.API_ENDPOINT}tenant/simulation`;

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
  status(idTenant: number): Observable<SimulationRunResult | null> {
    return this.httpClient
      .get<SimulationRunResult | null>(`${this.endpoint}/${idTenant}/run`, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }

  /** What the latest run was based on, or an empty body when the environment has never been replayed. */
  settings(idTenant: number): Observable<SimulationRunSettings | null> {
    const { url, ...options } = this.getSettingsRequest(idTenant);
    return this.httpClient
      .get<SimulationRunSettings | null>(url, options)
      .pipe(catchError(this.handleError.bind(this)));
  }

  /** The equity curve of the latest run; empty when the environment was never replayed or the run did not complete. */
  equitySeries(idTenant: number): Observable<SimulationRunEquityPoint[]> {
    return this.httpClient
      .get<SimulationRunEquityPoint[]>(`${this.endpoint}/${idTenant}/run/equity`, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }

  /**
   * A window of the audit trail around one day: the entries nearest to it on both sides, newest day first.
   *
   * @param idTenant - the simulation environment
   * @param anchorDate - the day in 'YYYY-MM-DD' the window is centred on; omitted, the end date of the run is used
   */
  eventWindow(idTenant: number, anchorDate?: string): Observable<SimulationRunEventWindow> {
    const query = anchorDate ? `?anchorDate=${anchorDate}` : '';
    return this.httpClient
      .get<SimulationRunEventWindow>(`${this.endpoint}/${idTenant}/run/events/window${query}`, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }

  cancel(idTenant: number): Observable<void> {
    return this.httpClient
      .post<void>(`${this.endpoint}/${idTenant}/run/cancel`, null, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }
}
