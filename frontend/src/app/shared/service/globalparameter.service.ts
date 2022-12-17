import {Injectable} from '@angular/core';
import {AppSettings} from '../app.settings';
import {MessageToastService} from '../message/message.toast.service';
import {Globalparameters} from '../../entities/globalparameters';
import {ValueKeyHtmlSelectOptions} from '../../dynamic-form/models/value.key.html.select.options';
import {Observable, of} from 'rxjs';
import {HttpClient, HttpParams} from '@angular/common/http';
import {GlobalSessionNames} from '../global.session.names';
import {catchError, tap} from 'rxjs/operators';
import {Auditable} from '../../entities/auditable';
import {BaseAuthService} from '../login/service/base.auth.service';
import {TenantLimit, TenantLimitTypes} from '../../entities/backend/tenant.limit';
import * as moment from 'moment';
import {CurrencyMaskConfig, CurrencyMaskInputMode} from 'ngx-currency';
import {ServiceEntityUpdate} from '../edit/service.entity.update';
import {FieldDescriptorInputAndShow} from '../dynamicfield/field.descriptor.input.and.show';
import NumberFormat = Intl.NumberFormat;


@Injectable()
export class GlobalparameterService extends BaseAuthService<Globalparameters> implements ServiceEntityUpdate<Globalparameters> {

  // Cached values
  private _numberFormat: NumberFormat;
  private _numberFormatRaw: NumberFormat;

  private thousandsSeparatorSymbol: string;
  private decimalSymbol: string;
  private timeDateFormat: string;
  private timeSecondDateFormat: string;
  private dateFormat: string;
  private dateFormatCalendar: string;
  private dateFormatCalendarTowNumber: string;
  private entityKeyMapping: { [entityName: string]: string };
  private currencyPrecisionMap: { [currency: string]: number };
  private dateFormatWithoutYear: string;
  private _useWebsocket: boolean = null;

  constructor(httpClient: HttpClient, messageToastService: MessageToastService) {
    super(httpClient, messageToastService);
  }

