import {Injectable} from '@angular/core';
import {HttpClient, HttpParams} from '@angular/common/http';
import {Observable} from 'rxjs';
import {catchError} from 'rxjs/operators';

import {AuthServiceWithLogout} from '../../login/service/base.auth.service.with.logout';
import {DeleteService} from '../../datashowbase/delete.service';
import {ServiceEntityUpdate} from '../../edit/service.entity.update';
import {EntityLimit} from '../../entities/entity.limit';
import {LimitKeyDefinition} from '../model/limit.key.definition';
import {ValueKeyHtmlSelectOptions} from '../../dynamic-form/models/value.key.html.select.options';
import {LoginService} from '../../login/service/log-in.service';
import {MessageToastService} from '../../message/message.toast.service';
import {BaseSettings} from '../../base.settings';

@Injectable()
export class EntityLimitService extends AuthServiceWithLogout<EntityLimit> implements DeleteService,
  ServiceEntityUpdate<EntityLimit> {

  constructor(loginService: LoginService, httpClient: HttpClient, messageToastService: MessageToastService) {
    super(loginService, httpClient, messageToastService);
  }

  public getAllEntityLimits(): Observable<EntityLimit[]> {
    return <Observable<EntityLimit[]>>this.httpClient.get(`${BaseSettings.API_ENDPOINT}${BaseSettings.ENTITY_LIMIT_KEY}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  /**
   * Loads the limit keys an administrator may configure. Keys the given user already holds a row for are filtered out
   * by the backend, so the picker cannot produce a duplicate.
   *
   * @param idUser the user the row belongs to, or undefined for a row that is not user-scoped
   * @param idEntityLimit the row currently being edited, whose own key must stay in the list
   */
  public getLimitKeyDefinitions(idUser?: number, idEntityLimit?: number): Observable<LimitKeyDefinition[]> {
    let httpParams = new HttpParams();
    if (idUser != null) {
      httpParams = httpParams.set('idUser', idUser.toString());
    }
    if (idEntityLimit != null) {
      httpParams = httpParams.set('idEntityLimit', idEntityLimit.toString());
    }
    return <Observable<LimitKeyDefinition[]>>this.httpClient.get(
      `${BaseSettings.API_ENDPOINT}${BaseSettings.ENTITY_LIMIT_KEY}/keys`,
      {headers: this.prepareHeaders(), params: httpParams}).pipe(catchError(this.handleError.bind(this)));
  }

  /**
   * Loads the roles a limit may be narrowed to. Served by the backend rather than hardcoded here, so that a role
   * posted directly against the REST API is validated against the same set.
   */
  public getRoles(): Observable<ValueKeyHtmlSelectOptions[]> {
    return <Observable<ValueKeyHtmlSelectOptions[]>>this.httpClient.get(
      `${BaseSettings.API_ENDPOINT}${BaseSettings.ENTITY_LIMIT_KEY}/roles`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  public update(entityLimit: EntityLimit): Observable<EntityLimit> {
    return this.updateEntity(entityLimit, entityLimit.idEntityLimit, BaseSettings.ENTITY_LIMIT_KEY);
  }

  public deleteEntity(idEntityLimit: number): Observable<any> {
    return this.httpClient.delete(`${BaseSettings.API_ENDPOINT}${BaseSettings.ENTITY_LIMIT_KEY}/${idEntityLimit}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }
}
