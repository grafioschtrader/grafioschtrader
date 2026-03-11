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
import {SimulationTenantCreateDTO, SimulationTenantInfo} from '../../algo/model/simulation.tenant';
import {TaxStatementExportRequest} from '../../taxdata/service/tax-data.service';


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
    return this.httpClient.patch(`${BaseSettings.API_ENDPOINT}${BaseSettings.TENANT_KEY}/currency/${currency}`, null,
      {headers: this.prepareHeaders()}).pipe(catchError(this.handleError.bind(this)));
  }

  public getMaxTenantLimitsByMsgKey(msgKeys: TenantLimitTypes[]): Observable<TenantLimit[]> {
    let httpParams = new HttpParams();
    httpParams = httpParams.append('msgKeys', msgKeys.join(','));
    return <Observable<TenantLimit[]>>this.httpClient.get(`${BaseSettings.API_ENDPOINT}`
      + `${BaseSettings.GLOBALPARAMETERS_P_KEY}/tenantlimits`,
      {headers: this.prepareHeaders(), params: httpParams}).pipe(catchError(this.handleError.bind(this)));
  }

  public getSimulationTenants(): Observable<SimulationTenantInfo[]> {
    return <Observable<SimulationTenantInfo[]>>this.httpClient.get(
      `${BaseSettings.API_ENDPOINT}${BaseSettings.TENANT_KEY}/simulations`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  public createSimulationTenant(dto: SimulationTenantCreateDTO): Observable<Tenant> {
    return <Observable<Tenant>>this.httpClient.post(
      `${BaseSettings.API_ENDPOINT}${BaseSettings.TENANT_KEY}/simulation`, dto,
      {headers: this.prepareHeaders()}).pipe(catchError(this.handleError.bind(this)));
  }

  public deleteSimulationTenant(idTenant: number): Observable<void> {
    return <Observable<void>>this.httpClient.delete(
      `${BaseSettings.API_ENDPOINT}${BaseSettings.TENANT_KEY}/simulation/${idTenant}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  public saveTaxExportSettings(settings: TaxStatementExportRequest): Observable<void> {
    return <Observable<void>>this.httpClient.patch(
      `${BaseSettings.API_ENDPOINT}${BaseSettings.TENANT_KEY}/taxexportsettings`, settings,
      {headers: this.prepareHeaders()}).pipe(catchError(this.handleError.bind(this)));
  }

  public switchTenant(idTargetTenant: number): Observable<{token: string}> {
    return <Observable<{token: string}>>this.httpClient.post(
      `${BaseSettings.API_ENDPOINT}${BaseSettings.TENANT_KEY}/switchto/${idTargetTenant}`, null,
      {headers: this.prepareHeaders()}).pipe(catchError(this.handleError.bind(this)));
  }

}