  public getAllGlobalparameters(): Observable<Globalparameters[]> {
    return <Observable<Globalparameters[]>>this.httpClient.get(`${AppSettings.API_ENDPOINT}`
      + `${AppSettings.GLOBALPARAMETERS_P_KEY}`, this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  public clearValues(): void {
    for (const key of Object.keys(this)) {
      if (typeof this[key] === 'string' || this[key] instanceof NumberFormat) {
        this[key] = null;
      }
    }
  }

  public getIdTenant(): number {
    const idTenant = sessionStorage.getItem(GlobalSessionNames.ID_TENANT);
    return idTenant ? +idTenant : null;
  }

  public getIdUser(): number {
    return +sessionStorage.getItem(GlobalSessionNames.ID_USER);
  }

  public getUserLang(): string {
    return sessionStorage.getItem(GlobalSessionNames.LANGUAGE);
  }

  public getLocale(): string {
    return sessionStorage.getItem(GlobalSessionNames.LOCALE) || 'de-CH';
  }

  public isUiShowMyProperty(): boolean {
    return sessionStorage.getItem(GlobalSessionNames.UI_SHOW_MY_PROPERTY) === 'true';
  }

  public hasRole(requiredRole: string) {
    const roles: string = sessionStorage.getItem(GlobalSessionNames.ROLES);
    return roles.split(',').indexOf(requiredRole) >= 0;
  }

  public isEntityCreatedByUser(entity: Auditable): boolean {
    return entity.createdBy === +sessionStorage.getItem(GlobalSessionNames.ID_USER);
  }

  public getTimeDateFormatForTable(): string {
    if (!this.timeDateFormat) {
      moment.locale(this.getLocale());
      const formatYear = moment.localeData().longDateFormat('L');
      const formatTime = moment.localeData().longDateFormat('LT');
      this.timeDateFormat = formatTime + ' ' + formatYear.replace(/YYYY/g, 'YY');
    }
    return this.timeDateFormat;
  }

  public getTimeSecondDateFormatForTable(): string {
    if (!this.timeSecondDateFormat) {
      moment.locale(this.getLocale());
      const formatYear = moment.localeData().longDateFormat('L');
      const formatTime = moment.localeData().longDateFormat('LTS');
      this.timeSecondDateFormat = formatTime + ' ' + formatYear.replace(/YYYY/g, 'YY');
    }
    return this.timeSecondDateFormat;
  }


  public getNumberCurrencyMask(): CurrencyMaskConfig {
    return <CurrencyMaskConfig>{
      align: 'right',
      allowNegative: true,
      allowZero: true,
      decimal: this.getDecimalSymbol(),
      precision: AppSettings.FID_STANDARD_FRACTION_DIGITS,
      prefix: '',
      suffix: '',
      thousands: this.getThousandsSeparatorSymbol(),
      nullable: true,
      min: null,
      max: null,
      inputMode: CurrencyMaskInputMode.NATURAL
    };
  }

  public getDateFormatWithoutYear(): string {
    if (!this.dateFormatWithoutYear) {
      this.dateFormatWithoutYear = this.getDateFormatYearCalendar('YYYY');
      if (this.dateFormatWithoutYear.match(/.YYYY/g)) {
        this.dateFormatWithoutYear = this.dateFormatWithoutYear.replace(/.YYYY/, '');
      }
      if (this.dateFormatWithoutYear.match(/YYYY./g)) {
        this.dateFormatWithoutYear = this.dateFormatWithoutYear.replace(/YYYY./, '');
      }
    }
    return this.dateFormatWithoutYear;
  }

  /**
   * Short Date format depending on locale. Year from 00-99, like MM-DD-YY or MM.DD.YY.
   */
  public getDateFormat(): string {
    return this.dateFormat || (this.dateFormat = this.getDateFormatYearCalendar('YY'));
  }

  public getDateFormatForCalendar(): string {
    return this.dateFormatCalendar || (this.dateFormatCalendar = this.getDateFormatYearCalendar('Y'));
  }

  public getCalendarTwoNumberDateFormat(): string {
    if (!this.dateFormatCalendarTowNumber) {
      moment.locale(this.getLocale());
      const formatYear = moment.localeData().longDateFormat('L');
      this.dateFormatCalendarTowNumber = formatYear.replace(/YYYY/g, 'Y');
    }
    return this.dateFormatCalendarTowNumber;
  }

  /**
   * Gets the key property name by the entity name from session storge
   *
   * @param entityName Name of entity
   */
  public getKeyNameByEntityName(entityName: string): string {
    if (!this.entityKeyMapping) {
      this.entityKeyMapping = JSON.parse(sessionStorage.getItem(GlobalSessionNames.ENTITY_KEY_MAPPING));
    }
    return this.entityKeyMapping[entityName];
  }

  public getCurrencyPrecision(currency: string): number {
    if (!this.currencyPrecisionMap) {
      this.currencyPrecisionMap = JSON.parse(sessionStorage.getItem(GlobalSessionNames.CURRENCY_PRECISION));
    }
    return this.currencyPrecisionMap[currency] ? this.currencyPrecisionMap[currency] : AppSettings.FID_STANDARD_FRACTION_DIGITS;
  }

  public getThousandsSeparatorSymbol(): string {
    if (!this.thousandsSeparatorSymbol) {
      this.thousandsSeparatorSymbol = sessionStorage.getItem(GlobalSessionNames.THOUSANDS_SEPARATOR);
    }
    return this.thousandsSeparatorSymbol;
  }

  public getDecimalSymbol(): string {
    if (!this.decimalSymbol) {
      this.decimalSymbol = sessionStorage.getItem(GlobalSessionNames.DECIMAL_SEPARATOR);
    }
    return this.decimalSymbol;
  }

  public getNumberFormatRaw(): NumberFormat {
    if (!this._numberFormatRaw) {
      this._numberFormatRaw = new Intl.NumberFormat(this.getLocale(), {maximumFractionDigits: 10});
    }
    return this._numberFormatRaw;
  }

  public useWebsocket(): boolean {
    if (this._useWebsocket === null) {
      this._useWebsocket = sessionStorage.getItem(GlobalSessionNames.USE_WEBSOCKET) === 'true';
    }
    return this._useWebsocket;
  }

  public getNumberFormat(): NumberFormat {
    if (!this._numberFormat) {
      this._numberFormat = new Intl.NumberFormat(this.getLocale(), {
        minimumFractionDigits: AppSettings.FID_STANDARD_FRACTION_DIGITS,
        maximumFractionDigits: AppSettings.FID_MAX_FRACTION_DIGITS
      });
    }
    return this._numberFormat;
  }

  public getCurrencies(): Observable<ValueKeyHtmlSelectOptions[]> {
    return <Observable<ValueKeyHtmlSelectOptions[]>>this.httpClient.get(`${AppSettings.API_ENDPOINT}`
      + `${AppSettings.GLOBALPARAMETERS_P_KEY}/${AppSettings.CURRENCIES_P_KEY}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  public getCountriesForSelectBox(): Observable<ValueKeyHtmlSelectOptions[]> {
    return <Observable<ValueKeyHtmlSelectOptions[]>>this.httpClient.get(`${AppSettings.API_ENDPOINT}`
      + `${AppSettings.GLOBALPARAMETERS_P_KEY}/countries`, this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  public getTimezones(): Observable<ValueKeyHtmlSelectOptions[]> {
    return <Observable<ValueKeyHtmlSelectOptions[]>>this.httpClient.get(`${AppSettings.API_ENDPOINT}`
      + `${AppSettings.GLOBALPARAMETERS_P_KEY}/${AppSettings.TIMESZONES_P_KEY}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  public getMaxTenantLimitsByMsgKey(msgKeys: TenantLimitTypes[]): Observable<TenantLimit[]> {
    let httpParams = new HttpParams();
    // const msgKeyAsString: string[] = [];
    httpParams = httpParams.append('msgKeys', msgKeys.join(','));
    return <Observable<TenantLimit[]>>this.httpClient.get(`${AppSettings.API_ENDPOINT}`
      + `${AppSettings.GLOBALPARAMETERS_P_KEY}/tenantlimits`,
      {headers: this.prepareHeaders(), params: httpParams}).pipe(catchError(this.handleError.bind(this)));
  }

  public getUserFormDefinitions(): Observable<FieldDescriptorInputAndShow[]> {
    return <Observable<FieldDescriptorInputAndShow[]>>this.httpClient.get(`${AppSettings.API_ENDPOINT}`
      + `${AppSettings.GLOBALPARAMETERS_P_KEY}/userformdefinition`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  public getSupportedLocales(): Observable<ValueKeyHtmlSelectOptions[]> {
    return <Observable<ValueKeyHtmlSelectOptions[]>>this.httpClient.get(`${AppSettings.API_ENDPOINT}`
      + `${AppSettings.GLOBALPARAMETERS_P_KEY}/${AppSettings.LOCALES_P_KEY}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  public getIntraUpdateTimeout(): Observable<number> {
    return this.getGlobalparameterNumber(GlobalSessionNames.UPDATE_TIME, 'updatetimeout');
  }

  public getStartFeedDateAsTime(): Observable<number> {
    return this.getGlobalparameterNumber(GlobalSessionNames.START_FEED_DATE, 'startfeeddate');
  }

  update(globalparameters: Globalparameters): Observable<Globalparameters> {
    return <Observable<Globalparameters>>this.httpClient.put(`${AppSettings.API_ENDPOINT}`
      + `${AppSettings.GLOBALPARAMETERS_P_KEY}`, globalparameters,
      {headers: this.prepareHeaders()}).pipe(catchError(this.handleError.bind(this)));
  }

  private getDateFormatYearCalendar(yearReplace: string): string {
    moment.locale(this.getLocale());
    const formatYear = moment.localeData().longDateFormat('L');
    return formatYear.replace(/YYYY/g, yearReplace);
  }

  private getGlobalparameterNumber(globalSessionNames: GlobalSessionNames, uriPart: string): Observable<number> {
    if (sessionStorage.getItem(globalSessionNames)) {
      return of(+sessionStorage.getItem(globalSessionNames));
    } else {
      return <Observable<number>>this.httpClient.get(`${AppSettings.API_ENDPOINT}`
        + `${AppSettings.GLOBALPARAMETERS_P_KEY}/${uriPart}`,
        this.getHeaders()).pipe(tap(value => sessionStorage.setItem(globalSessionNames, '' + value)),
        catchError(this.handleError.bind(this)));
    }
  }
}
