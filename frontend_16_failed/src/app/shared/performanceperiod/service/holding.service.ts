import {Injectable} from '@angular/core';
import {AuthServiceWithLogout} from '../../login/service/base.auth.service.with.logout';
import {Tenant} from '../../../entities/tenant';
import {Observable} from 'rxjs';
import {AppSettings} from '../../app.settings';
import {catchError, map} from 'rxjs/operators';
import {plainToClass, Type} from 'class-transformer';
import {LoginService} from '../../login/service/log-in.service';
import {HttpClient, HttpParams} from '@angular/common/http';
import {MessageToastService} from '../../message/message.toast.service';
import {PerformancePeriod} from '../model/performance.period';
import {MissingQuotesWithSecurities} from '../../../tenant/model/missing.quotes.with.securities';

@Injectable()
export class HoldingService extends AuthServiceWithLogout<Tenant> {

  constructor(loginService: LoginService, httpClient: HttpClient, messageToastService: MessageToastService) {
    super(loginService, httpClient, messageToastService);
  }

  getFirstAndMissingTradingDays(idPortfolio: number): Observable<FirstAndMissingTradingDays> {
    return <Observable<FirstAndMissingTradingDays>>this.httpClient.get(
      `${AppSettings.API_ENDPOINT}${AppSettings.HOLDING_KEY}/getdatesforform`,
      this.getOptionsWithIdPortfolio(idPortfolio)).pipe(map(response => plainToClass(FirstAndMissingTradingDays, response)),
      catchError(this.handleError.bind(this)));
  }

  getPeriodPerformance(performanceWindowDef: PerformanceWindowDef): Observable<PerformancePeriod> {
    return <Observable<PerformancePeriod>>this.httpClient.get(
      `${AppSettings.API_ENDPOINT}${AppSettings.HOLDING_KEY}/${performanceWindowDef.dateFrom}/${performanceWindowDef.dateTo}`
      + `/${performanceWindowDef.periodSplit}`,
      this.getOptionsWithIdPortfolio(performanceWindowDef.idPortfolio)).pipe(catchError(this.handleError.bind(this)));
  }

  getMissingQuotesWithSecurities(year: number): Observable<MissingQuotesWithSecurities> {
    return <Observable<MissingQuotesWithSecurities>>this.httpClient.get(
      `${AppSettings.API_ENDPOINT}${AppSettings.HOLDING_KEY}/missingquotes/${year}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  private getOptionsWithIdPortfolio(idPortfolio: number) {
    const httpParams = new HttpParams().set('idPortfolio', '' + (idPortfolio ? idPortfolio : ''));
    return {headers: this.prepareHeaders(), params: httpParams};
  }
}

export class PerformanceWindowDef {
  dateFrom: Date;
  dateTo: Date;
  periodSplit: WeekYear;

  constructor(public idPortfolio: number) {
  }
}

export class FirstAndMissingTradingDays {
  maxWeekLimit: number;
  minIncludeMonthLimit: number;
  firstEverTradingDay: Date;
  secondEverTradingDay: Date;
  lastTradingDayOfLastYear: Date;
  secondLatestTradingDay: Date;
  latestTradingDay: Date;
  @Type(() => Date)
  holidayAndMissingQuoteDays: Date[];
}

export enum WeekYear {
  WM_WEEK = 0,
  WM_YEAR = 1
}
