import { Injectable } from '@angular/core';
import { AuthServiceWithLogout } from '../../lib/login/service/base.auth.service.with.logout';
import { Tenant } from '../../entities/tenant';
import { Observable, from, switchMap } from 'rxjs';
import { AppSettings } from '../../shared/app.settings';
import { catchError, map } from 'rxjs/operators';
import { plainToClass } from 'class-transformer';
import moment from 'moment';
import { LoginService } from '../../lib/login/service/log-in.service';
import { HttpClient, HttpParams, HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { MessageToastService } from '../../lib/message/message.toast.service';
import { PerformancePeriod } from '../model/performance.period';
import { MissingQuotesWithSecurities } from '../../tenant/model/missing.quotes.with.securities';
import { BaseSettings } from '../../lib/base.settings';
import { PerformanceReportOptions, PerformanceReportRequest } from '../model/performance-report';

@Injectable()
export class HoldingService extends AuthServiceWithLogout<Tenant> {
  constructor(loginService: LoginService, httpClient: HttpClient, messageToastService: MessageToastService) {
    super(loginService, httpClient, messageToastService);
  }

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

  getFirstAndMissingTradingDays(idPortfolio: number): Observable<FirstAndMissingTradingDays> {
    return <Observable<FirstAndMissingTradingDays>>(
      this.httpClient
        .get(
          `${BaseSettings.API_ENDPOINT}${AppSettings.HOLDING_KEY}/getdatesforform`,
          this.getOptionsWithIdPortfolio(idPortfolio)
        )
        .pipe(
          map((response) => this.toFirstAndMissingTradingDays(response)),
          catchError(this.handleError.bind(this))
        )
    );
  }

  /**
   * The server delivers the trading days as ISO date strings ("2006-02-20"). new Date() would read them as UTC
   * midnight, which is 01:00 or 02:00 in Central Europe. The date picker compares its input, taken as local
   * midnight, against such a bound and clears the first allowed day as being below minDate. Every day is therefore
   * parsed as local midnight.
   */
  private toFirstAndMissingTradingDays(response: any): FirstAndMissingTradingDays {
    const famtd = plainToClass(FirstAndMissingTradingDays, response);
    const toLocalDate = (day: any): Date => (day ? moment(day, BaseSettings.FORMAT_DATE_SHORT_NATIVE).toDate() : day);
    famtd.firstEverTradingDay = toLocalDate(response.firstEverTradingDay);
    famtd.secondEverTradingDay = toLocalDate(response.secondEverTradingDay);
    famtd.lastTradingDayOfLastYear = toLocalDate(response.lastTradingDayOfLastYear);
    famtd.secondLatestTradingDay = toLocalDate(response.secondLatestTradingDay);
    famtd.latestTradingDay = toLocalDate(response.latestTradingDay);
    famtd.holidayAndMissingQuoteDays = (response.holidayAndMissingQuoteDays ?? []).map(toLocalDate);
    return famtd;
  }

  getPeriodPerformance(performanceWindowDef: PerformanceWindowDef): Observable<PerformancePeriod> {
    return <Observable<PerformancePeriod>>(
      this.httpClient
        .get(
          `${BaseSettings.API_ENDPOINT}${AppSettings.HOLDING_KEY}/${performanceWindowDef.dateFrom}/${performanceWindowDef.dateTo}` +
            `/${performanceWindowDef.periodSplit}`,
          this.getOptionsWithIdPortfolio(performanceWindowDef.idPortfolio)
        )
        .pipe(catchError(this.handleError.bind(this)))
    );
  }

  getMissingQuotesWithSecurities(year: number): Observable<MissingQuotesWithSecurities> {
    return <Observable<MissingQuotesWithSecurities>>(
      this.httpClient
        .get(`${BaseSettings.API_ENDPOINT}${AppSettings.HOLDING_KEY}/missingquotes/${year}`, this.getHeaders())
        .pipe(catchError(this.handleError.bind(this)))
    );
  }

  private getOptionsWithIdPortfolio(idPortfolio: number) {
    const httpParams = new HttpParams().set('idPortfolio', '' + (idPortfolio ? idPortfolio : ''));
    return { headers: this.prepareHeaders(), params: httpParams };
  }
}

export class PerformanceWindowDef {
  dateFrom: Date;
  dateTo: Date;
  periodSplit: WeekYear;

  constructor(public idPortfolio: number) {}
}

export class FirstAndMissingTradingDays {
  maxWeekLimit: number;
  minIncludeMonthLimit: number;
  firstEverTradingDay: Date;
  secondEverTradingDay: Date;
  lastTradingDayOfLastYear: Date;
  secondLatestTradingDay: Date;
  latestTradingDay: Date;
  holidayAndMissingQuoteDays: Date[];
}

export enum WeekYear {
  WM_WEEK = 0,
  WM_YEAR = 1
}
