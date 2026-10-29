import { HttpParams, HttpResourceRequest } from '@angular/common/http';
import { Service } from '@angular/core';
import { Observable } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { BaseSettings } from '../../base.settings';
import { AuthServiceWithLogout } from '../../login/service/base.auth.service.with.logout';
import { GTNetExchangeLogTree } from '../model/gtnet-exchange-log';

/**
 * Service for GTNet exchange log operations.
 */
@Service()
export class GTNetExchangeLogService extends AuthServiceWithLogout<GTNetExchangeLogTree> {
  getAllExchangeLogTreesRequest(entityKind: string) {
    return {
      url: `${BaseSettings.API_ENDPOINT}gtnetexchangelog/trees`,
      headers: this.prepareHeaders(),
      params: new HttpParams().set('entityKind', entityKind)
    } satisfies HttpResourceRequest;
  }

  /**
   * Gets the exchange log tree for a specific GTNet and entity kind.
   *
   * @param idGtNet the GTNet identifier
   * @param entityKind the entity kind (LAST_PRICE, HISTORICAL_PRICES, or SECURITY_METADATA)
   */
  getExchangeLogTree(idGtNet: number, entityKind: string): Observable<GTNetExchangeLogTree> {
    const params = new HttpParams().set('entityKind', entityKind);
    return this.httpClient
      .get<GTNetExchangeLogTree>(`${BaseSettings.API_ENDPOINT}gtnetexchangelog/tree/${idGtNet}`, {
        ...this.getHeaders(),
        params
      })
      .pipe(catchError(this.handleError.bind(this)));
  }

  /**
   * Gets all exchange log trees for GTNets that have communication enabled for the specified entity kind.
   *
   * @param entityKind the entity kind (LAST_PRICE, HISTORICAL_PRICES, or SECURITY_METADATA)
   */
  getAllExchangeLogTrees(entityKind: string): Observable<GTNetExchangeLogTree[]> {
    const { url, ...options } = this.getAllExchangeLogTreesRequest(entityKind);
    return this.httpClient.get<GTNetExchangeLogTree[]>(url, options).pipe(catchError(this.handleError.bind(this)));
  }
}
