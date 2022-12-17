import {AuthServiceWithLogout} from '../../shared/login/service/base.auth.service.with.logout';
import {
  CorrelationLimit,
  CorrelationResult,
  CorrelationRollingResult,
  CorrelationSet
} from '../../entities/correlation.set';
import {ServiceEntityUpdate} from '../../shared/edit/service.entity.update';
import {LoginService} from '../../shared/login/service/log-in.service';
import {HttpClient, HttpParams} from '@angular/common/http';
import {MessageToastService} from '../../shared/message/message.toast.service';
import {Injectable} from '@angular/core';
import {AppSettings} from '../../shared/app.settings';
import {DeleteService} from '../../shared/datashowbase/delete.service';
import {Observable} from 'rxjs';
import {catchError} from 'rxjs/operators';
import {TenantLimit} from '../../entities/backend/tenant.limit';
import {SecuritycurrencySearch} from '../../entities/search/securitycurrency.search';
import {SecuritycurrencyLists} from '../../entities/view/securitycurrency.lists';
import {AppHelper} from '../../shared/helper/app.helper';
import {AddSearchToListService} from '../component/add-instrument-table.component';

@Injectable()
export class CorrelationSetService extends AuthServiceWithLogout<CorrelationSet> implements DeleteService,
  ServiceEntityUpdate<CorrelationSet>, AddSearchToListService<CorrelationSet> {

  constructor(loginService: LoginService, httpClient: HttpClient, messageToastService: MessageToastService) {
    super(loginService, httpClient, messageToastService);
  }

  getCorrelationSetByTenant(): Observable<CorrelationSet[]> {
    return <Observable<CorrelationSet[]>>this.httpClient.get(`${AppSettings.API_ENDPOINT}`
      + `${AppSettings.CORRELATION_SET_KEY}/tenant`, this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  getCalculationByCorrelationSet(idCorrelationSet: number): Observable<CorrelationResult> {
    return <Observable<CorrelationResult>>this.httpClient.get(`${AppSettings.API_ENDPOINT}`
      + `${AppSettings.CORRELATION_SET_KEY}/calculation/${idCorrelationSet}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  update(correlationSet: CorrelationSet) {
    return super.updateEntity(correlationSet, correlationSet.idCorrelationSet, AppSettings.CORRELATION_SET_KEY);
  }

  public deleteEntity(idCorrelationSet: number): Observable<any> {
    return this.httpClient.delete(`${AppSettings.API_ENDPOINT}${AppSettings.CORRELATION_SET_KEY}/`
      + `${idCorrelationSet}`, this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  addSecuritycurrenciesToList(idCorrelationSet: number, securitycurrencyLists: SecuritycurrencyLists): Observable<CorrelationSet> {
    return <Observable<CorrelationSet>>this.httpClient.put(`${AppSettings.API_ENDPOINT}${AppSettings.CORRELATION_SET_KEY}/`
      + `${idCorrelationSet}/addSecuritycurrency`, securitycurrencyLists,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  removeInstrumentFromCorrelationSet(idCorrelationSet: number, idSecuritycurrency: number): Observable<CorrelationSet> {
    return this.httpClient.delete(`${AppSettings.API_ENDPOINT}${AppSettings.CORRELATION_SET_KEY}/`
      + `${idCorrelationSet}/removeinstrument/${idSecuritycurrency}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  searchByCriteria(idCorrelationSet: number, securitycurrencySearch: SecuritycurrencySearch): Observable<SecuritycurrencyLists> {
    return <Observable<SecuritycurrencyLists>>this.httpClient.get(`${AppSettings.API_ENDPOINT}${AppSettings.CORRELATION_SET_KEY}`
      + `/${idCorrelationSet}/search`, {
      headers: this.prepareHeaders(),
      params: AppHelper.getHttpParamsOfObject(securitycurrencySearch)
    }).pipe(catchError(this.handleError.bind(this)));
  }

  getCorrelationSetLimit(): Observable<CorrelationLimit> {
    return <Observable<CorrelationLimit>>this.httpClient.get(
      `${AppSettings.API_ENDPOINT}${AppSettings.CORRELATION_SET_KEY}/limit`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  getRollingCorrelations(idCorrelationSet: number, securityIdsPairs: number[][]): Observable<CorrelationRollingResult[]> {
    let params = new HttpParams();
    params = params.append('securityIdsPairs', [].concat(...securityIdsPairs).join(','));
    return <Observable<CorrelationRollingResult[]>>this.httpClient.get(`${AppSettings.API_ENDPOINT}${AppSettings.CORRELATION_SET_KEY}`
      + `/corrrolling/${idCorrelationSet}`, {
      headers: this.prepareHeaders(),
      params
    }).pipe(catchError(this.handleError.bind(this)));
  }

  getCorrelationSetInstrumentLimit(idCorrelationSet: number): Observable<TenantLimit> {
    return <Observable<TenantLimit>>this.httpClient.get(
      `${AppSettings.API_ENDPOINT}${AppSettings.CORRELATION_SET_KEY}/limit/${idCorrelationSet}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }
}
