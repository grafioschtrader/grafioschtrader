import { Service } from '@angular/core';
import { Observable, catchError } from 'rxjs';
import { AuthServiceWithLogout } from '../../login/service/base.auth.service.with.logout';
import { BaseSettings } from '../../base.settings';
import { DeleteService } from '../../datashowbase/delete.service';
import {
  AccessTokenClientInfo,
  OAuthAuthorization,
  UserAccessToken,
  UserAccessTokenCreated,
  UserAccessTokenCreateRequest
} from '../model/user-access-token';

/** REST access to the personal access tokens of the logged-in user. */
@Service()
export class UserAccessTokenService extends AuthServiceWithLogout<UserAccessToken> implements DeleteService {
  public static readonly USER_ACCESS_TOKEN = 'UserAccessToken';
  private readonly endpoint = BaseSettings.API_ENDPOINT + 'useraccesstoken';

  getAll(): Observable<UserAccessToken[]> {
    return this.httpClient
      .get<UserAccessToken[]>(this.endpoint, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }

  create(request: UserAccessTokenCreateRequest): Observable<UserAccessTokenCreated> {
    return this.httpClient
      .post<UserAccessTokenCreated>(this.endpoint, request, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }

  getClientInfo(): Observable<AccessTokenClientInfo> {
    return this.httpClient
      .get<AccessTokenClientInfo>(this.endpoint + '/clientinfo', this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }

  getOAuthAuthorizations(): Observable<OAuthAuthorization[]> {
    return this.httpClient
      .get<OAuthAuthorization[]>(this.endpoint + '/oauthauthorization', this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }

  deleteOAuthAuthorization(id: number): Observable<any> {
    return this.httpClient
      .delete(this.endpoint + '/oauthauthorization/' + id, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }

  deleteEntity(id: number): Observable<any> {
    return this.httpClient
      .delete(this.endpoint + '/' + id, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }
}
