import { Service } from '@angular/core';
import { Observable, catchError } from 'rxjs';
import { AuthServiceWithLogout } from '../../login/service/base.auth.service.with.logout';
import { BaseSettings } from '../../base.settings';
import { SuccessfullyChanged } from '../../login/model/successfully.changed';
import { RecoveryCodes, SecondFactorState, TotpSetup } from '../model/sign-in-security';

/**
 * REST access to the sign-in methods of the signed-in user. An answer to a change of the credentials carries a
 * replacement token, which the shared authInterceptor adopts; the other sessions of the user end.
 */
@Service()
export class SignInSecurityService extends AuthServiceWithLogout<unknown> {
  private readonly endpoint = BaseSettings.API_ENDPOINT + 'user/';

  getState(): Observable<SecondFactorState> {
    return this.httpClient
      .get<SecondFactorState>(this.endpoint + 'secondfactor', this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }

  /**
   * Starts a setup of the authenticator app.
   *
   * @param password - Password of the user
   * @param code - Code of the enabled authenticator app or a recovery code; needed while a factor is enabled
   */
  startTotpSetup(password: string, code?: string): Observable<TotpSetup> {
    return this.httpClient
      .post<TotpSetup>(this.endpoint + 'totp/setup', { password, code }, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }

  /** Confirms the setup with a code of the new secret; the answer holds the recovery codes, readable only now. */
  enableTotp(code: string): Observable<RecoveryCodes> {
    return this.httpClient
      .post<RecoveryCodes>(this.endpoint + 'totp/enable', { code }, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }

  /** Replaces the recovery codes after a current code of the authenticator app. */
  renewRecoveryCodes(code: string): Observable<RecoveryCodes> {
    return this.httpClient
      .post<RecoveryCodes>(this.endpoint + 'totp/recoverycodes', { code }, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }

  /** Disables the authenticator app after the password and a code of the app or a recovery code. */
  disableTotp(password: string, code: string): Observable<SuccessfullyChanged> {
    return this.httpClient
      .post<SuccessfullyChanged>(this.endpoint + 'totp/disable', { password, code }, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }
}
