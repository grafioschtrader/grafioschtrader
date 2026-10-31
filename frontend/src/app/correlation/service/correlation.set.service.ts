import { AuthServiceWithLogout } from '../../lib/login/service/base.auth.service.with.logout';
import {
  CorrelationLimit,
  CorrelationRollingResult,
  CorrelationSet,
  SamplingPeriodType
} from '../../entities/correlation.set';
import { ServiceEntityUpdate } from '../../lib/edit/service.entity.update';
import { HttpParams, HttpResourceRequest } from '@angular/common/http';
import { Service } from '@angular/core';
import { AppSettings } from '../../shared/app.settings';
import { DeleteService } from '../../lib/datashowbase/delete.service';
import { Observable } from 'rxjs';
import { catchError } from 'rxjs/operators';
import type { TenantLimit } from '../../shared/types/tenant.limit';
import { SecuritycurrencySearch } from '../../entities/search/securitycurrency.search';
import { SecuritycurrencyLists } from '../../entities/view/securitycurrency.lists';
import { AppHelper } from '../../lib/helper/app.helper';
import { AddSearchToListService } from '../../watchlist/component/add-instrument-table.component';
import { BaseSettings } from '../../lib/base.settings';

@Service()
export class CorrelationSetService
  extends AuthServiceWithLogout<CorrelationSet>
  implements DeleteService, ServiceEntityUpdate<CorrelationSet>, AddSearchToListService<CorrelationSet>
{
  getCorrelationSetByTenant(): Observable<CorrelationSet[]> {
    return this.httpClient
      .get<CorrelationSet[]>(
        `${BaseSettings.API_ENDPOINT}` + `${AppSettings.CORRELATION_SET_KEY}/tenant`,
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  /**
   * Builds the request of the correlation matrix for an `httpResource` returning a `CorrelationResult`. Its
   * failures are reported through `handleResourceError`.
   *
   * @param idCorrelationSet the correlation set to calculate
   * @returns the GET request of the calculation
   */
  getCalculationByCorrelationSetRequest(idCorrelationSet: number): HttpResourceRequest {
    return {
      url: `${BaseSettings.API_ENDPOINT}${AppSettings.CORRELATION_SET_KEY}/calculation/${idCorrelationSet}`,
      headers: this.prepareHeaders()
    };
  }

  update(correlationSet: CorrelationSet) {
    return super.updateEntity(
      this.toBackendPayload(correlationSet),
      correlationSet.idCorrelationSet,
      AppSettings.CORRELATION_SET_KEY
    );
  }

  /**
   * The sampling-period select is a string field, so its form value is the enum name (e.g.
   * 'DAILY_RETURNS'). The backend {@code CorrelationSet.samplingPeriod} is a numeric byte and only
   * deserializes the numeric enum value, so convert the name to its number before sending. The
   * response still carries the name, so the display/read path is unaffected. A shallow copy is used
   * so the caller's entity keeps the name it needs for the select.
   *
   * @param correlationSet the correlation set as held by the component
   * @returns a payload whose samplingPeriod is the numeric enum value expected by the backend
   */
  private toBackendPayload(correlationSet: CorrelationSet): CorrelationSet {
    if (typeof correlationSet.samplingPeriod !== 'string') {
      return correlationSet;
    }
    const payload: CorrelationSet = Object.assign(Object.create(Object.getPrototypeOf(correlationSet)), correlationSet);
    payload.samplingPeriod = SamplingPeriodType[correlationSet.samplingPeriod as keyof typeof SamplingPeriodType];
    return payload;
  }

  public deleteEntity(idCorrelationSet: number): Observable<any> {
    return this.httpClient
      .delete(
        `${BaseSettings.API_ENDPOINT}${AppSettings.CORRELATION_SET_KEY}/` + `${idCorrelationSet}`,
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  addSecuritycurrenciesToList(
    idCorrelationSet: number,
    securitycurrencyLists: SecuritycurrencyLists
  ): Observable<CorrelationSet> {
    return this.httpClient
      .put<CorrelationSet>(
        `${BaseSettings.API_ENDPOINT}${AppSettings.CORRELATION_SET_KEY}/` + `${idCorrelationSet}/addSecuritycurrency`,
        securitycurrencyLists,
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  removeInstrumentFromCorrelationSet(idCorrelationSet: number, idSecuritycurrency: number): Observable<CorrelationSet> {
    return this.httpClient
      .delete<CorrelationSet>(
        `${BaseSettings.API_ENDPOINT}${AppSettings.CORRELATION_SET_KEY}/` +
          `${idCorrelationSet}/removeinstrument/${idSecuritycurrency}`,
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  searchByCriteria(
    idCorrelationSet: number,
    securitycurrencySearch: SecuritycurrencySearch
  ): Observable<SecuritycurrencyLists> {
    return this.httpClient
      .get<SecuritycurrencyLists>(
        `${BaseSettings.API_ENDPOINT}${AppSettings.CORRELATION_SET_KEY}` + `/${idCorrelationSet}/search`,
        {
          headers: this.prepareHeaders(),
          params: AppHelper.getHttpParamsOfObjectAllowBooleanNullFields(securitycurrencySearch, ['onlyTenantPrivate'])
        }
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  getCorrelationSetLimit(): Observable<CorrelationLimit> {
    return this.httpClient
      .get<CorrelationLimit>(`${BaseSettings.API_ENDPOINT}${AppSettings.CORRELATION_SET_KEY}/limit`, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }

  getRollingCorrelations(
    idCorrelationSet: number,
    securityIdsPairs: number[][]
  ): Observable<CorrelationRollingResult[]> {
    let params = new HttpParams();
    params = params.append('securityIdsPairs', securityIdsPairs.flat().join(','));
    return this.httpClient
      .get<CorrelationRollingResult[]>(
        `${BaseSettings.API_ENDPOINT}${AppSettings.CORRELATION_SET_KEY}` + `/corrrolling/${idCorrelationSet}`,
        {
          headers: this.prepareHeaders(),
          params
        }
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  getCorrelationSetInstrumentLimit(idCorrelationSet: number): Observable<TenantLimit> {
    return this.httpClient
      .get<TenantLimit>(
        `${BaseSettings.API_ENDPOINT}${AppSettings.CORRELATION_SET_KEY}/limit/${idCorrelationSet}`,
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }
}
