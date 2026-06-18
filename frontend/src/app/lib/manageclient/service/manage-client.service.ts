import {Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Router} from '@angular/router';
import {Observable} from 'rxjs';
import {catchError} from 'rxjs/operators';

import {AuthServiceWithLogout} from '../../login/service/base.auth.service.with.logout';
import {LoginService} from '../../login/service/log-in.service';
import {MessageToastService} from '../../message/message.toast.service';
import {BaseSettings} from '../../base.settings';
import {GlobalSessionNames} from '../../global.session.names';
import {TenantAccessInfo} from '../model/tenant-access-info';
import {SharedViewerInfo} from '../model/shared-viewer-info';

/**
 * Service for the manage-client feature (g.use.manageclient): listing the tenants the current user may access,
 * switching between them, and creating a new managed client (a tenant with a read-only client login).
 *
 * Lives in the shared library because managing tenants/users on behalf of clients is a generic capability.
 */
@Injectable()
export class ManageClientService extends AuthServiceWithLogout<any> {

  constructor(loginService: LoginService, httpClient: HttpClient, messageToastService: MessageToastService,
    private router: Router) {
    super(loginService, httpClient, messageToastService);
  }

  /** Lists the home tenant plus all tenants the user has been granted access to. */
  public getAccessibleTenants(): Observable<TenantAccessInfo[]> {
    return <Observable<TenantAccessInfo[]>>this.httpClient.get(
      `${BaseSettings.API_ENDPOINT}${BaseSettings.TENANT_KEY}/accessible`,
      {headers: this.prepareHeaders()}).pipe(catchError(this.handleError.bind(this)));
  }

  /** Creates a managed client: a new tenant with a read-only client login; the client is e-mailed their credentials. */
  public createClient(request: { email: string; password: string }): Observable<void> {
    return <Observable<void>>this.httpClient.post(
      `${BaseSettings.API_ENDPOINT}${BaseSettings.TENANT_KEY}/createclient`, request,
      {headers: this.prepareHeaders()}).pipe(catchError(this.handleError.bind(this)));
  }

  /** Deletes a managed client entirely (its tenant, all data and the read-only client user). */
  public deleteClient(idTenant: number): Observable<void> {
    return <Observable<void>>this.httpClient.delete(
      `${BaseSettings.API_ENDPOINT}${BaseSettings.TENANT_KEY}/client/${idTenant}`,
      {headers: this.prepareHeaders()}).pipe(catchError(this.handleError.bind(this)));
  }

  /**
   * Grants read access to the current owner's own portfolio. If the e-mail is already registered a read grant is
   * created; otherwise a new read-only viewer login is created and e-mailed the password. The password is ignored for
   * an already-registered recipient.
   */
  public shareReadAccess(request: { email: string; password: string }): Observable<void> {
    return <Observable<void>>this.httpClient.post(
      `${BaseSettings.API_ENDPOINT}${BaseSettings.TENANT_KEY}/share`, request,
      {headers: this.prepareHeaders()}).pipe(catchError(this.handleError.bind(this)));
  }

  /**
   * Resolves the status of a recipient e-mail before sharing: 'SELF', 'ALREADY_SHARED', 'EXISTS' (registered user, no
   * password needed) or 'NEW' (not registered, a password is required). Read-only lookup; does not change any data.
   */
  public checkRecipientStatus(email: string): Observable<{status: string}> {
    return <Observable<{status: string}>>this.httpClient.get(
      `${BaseSettings.API_ENDPOINT}${BaseSettings.TENANT_KEY}/share/recipient-status`,
      {headers: this.prepareHeaders(), params: {email}}).pipe(catchError(this.handleError.bind(this)));
  }

  /** Lists everyone who can read the current owner's portfolio (read grants and read-only viewer logins). */
  public getSharedViewers(): Observable<SharedViewerInfo[]> {
    return <Observable<SharedViewerInfo[]>>this.httpClient.get(
      `${BaseSettings.API_ENDPOINT}${BaseSettings.TENANT_KEY}/shares`,
      {headers: this.prepareHeaders()}).pipe(catchError(this.handleError.bind(this)));
  }

  /** Revokes a person's read access to the current owner's portfolio (removes a grant or deletes a viewer login). */
  public revokeShare(idUser: number): Observable<void> {
    return <Observable<void>>this.httpClient.delete(
      `${BaseSettings.API_ENDPOINT}${BaseSettings.TENANT_KEY}/share/${idUser}`,
      {headers: this.prepareHeaders()}).pipe(catchError(this.handleError.bind(this)));
  }

  /** Switches the session to another accessible tenant and returns the new token + read-only flag. */
  public switchTenant(idTargetTenant: number): Observable<{token: string; readOnly: string}> {
    return <Observable<{token: string; readOnly: string}>>this.httpClient.post(
      `${BaseSettings.API_ENDPOINT}${BaseSettings.TENANT_KEY}/switchto/${idTargetTenant}`, null,
      {headers: this.prepareHeaders()}).pipe(catchError(this.handleError.bind(this)));
  }

  /**
   * Switches to the given tenant, updates the session (JWT, current tenant, read-only flag, home-tenant bookmark) and
   * reloads the application so the navigation tree, menus and views reflect the new tenant. Before reloading it
   * navigates to the neutral {@code mainview} base route, dropping any tenant-specific deep URL (e.g. a watchlist or
   * portfolio id) that does not exist for the target tenant; otherwise the new tenant would open onto a broken view.
   *
   * @param idTargetTenant the tenant to switch into
   * @param backToHome     true when returning to the user's own home tenant
   */
  public switchAndReload(idTargetTenant: number, backToHome: boolean): void {
    this.switchTenant(idTargetTenant).subscribe({
      next: (response) => {
        if (!backToHome && !sessionStorage.getItem(GlobalSessionNames.MAIN_ID_TENANT)) {
          sessionStorage.setItem(GlobalSessionNames.MAIN_ID_TENANT,
            sessionStorage.getItem(GlobalSessionNames.ID_TENANT));
        }
        sessionStorage.setItem(GlobalSessionNames.JWT, response.token);
        sessionStorage.setItem(GlobalSessionNames.ID_TENANT, idTargetTenant.toString());
        sessionStorage.setItem(GlobalSessionNames.TENANT_READ_ONLY, JSON.stringify(response.readOnly === 'true'));
        if (backToHome) {
          sessionStorage.removeItem(GlobalSessionNames.MAIN_ID_TENANT);
        }
        this.router.navigate(['/' + BaseSettings.MAINVIEW_KEY]).then(() => window.location.reload());
      },
      error: err => console.error('Error switching tenant:', err)
    });
  }
}
