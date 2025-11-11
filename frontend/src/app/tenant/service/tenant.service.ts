import {Injectable} from '@angular/core';

import {AppSettings} from '../../shared/app.settings';
import {Tenant} from '../../entities/tenant';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {Observable} from 'rxjs';
import {AuthServiceWithLogout} from '../../lib/login/service/base.auth.service.with.logout';
import {HttpClient, HttpParams} from '@angular/common/http';
import {catchError} from 'rxjs/operators';
import {LoginService} from '../../lib/login/service/log-in.service';
import {BaseSettings} from '../../lib/base.settings';
import {TenantLimit, TenantLimitTypes} from '../../shared/types/tenant.limit';


@Injectable()
export class TenantService extends AuthServiceWithLogout<Tenant> {

  constructor(loginService: LoginService, httpClient: HttpClient, messageToastService: MessageToastService) {
    super(loginService, httpClient, messageToastService);
  }

  getTenantAndPortfolio(): Observable<Tenant> {
    return <Observable<Tenant>>this.httpClient.get(`${BaseSettings.API_ENDPOINT}${BaseSettings.TENANT_KEY}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  update(tenant: Tenant): Observable<Tenant> {
    return this.updateEntity(tenant, tenant.idTenant, BaseSettings.TENANT_KEY);
  }

  public setWatchlistForPerformance(idWatchlist: number): Observable<Tenant> {
    return this.httpClient.patch(
      `${BaseSettings.API_ENDPOINT}${BaseSettings.TENANT_KEY}/watchlistforperformance/${idWatchlist}`, null,
      {headers: this.prepareHeaders()}).pipe(catchError(this.handleError.bind(this)));
  }

  public changeCurrencyTenantAndPortfolios(currency: string): Observable<Tenant> {
    return this.httpClient.patch(`${BaseSettings.API_ENDPOINT}${BaseSettings.TENANT_KEY}/${currency}`, null,
      {headers: this.prepareHeaders()}).pipe(catchError(this.handleError.bind(this)));
  }

  public getMaxTenantLimitsByMsgKey(msgKeys: TenantLimitTypes[]): Observable<TenantLimit[]> {
    let httpParams = new HttpParams();
    httpParams = httpParams.append('msgKeys', msgKeys.join(','));
    return <Observable<TenantLimit[]>>this.httpClient.get(`${BaseSettings.API_ENDPOINT}`
      + `${BaseSettings.GLOBALPARAMETERS_P_KEY}/tenantlimits`,
      {headers: this.prepareHeaders(), params: httpParams}).pipe(catchError(this.handleError.bind(this)));
  }

}
