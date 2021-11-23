import {Injectable} from '@angular/core';
import {AppSettings} from '../../shared/app.settings';
import {SecurityPositionGrandSummary} from '../../entities/view/security.position.grand.summary';
import {MessageToastService} from '../../shared/message/message.toast.service';
import {Securityaccount} from '../../entities/securityaccount';
import {Observable} from 'rxjs';
import {HttpClient} from '@angular/common/http';
import {AuthServiceWithLogout} from '../../shared/login/service/base.auth.service.with.logout';
import {ServiceEntityUpdate} from '../../shared/edit/service.entity.update';
import {catchError} from 'rxjs/operators';
import {LoginService} from '../../shared/login/service/log-in.service';
import {AppHelper} from '../../shared/helper/app.helper';
import {InstrumentStatisticsResult} from '../../entities/view/instrument.statistics.result';


@Injectable()
export class SecurityaccountService extends AuthServiceWithLogout<Securityaccount> implements ServiceEntityUpdate<Securityaccount> {

  constructor(loginService: LoginService, httpClient: HttpClient, messageToastService: MessageToastService) {
    super(loginService, httpClient, messageToastService);
  }

  getSecurityPositionSummaryTenant(group: string, includeClosedPosition: boolean,
                                   untilDate: Date): Observable<SecurityPositionGrandSummary> {
    return <Observable<SecurityPositionGrandSummary>>this.httpClient.get(`${AppSettings.API_ENDPOINT}`
      + `${AppSettings.SECURITYACCOUNT_KEY}/tenantsecurityaccountsummary/${group}`,
      AppHelper.getOptionsWithIncludeClosedPositionAndUntilDate(includeClosedPosition, untilDate, this.prepareHeaders()))
      .pipe(catchError(this.handleError.bind(this)));
  }

  getSecurityPositionSummaryPortfolio(idPortfolio: number, group: string,
                                      includeClosedPosition: boolean, untilDate: Date): Observable<SecurityPositionGrandSummary> {
    return <Observable<SecurityPositionGrandSummary>>this.httpClient.get(`${AppSettings.API_ENDPOINT}${AppSettings.SECURITYACCOUNT_KEY}/`
      + `${idPortfolio}/portfoliosecurityaccountsummary/${group}`,
      AppHelper.getOptionsWithIncludeClosedPositionAndUntilDate(includeClosedPosition, untilDate, this.prepareHeaders()))
      .pipe(catchError(this.handleError.bind(this)));
  }

  getPositionSummarySecurityaccount(idSecurityaccount: number, group: string,
                                    includeClosedPosition: boolean, untilDate: Date): Observable<SecurityPositionGrandSummary> {
    return <Observable<SecurityPositionGrandSummary>>this.httpClient.get(`${AppSettings.API_ENDPOINT}`
      + `${AppSettings.SECURITYACCOUNT_KEY}/${idSecurityaccount}/securityaccountsummary/${group}`,
      AppHelper.getOptionsWithIncludeClosedPositionAndUntilDate(includeClosedPosition, untilDate, this.prepareHeaders()))
      .pipe(catchError(this.handleError.bind(this)));
  }

  update(securityaccount: Securityaccount): Observable<Securityaccount> {
    return this.updateEntity(securityaccount, securityaccount.idSecuritycashAccount, AppSettings.SECURITYACCOUNT_KEY);
  }

  deleteSecurityaccount(idSecuritycashaccount: number) {
    return this.httpClient.delete(`${AppSettings.API_ENDPOINT}${AppSettings.SECURITYACCOUNT_KEY}/${idSecuritycashaccount}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }


}

