import {Injectable} from '@angular/core';

import {AppSettings} from '../../../shared/app.settings';
import {Tenant} from '../../../entities/tenant';
import {MessageToastService} from '../../message/message.toast.service';
import {lastValueFrom, Observable} from 'rxjs';
import {AuthServiceWithLogout} from '../../login/service/base.auth.service.with.logout';
import {HttpClient} from '@angular/common/http';
import {catchError} from 'rxjs/operators';
import {LoginService} from '../../login/service/log-in.service';
import {BaseSettings} from '../../base.settings';


@Injectable()
export class TenantService extends AuthServiceWithLogout<Tenant> {

  constructor(loginService: LoginService, httpClient: HttpClient, messageToastService: MessageToastService) {
    super(loginService, httpClient, messageToastService);
  }

  getTenantAndPortfolio(): Observable<Tenant> {
    return <Observable<Tenant>>this.httpClient.get(`${BaseSettings.API_ENDPOINT}${AppSettings.TENANT_KEY}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  update(tenant: Tenant): Observable<Tenant> {
    return this.updateEntity(tenant, tenant.idTenant, AppSettings.TENANT_KEY);
  }

  public setWatchlistForPerformance(idWatchlist: number): Observable<Tenant> {
    return this.httpClient.patch(
      `${BaseSettings.API_ENDPOINT}${AppSettings.TENANT_KEY}/watchlistforperformance/${idWatchlist}`, null,
      {headers: this.prepareHeaders()}).pipe(catchError(this.handleError.bind(this)));
  }

  public async getExportPersonalDataAsZip(): Promise<Blob> {
    const blob$ = this.httpClient.get<Blob>(
      `${BaseSettings.API_ENDPOINT}${AppSettings.TENANT_KEY}/exportpersonaldataaszip`,
      {headers: this.prepareHeaders('application/zip'), responseType: 'blob' as 'json'});
    return await lastValueFrom(blob$);
  }

  public deleteMyDataAndUserAccount() {
    return this.httpClient.delete(`${BaseSettings.API_ENDPOINT}${AppSettings.TENANT_KEY}`, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }

  public changeCurrencyTenantAndPortfolios(currency: string): Observable<Tenant> {
    return this.httpClient.patch(`${BaseSettings.API_ENDPOINT}${AppSettings.TENANT_KEY}/${currency}`, null,
      {headers: this.prepareHeaders()}).pipe(catchError(this.handleError.bind(this)));
  }

}
