import {Observable} from 'rxjs';
import {Injectable} from '@angular/core';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../service/globalparameter.service';
import {User} from '../../../entities/user';
import {AppSettings} from '../../app.settings';
import {MessageToastService} from '../../message/message.toast.service';
import {Router} from '@angular/router';
import {ChangePasswordDTO} from '../../../entities/backend/change.password.dto';
import {GlobalSessionNames} from '../../global.session.names';
import {catchError} from 'rxjs/operators';
import {HttpClient, HttpHeaders} from '@angular/common/http';
import {BaseAuthService} from './base.auth.service';
import * as moment from 'moment';
import {UserOwnProjection} from '../../../entities/projection/user.own.projection';
import {SuccessfullyChanged} from '../../../entities/backend/successfully.changed';
import {ConfigurationWithLogin} from '../component/login.component';
import {PrimeNGConfig} from 'primeng/api';


@Injectable()
export class LoginService extends BaseAuthService<User> {

  constructor(private router: Router,
              public translateService: TranslateService,
              private gps: GlobalparameterService,
              httpClient: HttpClient, messageToastService: MessageToastService) {
    super(httpClient, messageToastService);
  }

  public static setGlobalLang(translateService: TranslateService, primeNGConfig: PrimeNGConfig): void {
    if (translateService.getLangs().find(lang => lang === sessionStorage.getItem(GlobalSessionNames.LANGUAGE))) {
      translateService.use(sessionStorage.getItem(GlobalSessionNames.LANGUAGE)).subscribe(params => console.log('loaded'));
    } else {
      translateService.use(translateService.getBrowserLang());
    }
    translateService.get('primeng').subscribe(res => primeNGConfig.setTranslation(res));
  }

  login(email: string, password: string, note?: string): Observable<Response> {
    const date = new Date();
    const loginRequest = {email, password, timezoneOffset: date.getTimezoneOffset(), note};
    const headers = new HttpHeaders({'Content-Type': 'application/json', Accept: 'application/json'});

    return this.httpClient.post('/api/login', loginRequest, {headers, observe: 'response'})
      .pipe(catchError(this.handleError.bind(this)));
  }


  aftersuccessfully(token: string, configurationWithLogin: ConfigurationWithLogin): void {
    this.gps.clearValues();
    const number = 1000.45;

    const responseClaim = this.parseToken(token);
    sessionStorage.setItem(GlobalSessionNames.UI_SHOW_MY_PROPERTY, JSON.stringify(configurationWithLogin.uiShowMyProperty));
    sessionStorage.setItem(GlobalSessionNames.ID_TENANT, responseClaim.idTenant);
    sessionStorage.setItem(GlobalSessionNames.ID_USER, responseClaim.idUser);
    sessionStorage.setItem(GlobalSessionNames.LOCALE, responseClaim.localeStr);
    const numberFormated = number.toLocaleString(responseClaim.localeStr);
    sessionStorage.setItem(GlobalSessionNames.DECIMAL_SEPARATOR, numberFormated.substring(numberFormated.length - 3,
      numberFormated.length - 2));
    sessionStorage.setItem(GlobalSessionNames.THOUSANDS_SEPARATOR, numberFormated.substring(1, 2));
    sessionStorage.setItem(GlobalSessionNames.ROLES, responseClaim.roles);
    sessionStorage.setItem(GlobalSessionNames.LANGUAGE, responseClaim.localeStr.slice(0, 2));
    sessionStorage.setItem(GlobalSessionNames.JWT, token);
    sessionStorage.setItem(GlobalSessionNames.REPORT_UNTIL_DATE, moment().format(AppSettings.FORMAT_DATE_SHORT_NATIVE));

    sessionStorage.setItem(GlobalSessionNames.USE_WEBSOCKET, JSON.stringify(configurationWithLogin.useWebsocket));
    sessionStorage.setItem(GlobalSessionNames.USE_ALGO, JSON.stringify(configurationWithLogin.useAlgo));
    sessionStorage.setItem(GlobalSessionNames.CRYPTOS, JSON.stringify(configurationWithLogin.cryptocurrencies));

    sessionStorage.setItem(GlobalSessionNames.STANDARD_PRECISION, JSON.stringify(configurationWithLogin.standardPrecision));
    AppSettings.resetInterFractionLimit();

    sessionStorage.setItem(GlobalSessionNames.CURRENCY_PRECISION, JSON.stringify(configurationWithLogin.currencyPrecision));
    const entityNameWithKeyNameMap = configurationWithLogin.entityNameWithKeyNameList.reduce(
      (ac, eNK) => ({...ac, [eNK.entityName]: eNK.keyName}), {});
    sessionStorage.setItem(GlobalSessionNames.ENTITY_KEY_MAPPING, JSON.stringify(entityNameWithKeyNameMap));

  }

  logout(): void {
    sessionStorage.clear();
    this.gps.clearValues();
  }

  logoutWithLoginView() {
    this.logout();
    this.router.navigate(['/' + AppSettings.LOGIN_KEY]);

  }

  isSignedIn(): boolean {
    return sessionStorage.getItem(GlobalSessionNames.JWT) !== null;
  }

  parseToken(token): any {
    const base64Url = token.split('.')[1];
    const base64 = base64Url.replace('-', '+').replace('_', '/');
    return JSON.parse(window.atob(base64));
  }

  update(user: User): Observable<User> {
    return this.updateEntity(user, user.idUser, AppSettings.USER_KEY);
  }

  getOwnUser(): Observable<UserOwnProjection> {
    return <Observable<UserOwnProjection>>this.httpClient.get(`${AppSettings.API_ENDPOINT}${AppSettings.USER_KEY}/own`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  updateNicknameLocale(userOwnProjection: UserOwnProjection): Observable<SuccessfullyChanged> {
    return <Observable<SuccessfullyChanged>>this.httpClient.put(`${AppSettings.API_ENDPOINT}${AppSettings.USER_KEY}/nicknamelocale`,
      userOwnProjection, this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  updatePassword(changePasswordDTO: ChangePasswordDTO): Observable<SuccessfullyChanged> {
    return <Observable<SuccessfullyChanged>>this.httpClient.put(`${AppSettings.API_ENDPOINT}${AppSettings.USER_KEY}/password`,
      changePasswordDTO, this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  getTokenVerified(token: string): Observable<string> {
    const options: any = this.getHeaders();
    options.responseType = 'text';
    return <Observable<string>>this.httpClient.get(`${AppSettings.API_ENDPOINT}${AppSettings.USER_KEY}/tokenverify/${token}`,
      options).pipe(catchError(this.handleError.bind(this)));
  }

}
