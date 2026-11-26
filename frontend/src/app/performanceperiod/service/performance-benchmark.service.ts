import { Service } from '@angular/core';
import { HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { AuthServiceWithLogout } from '../../lib/login/service/base.auth.service.with.logout';
import { BaseSettings } from '../../lib/base.settings';
import type { PerformanceBenchmarkView } from '../model/performance.period';

/**
 * Reads and writes the stored benchmark of the period performance comparison: the one of the client view, which is
 * the default of every portfolio, and the override of a single portfolio. The server resolves inheritance and checks
 * that the instrument is readable; a read-only tenant access cannot write.
 */
@Service()
export class PerformanceBenchmarkService extends AuthServiceWithLogout<PerformanceBenchmarkView> {
  private readonly url = BaseSettings.API_ENDPOINT + 'performancebenchmark';

  /**
   * The effective stored benchmark of a scope.
   *
   * @param idPortfolio - The portfolio, null for the client view
   * @returns the benchmark with its source, null when none is stored or inherited (HTTP 204)
   */
  getBenchmark(idPortfolio: number | null): Observable<PerformanceBenchmarkView | null> {
    return this.httpClient
      .get<PerformanceBenchmarkView | null>(this.url, {
        headers: this.prepareHeaders(),
        params: this.scopeParams(idPortfolio)
      })
      .pipe(catchError(this.handleError.bind(this)));
  }

  /**
   * Stores the benchmark of a scope.
   *
   * @param idPortfolio - The portfolio, null for the client view
   * @param idSecuritycurrency - The benchmark instrument
   * @returns the effective benchmark of the scope after saving
   */
  assignBenchmark(idPortfolio: number | null, idSecuritycurrency: number): Observable<PerformanceBenchmarkView> {
    return this.httpClient
      .put<PerformanceBenchmarkView>(this.url, { idPortfolio, idSecuritycurrency }, { headers: this.prepareHeaders() })
      .pipe(catchError(this.handleError.bind(this)));
  }

  /**
   * Removes the benchmark of exactly one scope; a portfolio then inherits the benchmark of the tenant again.
   *
   * @param idPortfolio - The portfolio, null for the client view
   */
  clearBenchmark(idPortfolio: number | null): Observable<void> {
    return this.httpClient
      .delete<void>(this.url, { headers: this.prepareHeaders(), params: this.scopeParams(idPortfolio) })
      .pipe(catchError(this.handleError.bind(this)));
  }

  private scopeParams(idPortfolio: number | null): HttpParams {
    return idPortfolio == null ? new HttpParams() : new HttpParams().set('idPortfolio', '' + idPortfolio);
  }
}
