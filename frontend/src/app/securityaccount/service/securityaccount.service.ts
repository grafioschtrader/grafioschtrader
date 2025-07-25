import {Injectable} from '@angular/core';
import {AppSettings} from '../../shared/app.settings';
import {SecurityPositionGrandSummary} from '../../entities/view/security.position.grand.summary';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {Securityaccount} from '../../entities/securityaccount';
import {Observable} from 'rxjs';
import {HttpClient} from '@angular/common/http';
import {AuthServiceWithLogout} from '../../lib/login/service/base.auth.service.with.logout';
import {ServiceEntityUpdate} from '../../lib/edit/service.entity.update';
import {catchError} from 'rxjs/operators';
import {LoginService} from '../../lib/login/service/log-in.service';
import {AppHelper} from '../../lib/helper/app.helper';
import {BaseSettings} from '../../lib/base.settings';


@Injectable()
export class SecurityaccountService extends AuthServiceWithLogout<Securityaccount> implements ServiceEntityUpdate<Securityaccount> {

  constructor(loginService: LoginService, httpClient: HttpClient, messageToastService: MessageToastService) {
    super(loginService, httpClient, messageToastService);
  }

  getSecurityPositionSummaryTenant(group: string, includeClosedPosition: boolean,
    untilDate: Date): Observable<SecurityPositionGrandSummary> {
    return <Observable<SecurityPositionGrandSummary>>this.httpClient.get(`${BaseSettings.API_ENDPOINT}`
      + `${AppSettings.SECURITYACCOUNT_KEY}/tenantsecurityaccountsummary/${group}`,
      AppHelper.getOptionsWithIncludeClosedPositionAndUntilDate(includeClosedPosition, untilDate, this.prepareHeaders()))
      .pipe(catchError(this.handleError.bind(this)));
  }

  getSecurityPositionSummaryPortfolio(idPortfolio: number, group: string,
    includeClosedPosition: boolean, untilDate: Date): Observable<SecurityPositionGrandSummary> {
    return <Observable<SecurityPositionGrandSummary>>this.httpClient.get(`${BaseSettings.API_ENDPOINT}${AppSettings.SECURITYACCOUNT_KEY}/`
      + `${idPortfolio}/portfoliosecurityaccountsummary/${group}`,
      AppHelper.getOptionsWithIncludeClosedPositionAndUntilDate(includeClosedPosition, untilDate, this.prepareHeaders()))
      .pipe(catchError(this.handleError.bind(this)));
  }

  getPositionSummarySecurityaccount(idSecurityaccount: number, group: string,
    includeClosedPosition: boolean, untilDate: Date): Observable<SecurityPositionGrandSummary> {
    return <Observable<SecurityPositionGrandSummary>>this.httpClient.get(`${BaseSettings.API_ENDPOINT}`
      + `${AppSettings.SECURITYACCOUNT_KEY}/${idSecurityaccount}/securityaccountsummary/${group}`,
      AppHelper.getOptionsWithIncludeClosedPositionAndUntilDate(includeClosedPosition, untilDate, this.prepareHeaders()))
      .pipe(catchError(this.handleError.bind(this)));
  }

  update(securityaccount: Securityaccount): Observable<Securityaccount> {
    return this.updateEntity(securityaccount, securityaccount.idSecuritycashAccount, AppSettings.SECURITYACCOUNT_KEY);
  }

  deleteSecurityaccount(idSecuritycashaccount: number) {
    return this.httpClient.delete(`${BaseSettings.API_ENDPOINT}${AppSettings.SECURITYACCOUNT_KEY}/${idSecuritycashaccount}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }


}

