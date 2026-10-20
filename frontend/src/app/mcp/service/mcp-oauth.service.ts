import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, catchError } from 'rxjs';
import { AuthServiceWithLogout } from '../../lib/login/service/base.auth.service.with.logout';
import { LoginService } from '../../lib/login/service/log-in.service';
import { MessageToastService } from '../../lib/message/message.toast.service';
import { BaseSettings } from '../../lib/base.settings';

/** The OAuth parameters a client sends to the authorization endpoint, in their wire names. */
export interface OAuthAuthorizationRequest {
  response_type: string;
  client_id: string;
  redirect_uri: string;
  state: string;
  code_challenge: string;
  code_challenge_method: string;
  scope: string;
  resource: string;
}

/** Outcome of the server-side validation of an authorization request. */
export interface OAuthValidationResponse {
  authorizationRequestId?: string;
  clientName?: string;
  clientHost?: string;
  scope?: string;
  /** Set when the request is refused */
  error?: string;
  /** Set when the refusal may be reported to the client by redirecting there */
  redirect?: string;
}

/**
 * Server calls of the OAuth authorization page. The validation is anonymous, because a client sends the user here
 * before any login; the consent needs the user's login.
 */
@Injectable({ providedIn: 'root' })
export class McpOAuthService extends AuthServiceWithLogout<any> {
  private readonly endpoint = BaseSettings.API_ENDPOINT + 'oauth/';

  constructor(
    login: LoginService,
    private plainHttp: HttpClient,
    messages: MessageToastService
  ) {
    super(login, plainHttp, messages);
  }

  /** Errors are part of the answer; the page decides between showing them and redirecting. */
  validate(request: OAuthAuthorizationRequest): Observable<OAuthValidationResponse> {
    return this.plainHttp.post<OAuthValidationResponse>(this.endpoint + 'authorize/validate', request);
  }

  consent(authorizationRequestId: string, approve: boolean): Observable<{ redirect: string }> {
    return this.httpClient
      .post<{ redirect: string }>(this.endpoint + 'consent', { authorizationRequestId, approve }, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }
}
