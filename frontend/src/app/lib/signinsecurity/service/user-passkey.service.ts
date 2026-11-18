import { Service } from '@angular/core';
import { Observable, catchError } from 'rxjs';
import { AuthServiceWithLogout } from '../../login/service/base.auth.service.with.logout';
import { BaseSettings } from '../../base.settings';
import { DeleteService } from '../../datashowbase/delete.service';
import { PasskeyCredentialJSON, PasskeyOptions, UserPasskey } from '../model/sign-in-security';

/**
 * REST access to the passkeys of the signed-in user. Adding and deleting a passkey ends the other sessions; the answer
 * carries a replacement token, which the shared authInterceptor adopts.
 */
@Service()
export class UserPasskeyService extends AuthServiceWithLogout<UserPasskey> implements DeleteService {
  public static readonly USER_PASSKEY = 'UserPasskey';
  private readonly endpoint = BaseSettings.API_ENDPOINT + 'user/passkey';

  getAll(): Observable<UserPasskey[]> {
    return this.httpClient
      .get<UserPasskey[]>(this.endpoint, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }

  /**
   * Starts a registration after the proof of a new sign-in method.
   *
   * @param password - Password of the user
   * @param code - Code of the authenticator app or a recovery code, while one is enabled
   */
  registrationOptions(
    password: string,
    code?: string
  ): Observable<PasskeyOptions<PublicKeyCredentialCreationOptionsJSON>> {
    return this.httpClient
      .post<PasskeyOptions<PublicKeyCredentialCreationOptionsJSON>>(
        this.endpoint + '/options',
        { password, code },
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  /** Completes a registration with the credential the browser created. */
  register(challengeId: string, label: string, credential: PasskeyCredentialJSON): Observable<UserPasskey> {
    return this.httpClient
      .post<UserPasskey>(this.endpoint, { challengeId, label, credential }, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }

  rename(idUserPasskey: number, label: string): Observable<UserPasskey> {
    return this.httpClient
      .put<UserPasskey>(`${this.endpoint}/${idUserPasskey}`, { label }, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }

  deleteEntity(id: number | string): Observable<void> {
    return this.httpClient
      .delete<void>(`${this.endpoint}/${id}`, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }
}
