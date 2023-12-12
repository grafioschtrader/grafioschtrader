import {Injectable} from '@angular/core';
import {AuthServiceWithLogout} from '../../shared/login/service/base.auth.service.with.logout';
import {DeleteService} from '../../shared/datashowbase/delete.service';
import {ServiceEntityUpdate} from '../../shared/edit/service.entity.update';
import {UserEntityChangeLimit} from '../../entities/user.entity.change.limit';
import {LoginService} from '../../shared/login/service/log-in.service';
import {HttpClient, HttpParams} from '@angular/common/http';
import {MessageToastService} from '../../shared/message/message.toast.service';
import {Observable} from 'rxjs';
import {AppSettings} from '../../shared/app.settings';
import {catchError} from 'rxjs/operators';
import {ValueKeyHtmlSelectOptions} from '../../dynamic-form/models/value.key.html.select.options';


@Injectable()
export class UserEntityChangeLimitService extends AuthServiceWithLogout<UserEntityChangeLimit> implements DeleteService,
  ServiceEntityUpdate<UserEntityChangeLimit> {

  constructor(loginService: LoginService, httpClient: HttpClient, messageToastService: MessageToastService) {
    super(loginService, httpClient, messageToastService);
  }

  getPublicEntitiesAsHtmlSelectOptions(idUser: number,
                                       idUserEntityChangeLimit: number): Observable<ValueKeyHtmlSelectOptions[]> {
    return <Observable<ValueKeyHtmlSelectOptions[]>>this.httpClient.get(
      `${AppSettings.API_ENDPOINT}${AppSettings.USER_ENTITY_LIMIT_KEY}/${idUser}/entities`,
      {headers: this.prepareHeaders(), params: this.getHttpParams(idUserEntityChangeLimit)})
      .pipe(catchError(this.handleError.bind(this)));
  }

  public update(userEntityChangeLimit: UserEntityChangeLimit): Observable<UserEntityChangeLimit> {
    return this.updateEntity(userEntityChangeLimit, userEntityChangeLimit.idUserEntityChangeLimit, AppSettings.USER_ENTITY_LIMIT_KEY);
  }

  public deleteEntity(userEntityChangeLimit: number): Observable<any> {
    return this.httpClient.delete(`${AppSettings.API_ENDPOINT}${AppSettings.USER_ENTITY_LIMIT_KEY}/`
      + `${userEntityChangeLimit}`, this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  private getHttpParams(idUserEntityChangeLimit: number): HttpParams {
    return idUserEntityChangeLimit ? new HttpParams().set('idUserEntityChangeLimit', idUserEntityChangeLimit.toString()) : null;
  }
}
