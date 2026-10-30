import { Observable } from 'rxjs';
import { inject, Service } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { GlobalparameterService } from '../../services/globalparameter.service';
import { User } from '../../entities/user';
import { Router } from '@angular/router';
import { ChangePasswordDTO } from '../model/change.password.dto';
import { GlobalSessionNames } from '../../global.session.names';
import { catchError } from 'rxjs/operators';
import { HttpContext, HttpHeaders, HttpResponse } from '@angular/common/http';
import { SKIP_AUTH_HEADERS } from './auth.http.interceptor';
import { BaseAuthService } from './base.auth.service';
import { UserOwnProjection } from '../../entities/projection/user.own.projection';
import { SuccessfullyChanged } from '../model/successfully.changed';
import { AppHelper } from '../../helper/app.helper';
import { Optimus } from '@openng/optimus-ui/config';
import { BaseSettings } from '../../base.settings';
import { AfterLoginHandler } from './after-login.handler';
import { ConfigurationWithLogin } from '../model';

@Service()
export class LoginService extends BaseAuthService<User> {
  private router = inject(Router);
  translateService = inject(TranslateService);
  private gps = inject(GlobalparameterService);
  private optimusConfig = inject(Optimus);
  private afterLoginHandler = inject(AfterLoginHandler, { optional: true });

  public static setGlobalLang(translateService: TranslateService, optimusConfig: Optimus): void {
    const language = sessionStorage.getItem(GlobalSessionNames.LANGUAGE);
    if (language && translateService.getLangs().includes(language)) {
      translateService.use(language).subscribe((params) => console.log('loaded'));
    } else {
      translateService.use(AppHelper.getNonUserDefinedLanguage(translateService.getBrowserLang()));
    }
    translateService.get('optimus').subscribe((res) => optimusConfig.setTranslation(res));
  }

  login(email: string, password: string, note?: string): Observable<HttpResponse<ConfigurationWithLogin>> {
    const date = new Date();
    const loginRequest = { email, password, timezoneOffset: date.getTimezoneOffset(), note };
    const headers = new HttpHeaders({ 'Content-Type': 'application/json', Accept: 'application/json' });

    return this.httpClient
      .post<ConfigurationWithLogin>('/api/login', loginRequest, {
        headers,
        observe: 'response',
        context: new HttpContext().set(SKIP_AUTH_HEADERS, true)
      })
      .pipe(catchError(this.handleError.bind(this)));
  }

