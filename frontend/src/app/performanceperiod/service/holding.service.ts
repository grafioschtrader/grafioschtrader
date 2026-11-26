import { Service } from '@angular/core';
import type { PerformanceWindowDef } from '../model/performance-window';
export { PerformanceWindowDef, WeekYear } from '../model/performance-window';
import { AuthServiceWithLogout } from '../../lib/login/service/base.auth.service.with.logout';
import { Tenant } from '../../entities/tenant';
import { Observable } from 'rxjs';
import { AppSettings } from '../../shared/app.settings';
import { catchError, map } from 'rxjs/operators';
import { HttpParams, HttpResponse } from '@angular/common/http';
import { PerformancePeriod } from '../model/performance.period';
import type { SecurityPeriodPerformance } from '../model/security-period-performance';
import type { SecurityPositionSeries } from '../model/security-position-series';
import { MissingQuotesWithSecurities } from '../../tenant/model/missing.quotes.with.securities';
import { BaseSettings } from '../../lib/base.settings';
import { PerformanceReportOptions, PerformanceReportRequest } from '../model/performance-report';
import {
  parseTradingDays,
  type FirstAndMissingTradingDays,
  type FirstAndMissingTradingDaysResponse
} from '../model/trading-days';
export type { FirstAndMissingTradingDays } from '../model/trading-days';

@Service()
export class HoldingService extends AuthServiceWithLogout<Tenant> {
  getReportOptions(): Observable<PerformanceReportOptions> {
    return this.httpClient
      .get<PerformanceReportOptions>(
        `${BaseSettings.API_ENDPOINT}${AppSettings.HOLDING_KEY}/report/options`,
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  /** Blob error bodies still use the standard translated server error toast. */
  downloadPeriodPerformancePdf(request: PerformanceReportRequest): Observable<HttpResponse<Blob>> {
    return this.postForPdf(`${AppSettings.HOLDING_KEY}/report/pdf`, request, 'gt.report.invalid.download');
  }

  getFirstAndMissingTradingDays(idPortfolio: number | null | undefined): Observable<FirstAndMissingTradingDays> {
    return this.httpClient
      .get<FirstAndMissingTradingDaysResponse>(
        `${BaseSettings.API_ENDPOINT}${AppSettings.HOLDING_KEY}/getdatesforform`,
        this.getOptionsWithIdPortfolio(idPortfolio)
      )
      .pipe(map(parseTradingDays), catchError(this.handleError.bind(this)));
  }

  /**
   * Loads the period performance. A request-local benchmark is sent only when one was chosen; 0 is a choice too and
   * compares none, which is why the parameter is tested against null rather than for truthiness.
   */
  getPeriodPerformance(performanceWindowDef: PerformanceWindowDef): Observable<PerformancePeriod> {
    const options = this.getOptionsWithIdPortfolio(performanceWindowDef.idPortfolio);
    const params =
      performanceWindowDef.idBenchmark != null
        ? options.params.set('idBenchmark', '' + performanceWindowDef.idBenchmark)
        : options.params;
    return this.httpClient
      .get<PerformancePeriod>(
        `${BaseSettings.API_ENDPOINT}${AppSettings.HOLDING_KEY}/${performanceWindowDef.dateFrom}/${performanceWindowDef.dateTo}` +
          `/${performanceWindowDef.periodSplit}`,
        { headers: options.headers, params }
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  /**
   * Loads the absolute gain and the time-weighted return of every security held or traded in a period.
   *
   * @param idPortfolio - The portfolio, or null for the whole client
   * @param dateFrom - Excluded base date, yyyy-MM-dd
   * @param dateTo - Last date, yyyy-MM-dd
   * @returns the period performance per security
   */
  getSecurityPeriodPerformance(
    idPortfolio: number | null,
    dateFrom: string,
    dateTo: string,
    idBenchmark?: number
  ): Observable<SecurityPeriodPerformance> {
    // 0 is a choice too and compares none, so the parameter is tested against null rather than for truthiness
    const options = this.getOptionsWithIdPortfolio(idPortfolio);
    const params = idBenchmark != null ? options.params.set('idBenchmark', '' + idBenchmark) : options.params;
    return this.httpClient
      .get<SecurityPeriodPerformance>(
        `${BaseSettings.API_ENDPOINT}${AppSettings.HOLDING_KEY}/securityperformance/${dateFrom}/${dateTo}`,
        { headers: options.headers, params }
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  getMissingQuotesWithSecurities(year: number): Observable<MissingQuotesWithSecurities> {
    return this.httpClient
      .get<MissingQuotesWithSecurities>(
        `${BaseSettings.API_ENDPOINT}${AppSettings.HOLDING_KEY}/missingquotes/${year}`,
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  /**
   * Loads the series of the security position chart. Only one scope parameter is sent: a security account wins over
   * a portfolio, as for the chart PDF; neither means the whole client.
   *
   * @param idSecuritycurrency - The security
   * @param idPortfolio - Portfolio of the calling view, or null
   * @param idSecurityaccount - Security account of the calling view, or null
   * @param phaseFrom - First day of the requested holding phase, yyyy-MM-dd; null for the newest phase
   * @returns the phases and the series of one phase
   */
  getSecurityPositionSeries(
    idSecuritycurrency: number,
    idPortfolio: number | null | undefined,
    idSecurityaccount: number | null | undefined,
    phaseFrom: string | null
  ): Observable<SecurityPositionSeries> {
    let params = new HttpParams();
    if (idSecurityaccount) {
      params = params.set('idSecurityaccount', idSecurityaccount);
    } else if (idPortfolio) {
      params = params.set('idPortfolio', idPortfolio);
    }
    if (phaseFrom) {
      params = params.set('phaseFrom', phaseFrom);
    }
    return this.httpClient
      .get<SecurityPositionSeries>(
        `${BaseSettings.API_ENDPOINT}${AppSettings.HOLDING_KEY}/securityposition/${idSecuritycurrency}`,
        { headers: this.prepareHeaders(), params }
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  private getOptionsWithIdPortfolio(idPortfolio: number | null | undefined) {
    const httpParams = new HttpParams().set('idPortfolio', '' + (idPortfolio ? idPortfolio : ''));
    return { headers: this.prepareHeaders(), params: httpParams };
  }
}
