import { HttpContext, HttpHeaders, HttpResponse } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { Observable } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { GlobalSessionNames } from '../../../app/lib/global.session.names';
import { SKIP_AUTH_HEADERS } from '../../../app/lib/login/service/auth.http.interceptor';
import { BaseAuthService } from '../../../app/lib/login/service/base.auth.service';
import { ConfigurationWithLoginGT } from '../../../app/shared/login/configuration-with-login-gt';
import { MobileFormatService } from './mobile.format.service';

/** The claims of the GT token the mobile application reads. */
interface TokenClaims {
  idTenant: string;
  idUser: string;
  localeStr: string;
}

/**
 * Signs in and out of the mobile application. It stores the token and the locale settings under the same session keys
 * as the desktop application, so the shared interceptor and the shared number and date formats work unchanged. Like
 * the desktop, it keeps the token in the session storage: closing the application ends the session.
 */
@Service()
export class MobileLoginService extends BaseAuthService<unknown> {
  private readonly router = inject(Router);
  private readonly translateService = inject(TranslateService);
  private readonly formatService = inject(MobileFormatService);

  login(email: string, password: string): Observable<HttpResponse<ConfigurationWithLoginGT>> {
    const loginRequest = { email, password, timezoneOffset: new Date().getTimezoneOffset() };
    const headers = new HttpHeaders({ 'Content-Type': 'application/json', Accept: 'application/json' });
    return this.httpClient
      .post<ConfigurationWithLoginGT>('/api/login', loginRequest, {
        headers,
        observe: 'response',
        context: new HttpContext().set(SKIP_AUTH_HEADERS, true)
      })
      .pipe(catchError(this.handleError.bind(this)));
  }

  /**
   * Stores what the dashboard needs from a successful login and switches the texts to the language of the user.
   *
   * @param token The JWT from the response header
   * @param configuration The configuration of the response body
   */
  afterSuccessfulLogin(token: string, configuration: ConfigurationWithLoginGT): void {
    const claims = this.parseToken(token);
    const formatted = (1000.45).toLocaleString(claims.localeStr);
    sessionStorage.setItem(GlobalSessionNames.JWT, token);
    sessionStorage.setItem(GlobalSessionNames.ID_TENANT, claims.idTenant);
    sessionStorage.setItem(GlobalSessionNames.ID_USER, claims.idUser);
    sessionStorage.setItem(GlobalSessionNames.LOCALE, claims.localeStr);
    sessionStorage.setItem(GlobalSessionNames.LANGUAGE, claims.localeStr.slice(0, 2));
    sessionStorage.setItem(
      GlobalSessionNames.DECIMAL_SEPARATOR,
      formatted.substring(formatted.length - 3, formatted.length - 2)
    );
    sessionStorage.setItem(GlobalSessionNames.THOUSANDS_SEPARATOR, formatted.substring(1, 2));
    sessionStorage.setItem(GlobalSessionNames.USE_FEATURES, JSON.stringify(configuration.useFeatures));
    sessionStorage.setItem(GlobalSessionNames.CURRENCY_PRECISION, JSON.stringify(configuration.currencyPrecision));
    this.formatService.reset();
    this.translateService.use(claims.localeStr.slice(0, 2)).subscribe();
  }

  /** Whether the instance offers the dashboard to this user. */
  useDashboard(): boolean {
    const features: string[] = JSON.parse(sessionStorage.getItem(GlobalSessionNames.USE_FEATURES) || '[]');
    return features.includes('DASHBOARD');
  }

  isSignedIn(): boolean {
    return sessionStorage.getItem(GlobalSessionNames.JWT) !== null;
  }

  /** Ends the session and returns to the sign-in page, keeping the chosen language for the next login. */
  logout(): void {
    const language = sessionStorage.getItem(GlobalSessionNames.LANGUAGE);
    sessionStorage.clear();
    if (language) {
      sessionStorage.setItem(GlobalSessionNames.LANGUAGE, language);
    }
    this.formatService.reset();
    this.router.navigate(['/login']);
  }

  /** The server ended the session, for example because the token expired. */
  protected override toLogout(): void {
    this.logout();
  }

  private parseToken(token: string): TokenClaims {
    const base64 = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    return JSON.parse(window.atob(base64));
  }
}