  afterSuccessfulLogin(token: string, configurationWithLogin: ConfigurationWithLogin): boolean {
    this.gps.clearValues();
    const number = 1000.45;

    const responseClaim = this.parseToken(token);
    sessionStorage.setItem(
      GlobalSessionNames.UI_SHOW_MY_PROPERTY,
      JSON.stringify(configurationWithLogin.uiShowMyProperty)
    );
    sessionStorage.setItem(GlobalSessionNames.ID_TENANT, responseClaim.idTenant);
    sessionStorage.setItem(GlobalSessionNames.ID_USER, responseClaim.idUser);
    sessionStorage.setItem(GlobalSessionNames.LOCALE, responseClaim.localeStr);
    const numberFormated = number.toLocaleString(responseClaim.localeStr);
    sessionStorage.setItem(
      GlobalSessionNames.DECIMAL_SEPARATOR,
      numberFormated.substring(numberFormated.length - 3, numberFormated.length - 2)
    );
    sessionStorage.setItem(GlobalSessionNames.THOUSANDS_SEPARATOR, numberFormated.substring(1, 2));
    sessionStorage.setItem(GlobalSessionNames.ROLES, responseClaim.roles);
    sessionStorage.setItem(GlobalSessionNames.LANGUAGE, responseClaim.localeStr.slice(0, 2));
    sessionStorage.setItem(GlobalSessionNames.JWT, token);
    sessionStorage.setItem(GlobalSessionNames.USE_FEATURES, JSON.stringify(configurationWithLogin.useFeatures));
    sessionStorage.setItem(
      GlobalSessionNames.GT_NET_LOG_ENABLED,
      JSON.stringify(configurationWithLogin.gtNetLogEnabled)
    );
    sessionStorage.setItem(
      GlobalSessionNames.GT_NET_HAS_HISTORICAL_PEER,
      JSON.stringify(configurationWithLogin.gtNetHasHistoricalExchangePeer)
    );
    sessionStorage.setItem(
      GlobalSessionNames.GT_NET_HAS_LASTPRICE_PEER,
      JSON.stringify(configurationWithLogin.gtNetHasLastpriceExchangePeer)
    );
    sessionStorage.setItem(
      GlobalSessionNames.STANDARD_CURRENCY_PRECISIONS_AND_LIMITS,
      JSON.stringify(configurationWithLogin.standardPrecision)
    );
    sessionStorage.setItem(GlobalSessionNames.FIELD_SIZE, JSON.stringify(configurationWithLogin.fieldSize));
    sessionStorage.setItem(GlobalSessionNames.STANDARD_TIMEZONE, configurationWithLogin.standardTimeZone);
    const entityNameWithKeyNameMap = configurationWithLogin.entityNameWithKeyNameList.reduce(
      (ac, eNK) => ({ ...ac, [eNK.entityName]: eNK.keyName }),
      {}
    );
    sessionStorage.setItem(GlobalSessionNames.ENTITY_KEY_MAPPING, JSON.stringify(entityNameWithKeyNameMap));
    sessionStorage.setItem(GlobalSessionNames.MOST_PRIVILEGED_ROLE, configurationWithLogin.mostPrivilegedRole);
    sessionStorage.setItem(GlobalSessionNames.TENANT_READ_ONLY, JSON.stringify(configurationWithLogin.tenantReadOnly));
    sessionStorage.setItem(GlobalSessionNames.UDF_CONFIG, JSON.stringify(configurationWithLogin.udfConfig));

    // Switch the user interface to the language of the user that just signed in. Until here the
    // application runs in the language chosen at bootstrap, where no user is known yet and
    // AppComponent could only fall back to the browser language. Without this call the user's
    // configured language would take effect at the next full page load at the earliest, and never
    // at all when the browser is set to another language.
    LoginService.setGlobalLang(this.translateService, this.optimusConfig);

    // Call application-specific handler if provided
    if (this.afterLoginHandler) {
      this.afterLoginHandler.handleAfterLogin(configurationWithLogin);
    }

    return configurationWithLogin.passwordRegexOk;
  }

  logout(): void {
    sessionStorage.clear();
    this.gps.clearValues();
  }

  logoutWithLoginView() {
    this.logout();
    this.router.navigate(['/' + BaseSettings.LOGIN_KEY]);
  }

  isSignedIn(): boolean {
    return sessionStorage.getItem(GlobalSessionNames.JWT) !== null;
  }

  parseToken(token: string): any {
    const base64Url = token.split('.')[1];
    const base64 = base64Url.replace('-', '+').replace('_', '/');
    return JSON.parse(window.atob(base64));
  }

  update(user: User): Observable<User> {
    if (user.idUser == null) {
      return this.httpClient
        .post<User>(`${BaseSettings.API_ENDPOINT}${BaseSettings.USER_KEY}`, user, {
          ...this.getHeaders(),
          context: new HttpContext().set(SKIP_AUTH_HEADERS, true)
        })
        .pipe(catchError(this.handleError.bind(this)));
    }
    return this.updateEntity(user, user.idUser, BaseSettings.USER_KEY);
  }

  getOwnUser(): Observable<UserOwnProjection> {
    return this.httpClient
      .get<UserOwnProjection>(`${BaseSettings.API_ENDPOINT}${BaseSettings.USER_KEY}/own`, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }

  updateNicknameLocale(userOwnProjection: UserOwnProjection): Observable<SuccessfullyChanged> {
    return this.httpClient
      .put<SuccessfullyChanged>(
        `${BaseSettings.API_ENDPOINT}${BaseSettings.USER_KEY}/nicknamelocale`,
        userOwnProjection,
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  updatePassword(changePasswordDTO: ChangePasswordDTO): Observable<SuccessfullyChanged> {
    return this.httpClient
      .put<SuccessfullyChanged>(
        `${BaseSettings.API_ENDPOINT}${BaseSettings.USER_KEY}/password`,
        changePasswordDTO,
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  getTokenVerified(token: string): Observable<string> {
    const options = {
      ...this.getHeaders(),
      responseType: 'text' as const,
      context: new HttpContext().set(SKIP_AUTH_HEADERS, true)
    };
    return this.httpClient
      .get(`${BaseSettings.API_ENDPOINT}${BaseSettings.USER_KEY}/tokenverify/${token}`, options)
      .pipe(catchError(this.handleError.bind(this)));
  }
}
