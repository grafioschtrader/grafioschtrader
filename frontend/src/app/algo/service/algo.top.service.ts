import { HttpResourceRequest } from '@angular/common/http';
import { Service } from '@angular/core';
import { Observable } from 'rxjs';
import { catchError } from 'rxjs/operators';
import {
  AlgoTopCreate,
  AlgoTopCreateFromPortfolio,
  AlgoTopCreateFromWatchlist
} from '../../entities/backend/algo.top.create';
import { BaseSettings } from '../../lib/base.settings';
import { DeleteService } from '../../lib/datashowbase/delete.service';
import { ServiceEntityUpdate } from '../../lib/edit/service.entity.update';
import { AuthServiceWithLogout } from '../../lib/login/service/base.auth.service.with.logout';
import { AppSettings } from '../../shared/app.settings';
import { AlgoHierarchyDto } from '../model/algo-hierarchy-dto';
import { AlgoTop } from '../model/algo.top';

@Service()
export class AlgoTopService
  extends AuthServiceWithLogout<AlgoTop>
  implements DeleteService, ServiceEntityUpdate<AlgoTop>
{
  /** Builds the cancellable read used by signal-driven views and observable callers. */
  getHierarchyRequest(idAlgoTop: number) {
    return {
      url: `${BaseSettings.API_ENDPOINT}${AppSettings.ALGO_TOP_KEY}/${idAlgoTop}/hierarchy`,
      headers: this.prepareHeaders()
    } satisfies HttpResourceRequest;
  }

  getAlgoTopByIdTenantOrderByName(): Observable<AlgoTop[]> {
    return this.httpClient
      .get<AlgoTop[]>(
        `${BaseSettings.API_ENDPOINT}${AppSettings.ALGO_TOP_KEY}/${BaseSettings.TENANT_KEY}`,
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  getAlgoTopByIdAlgoAssetclassSecurity(idAlgoAssetclassSecurity: number): Observable<AlgoTop> {
    return this.httpClient
      .get<AlgoTop>(
        `${BaseSettings.API_ENDPOINT}${AppSettings.ALGO_TOP_KEY}/${idAlgoAssetclassSecurity}`,
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  /** Loads the hierarchy together with its calculated totals and warning fields. */
  getHierarchy(idAlgoTop: number): Observable<AlgoHierarchyDto> {
    const { url, ...options } = this.getHierarchyRequest(idAlgoTop);
    return this.httpClient.get<AlgoHierarchyDto>(url, options).pipe(catchError(this.handleError.bind(this)));
  }

  public create(algoTopCreate: AlgoTopCreate): Observable<AlgoTop> {
    return this.httpClient
      .post<AlgoTop>(`${BaseSettings.API_ENDPOINT}${AppSettings.ALGO_TOP_KEY}/create`, algoTopCreate, {
        headers: this.prepareHeaders()
      })
      .pipe(catchError(this.handleError.bind(this)));
  }

  public update(algoTop: AlgoTop): Observable<AlgoTop> {
    return this.updateEntity(algoTop, algoTop.idAlgoAssetclassSecurity, AppSettings.ALGO_TOP_KEY);
  }

  public createFromPortfolio(dto: AlgoTopCreateFromPortfolio): Observable<AlgoTop> {
    return this.httpClient
      .post<AlgoTop>(`${BaseSettings.API_ENDPOINT}${AppSettings.ALGO_TOP_KEY}/createfromportfolio`, dto, {
        headers: this.prepareHeaders()
      })
      .pipe(catchError(this.handleError.bind(this)));
  }

  public createFromWatchlist(dto: AlgoTopCreateFromWatchlist): Observable<AlgoTop> {
    return this.httpClient
      .post<AlgoTop>(`${BaseSettings.API_ENDPOINT}${AppSettings.ALGO_TOP_KEY}/createfromwatchlist`, dto, {
        headers: this.prepareHeaders()
      })
      .pipe(catchError(this.handleError.bind(this)));
  }

  public normalizePercentages(idAlgoAssetclassSecurity: number): Observable<void> {
    return this.httpClient
      .put<void>(
        `${BaseSettings.API_ENDPOINT}${AppSettings.ALGO_TOP_KEY}/normalizepercentages/${idAlgoAssetclassSecurity}`,
        null,
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  public normalizeAllPercentages(idAlgoAssetclassSecurity: number): Observable<void> {
    return this.httpClient
      .put<void>(
        `${BaseSettings.API_ENDPOINT}${AppSettings.ALGO_TOP_KEY}/normalizeallpercentages/${idAlgoAssetclassSecurity}`,
        null,
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  public deleteEntity(idAlgoAssetclassSecurity: number): Observable<any> {
    return this.httpClient
      .delete(`${BaseSettings.API_ENDPOINT}${AppSettings.ALGO_TOP_KEY}/${idAlgoAssetclassSecurity}`, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }
}
