import {AuthServiceWithLogout} from '../../lib/login/service/base.auth.service.with.logout';
import {
  CorrelationLimit,
  CorrelationResult,
  CorrelationRollingResult,
  CorrelationSet
} from '../../entities/correlation.set';
import {ServiceEntityUpdate} from '../../lib/edit/service.entity.update';
import {LoginService} from '../../lib/login/service/log-in.service';
import {HttpClient, HttpParams} from '@angular/common/http';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {Injectable} from '@angular/core';
import {AppSettings} from '../../shared/app.settings';
import {DeleteService} from '../../lib/datashowbase/delete.service';
import {Observable} from 'rxjs';
import {catchError} from 'rxjs/operators';
import {TenantLimit} from '../../shared/types/tenant.limit';
import {SecuritycurrencySearch} from '../../entities/search/securitycurrency.search';
import {SecuritycurrencyLists} from '../../entities/view/securitycurrency.lists';
import {AppHelper} from '../../lib/helper/app.helper';
import {AddSearchToListService} from '../component/add-instrument-table.component';
import {BaseSettings} from '../../lib/base.settings';

@Injectable()
export class CorrelationSetService extends AuthServiceWithLogout<CorrelationSet> implements DeleteService,
  ServiceEntityUpdate<CorrelationSet>, AddSearchToListService<CorrelationSet> {

  constructor(loginService: LoginService, httpClient: HttpClient, messageToastService: MessageToastService) {
    super(loginService, httpClient, messageToastService);
  }

  getCorrelationSetByTenant(): Observable<CorrelationSet[]> {
    return <Observable<CorrelationSet[]>>this.httpClient.get(`${BaseSettings.API_ENDPOINT}`
      + `${AppSettings.CORRELATION_SET_KEY}/tenant`, this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  getCalculationByCorrelationSet(idCorrelationSet: number): Observable<CorrelationResult> {
    return <Observable<CorrelationResult>>this.httpClient.get(`${BaseSettings.API_ENDPOINT}`
      + `${AppSettings.CORRELATION_SET_KEY}/calculation/${idCorrelationSet}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  update(correlationSet: CorrelationSet) {
    return super.updateEntity(correlationSet, correlationSet.idCorrelationSet, AppSettings.CORRELATION_SET_KEY);
  }

  public deleteEntity(idCorrelationSet: number): Observable<any> {
    return this.httpClient.delete(`${BaseSettings.API_ENDPOINT}${AppSettings.CORRELATION_SET_KEY}/`
      + `${idCorrelationSet}`, this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  addSecuritycurrenciesToList(idCorrelationSet: number, securitycurrencyLists: SecuritycurrencyLists): Observable<CorrelationSet> {
    return <Observable<CorrelationSet>>this.httpClient.put(`${BaseSettings.API_ENDPOINT}${AppSettings.CORRELATION_SET_KEY}/`
      + `${idCorrelationSet}/addSecuritycurrency`, securitycurrencyLists,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  removeInstrumentFromCorrelationSet(idCorrelationSet: number, idSecuritycurrency: number): Observable<CorrelationSet> {
    return this.httpClient.delete(`${BaseSettings.API_ENDPOINT}${AppSettings.CORRELATION_SET_KEY}/`
      + `${idCorrelationSet}/removeinstrument/${idSecuritycurrency}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  searchByCriteria(idCorrelationSet: number, securitycurrencySearch: SecuritycurrencySearch): Observable<SecuritycurrencyLists> {
    return <Observable<SecuritycurrencyLists>>this.httpClient.get(`${BaseSettings.API_ENDPOINT}${AppSettings.CORRELATION_SET_KEY}`
      + `/${idCorrelationSet}/search`, {
      headers: this.prepareHeaders(),
      params: AppHelper.getHttpParamsOfObjectAllowBooleanNullFields(securitycurrencySearch, ['onlyTenantPrivate'])
    }).pipe(catchError(this.handleError.bind(this)));
  }

  getCorrelationSetLimit(): Observable<CorrelationLimit> {
    return <Observable<CorrelationLimit>>this.httpClient.get(
      `${BaseSettings.API_ENDPOINT}${AppSettings.CORRELATION_SET_KEY}/limit`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  getRollingCorrelations(idCorrelationSet: number, securityIdsPairs: number[][]): Observable<CorrelationRollingResult[]> {
    let params = new HttpParams();
    params = params.append('securityIdsPairs', [].concat(...securityIdsPairs).join(','));
    return <Observable<CorrelationRollingResult[]>>this.httpClient.get(`${BaseSettings.API_ENDPOINT}${AppSettings.CORRELATION_SET_KEY}`
      + `/corrrolling/${idCorrelationSet}`, {
      headers: this.prepareHeaders(),
      params
    }).pipe(catchError(this.handleError.bind(this)));
  }

  getCorrelationSetInstrumentLimit(idCorrelationSet: number): Observable<TenantLimit> {
    return <Observable<TenantLimit>>this.httpClient.get(
      `${BaseSettings.API_ENDPOINT}${AppSettings.CORRELATION_SET_KEY}/limit/${idCorrelationSet}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }
}
