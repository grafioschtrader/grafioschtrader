import {Injectable} from '@angular/core';
import {AuthServiceWithLogout} from '../../login/service/base.auth.service.with.logout';
import {ServiceEntityUpdate} from '../../edit/service.entity.update';
import {User} from '../../entities/user';
import {LoginService} from '../../login/service/log-in.service';
import {HttpClient} from '@angular/common/http';
import {MessageToastService} from '../../message/message.toast.service';
import {Observable} from 'rxjs';
import {catchError} from 'rxjs/operators';
import {DeleteService} from '../../datashowbase/delete.service';
import {ValueKeyHtmlSelectOptions} from '../../dynamic-form/models/value.key.html.select.options';
import {BaseSettings} from '../../base.settings';

@Injectable()
export class UserAdminService extends AuthServiceWithLogout<User> implements DeleteService, ServiceEntityUpdate<User> {

  constructor(loginService: LoginService, httpClient: HttpClient, messageToastService: MessageToastService) {
    super(loginService, httpClient, messageToastService);
  }

  public getAllUsers(): Observable<User[]> {
    return <Observable<User[]>>this.httpClient.get(`${BaseSettings.API_ENDPOINT}${BaseSettings.USER_ADMIN_KEY}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  // public getUserByIdUser(idUser: number): Observable<User> {
  //   return <Observable<User>>this.httpClient.get(`${BaseSettings.API_ENDPOINT}${AppSettings.USER_ADMIN_KEY}/${idUser}`,
  //     this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  // }

  public getIdUserAndNicknameExcludeMe(): Observable<ValueKeyHtmlSelectOptions[]> {
    return <Observable<ValueKeyHtmlSelectOptions[]>>this.httpClient.get(
      `${BaseSettings.API_ENDPOINT}${BaseSettings.USER_ADMIN_KEY}/idnicknameexcludeme`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  public update(user: User): Observable<User> {
    return this.updateEntity(user, user.idUser, BaseSettings.USER_ADMIN_KEY);
  }

  public deleteEntity(idUser: number): Observable<any> {
    return this.httpClient.delete(`${BaseSettings.API_ENDPOINT}${BaseSettings.USER_ADMIN_KEY}/`
      + `${idUser}`, this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  public moveCreatedByUserToOtherUser(fromIdUser: number, toIdUser: number): Observable<number> {
    return this.httpClient.patch(`${BaseSettings.API_ENDPOINT}${BaseSettings.USER_ADMIN_KEY}/`
      + `${fromIdUser}/${toIdUser}`, null,
      {headers: this.prepareHeaders()}).pipe(catchError(this.handleError.bind(this)));
  }
}
