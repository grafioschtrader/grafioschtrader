import {Injectable} from '@angular/core';
import {HttpClient, HttpParams} from '@angular/common/http';
import {Observable} from 'rxjs';
import {catchError} from 'rxjs/operators';
import {AuthServiceWithLogout} from '../../lib/login/service/base.auth.service.with.logout';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {LoginService} from '../../lib/login/service/log-in.service';
import {BaseSettings} from '../../lib/base.settings';
import {GTNetExchangeLogTree} from '../model/gtnet-exchange-log';
import {GTNetExchangeKindType} from '../model/gtnet';

/**
 * Service for GTNet exchange log operations.
 */
@Injectable({providedIn: 'root'})
export class GTNetExchangeLogService extends AuthServiceWithLogout<GTNetExchangeLogTree> {

  constructor(loginService: LoginService, httpClient: HttpClient, messageToastService: MessageToastService) {
    super(loginService, httpClient, messageToastService);
  }

  /**
   * Gets the exchange log tree for a specific GTNet and entity kind.
   *
   * @param idGtNet the GTNet identifier
   * @param entityKind the entity kind (LAST_PRICE or HISTORICAL_PRICES)
   */
  getExchangeLogTree(idGtNet: number, entityKind: GTNetExchangeKindType): Observable<GTNetExchangeLogTree> {
    const params = new HttpParams().set('entityKind', this.getEntityKindName(entityKind));
    return this.httpClient.get<GTNetExchangeLogTree>(
      `${BaseSettings.API_ENDPOINT}gtnetexchangelog/tree/${idGtNet}`,
      {...this.getHeaders(), params}
    ).pipe(catchError(this.handleError.bind(this)));
  }

  /**
   * Gets all exchange log trees for GTNets that have communication enabled for the specified entity kind.
   *
   * @param entityKind the entity kind (LAST_PRICE or HISTORICAL_PRICES)
   */
  getAllExchangeLogTrees(entityKind: GTNetExchangeKindType): Observable<GTNetExchangeLogTree[]> {
    const params = new HttpParams().set('entityKind', this.getEntityKindName(entityKind));
    return this.httpClient.get<GTNetExchangeLogTree[]>(
      `${BaseSettings.API_ENDPOINT}gtnetexchangelog/trees`,
      {...this.getHeaders(), params}
    ).pipe(catchError(this.handleError.bind(this)));
  }

  /**
   * Converts enum value to the backend enum name.
   */
  private getEntityKindName(entityKind: GTNetExchangeKindType): string {
    return entityKind === GTNetExchangeKindType.LAST_PRICE ? 'LAST_PRICE' : 'HISTORICAL_PRICES';
  }
}
