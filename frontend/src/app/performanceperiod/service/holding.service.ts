import { Service } from '@angular/core';
import type { PerformanceWindowDef } from '../model/performance-window';
export { PerformanceWindowDef, WeekYear } from '../model/performance-window';
import { AuthServiceWithLogout } from '../../lib/login/service/base.auth.service.with.logout';
import { Tenant } from '../../entities/tenant';
import { Observable, from, switchMap } from 'rxjs';
import { AppSettings } from '../../shared/app.settings';
import { catchError, map } from 'rxjs/operators';
import { HttpParams, HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { PerformancePeriod } from '../model/performance.period';
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
    return this.httpClient
      .post(`${BaseSettings.API_ENDPOINT}${AppSettings.HOLDING_KEY}/report/pdf`, request, {
        headers: this.prepareHeaders(),
        observe: 'response',
        responseType: 'blob'
      })
      .pipe(
        map((response) => {
          if (
            response.status !== 200 ||
            response.headers.get('Content-Type')?.split(';')[0].trim().toLowerCase() !== 'application/pdf'
          ) {
            throw new HttpErrorResponse({ status: response.status, error: 'gt.report.invalid.download' });
          }
          return response;
        }),
        catchError((error) => {
          if (error instanceof HttpErrorResponse && error.error instanceof Blob) {
            return from(error.error.text()).pipe(
              switchMap((text) => {
                let body: unknown;
                try {
                  body = JSON.parse(text);
                } catch {
                  body = { error: { message: text } };
                }
                return this.handleError(
                  new HttpErrorResponse({
                    error: body,
                    status: error.status,
                    statusText: error.statusText,
                    headers: error.headers
                  })
                );
              })
            );
          }
          return this.handleError(error);
        })
      ) as Observable<HttpResponse<Blob>>;
  }

  getFirstAndMissingTradingDays(idPortfolio: number | null | undefined): Observable<FirstAndMissingTradingDays> {
    return this.httpClient
      .get<FirstAndMissingTradingDaysResponse>(
        `${BaseSettings.API_ENDPOINT}${AppSettings.HOLDING_KEY}/getdatesforform`,
        this.getOptionsWithIdPortfolio(idPortfolio)
      )
      .pipe(map(parseTradingDays), catchError(this.handleError.bind(this)));
  }

  getPeriodPerformance(performanceWindowDef: PerformanceWindowDef): Observable<PerformancePeriod> {
    return this.httpClient
      .get<PerformancePeriod>(
        `${BaseSettings.API_ENDPOINT}${AppSettings.HOLDING_KEY}/${performanceWindowDef.dateFrom}/${performanceWindowDef.dateTo}` +
          `/${performanceWindowDef.periodSplit}`,
        this.getOptionsWithIdPortfolio(performanceWindowDef.idPortfolio)
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

  private getOptionsWithIdPortfolio(idPortfolio: number | null | undefined) {
    const httpParams = new HttpParams().set('idPortfolio', '' + (idPortfolio ? idPortfolio : ''));
    return { headers: this.prepareHeaders(), params: httpParams };
  }
}
