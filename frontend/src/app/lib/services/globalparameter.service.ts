import {Injectable} from '@angular/core';
import {MessageToastService} from '../message/message.toast.service';
import {Globalparameters} from '../entities/globalparameters';
import {ValueKeyHtmlSelectOptions} from '../dynamic-form/models/value.key.html.select.options';
import {Observable} from 'rxjs';
import {HttpClient, HttpParams} from '@angular/common/http';
import {GlobalSessionNames} from '../global.session.names';
import {catchError, shareReplay} from 'rxjs/operators';
import {ServiceEntityUpdate} from '../edit/service.entity.update';
import {ClassDescriptorInputAndShow, FieldDescriptorInputAndShow} from '../dynamicfield/field.descriptor.input.and.show';
import {BaseSettings} from '../base.settings';
import {AppHelper} from '../helper/app.helper';
import {Auditable} from '../entities/auditable';
import {FeatureType} from '../login/model/configuration-with-login';
import {BaseAuthService} from '../login/service/base.auth.service';
import NumberFormat = Intl.NumberFormat;
import moment from 'moment';
import 'moment/locale/de-ch.js';
import 'moment/locale/de.js';
import 'moment/locale/en-gb.js';
import 'moment/locale/en-au.js';
import 'moment/locale/en-nz.js';

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

  private fieldSizeMap: { [fieldNameOrKey: string]: number };
  private dateFormatWithoutYear: string;
  private featureCache = new Map<FeatureType, boolean>();
  private currencyPrecisionMap: { [currency: string]: number };
  private numberFormatPrecisionCache = new Map<number, NumberFormat>();
  private formDefinitionCache: { [entityAndDialog: string]: Observable<ClassDescriptorInputAndShow> } = {};


  constructor(httpClient: HttpClient, messageToastService: MessageToastService) {
    super(httpClient, messageToastService);
  }

  public getAllGlobalparameters(): Observable<Globalparameters[]> {
    return <Observable<Globalparameters[]>>this.httpClient.get(`${BaseSettings.API_ENDPOINT}`
      + `${BaseSettings.GLOBALPARAMETERS_P_KEY}`, this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  public clearValues(): void {
    for (const key of Object.keys(this)) {
      if (typeof this[key] === 'string' || this[key] instanceof NumberFormat) {
        this[key] = null;
      }
    }
    this.currencyPrecisionMap = null;
    this.numberFormatPrecisionCache.clear();
  }

  /**
   * Returns the number of decimal places for amounts in the given currency (e.g. BTC=8, JPY=0). The map is provided
   * by the backend at login and cached from sessionStorage. Currencies not contained in the map fall back to the
   * standard fraction digits, mirroring the backend's GlobalparametersService.getPrecisionForCurrency().
   *
   * @param currency ISO 4217 or crypto currency code
   * @returns Number of decimal places for amounts in this currency
   */
  public getCurrencyPrecision(currency: string): number {
    if (!this.currencyPrecisionMap) {
      this.currencyPrecisionMap = JSON.parse(sessionStorage.getItem(GlobalSessionNames.CURRENCY_PRECISION)) || {};
    }
    return this.currencyPrecisionMap[currency] != null ? this.currencyPrecisionMap[currency]
      : BaseSettings.FID_STANDARD_FRACTION_DIGITS;
  }

  /**
   * Returns a locale-aware number format with a fixed number of fraction digits (minimum = maximum = precision).
   * Instances are cached per precision. Used for displaying currency amounts with their currency-specific precision,
   * including precision 0 (e.g. JPY).
   *
   * @param precision Fixed number of fraction digits
   * @returns Cached Intl.NumberFormat for the user's locale and the given precision
   */
  public getNumberFormatForPrecision(precision: number): NumberFormat {
    let numberFormat = this.numberFormatPrecisionCache.get(precision);
    if (!numberFormat) {
      numberFormat = new Intl.NumberFormat(this.getLocale(), {
        minimumFractionDigits: precision,
        maximumFractionDigits: precision
      });
      this.numberFormatPrecisionCache.set(precision, numberFormat);
    }
    return numberFormat;
  }

  public getMaxFractionDigits(): number {
    return BaseSettings.FID_MAX_FRACTION_DIGITS;
  }

  public getStandardFractionDigits(): number {
    return BaseSettings.FID_STANDARD_FRACTION_DIGITS;
  }

  public getIdTenant(): number {
    const idTenant = sessionStorage.getItem(GlobalSessionNames.ID_TENANT);
    return idTenant ? +idTenant : null;
  }

  public getIdUser(): number {
    return +sessionStorage.getItem(GlobalSessionNames.ID_USER);
  }

  public getUserLang(): string {
    return sessionStorage.getItem(GlobalSessionNames.LANGUAGE) || 'en';
  }

  private getTimeLocale(): string {
    if (['de', 'en-gb', 'en-au', 'en-nz'].indexOf(this.getLocale().toLowerCase()) != -1) {
      return this.getLocale().toLowerCase();
    }
    return this.getLocale().startsWith('de') ? 'de' : 'en';
  }

  public getLocale(): string {
    return sessionStorage.getItem(GlobalSessionNames.LOCALE) || 'de-CH';
  }

  public getStandardTimeZone(): string {
    return sessionStorage.getItem(GlobalSessionNames.STANDARD_TIMEZONE);
  }

  public isUiShowMyProperty(): boolean {
    return sessionStorage.getItem(GlobalSessionNames.UI_SHOW_MY_PROPERTY) === 'true';
  }

  public hasRole(requiredRole: string) {
    const roles: string = sessionStorage.getItem(GlobalSessionNames.ROLES);
    return roles.split(',').indexOf(requiredRole) >= 0;
  }

  public getMostPrivilegedRole(): string {
    return sessionStorage.getItem(GlobalSessionNames.MOST_PRIVILEGED_ROLE);
  }

  /**
   * Whether the user has read-only access to the tenant they currently operate in. True for a managed client account or
   * an advisor switched into a READ-level tenant. Used to hide create/update/delete actions in the UI; the backend
   * write-blocking filter enforces it regardless.
   */
  public isReadOnlyUser(): boolean {
    return sessionStorage.getItem(GlobalSessionNames.TENANT_READ_ONLY) === 'true';
  }

  public isEntityCreatedByUser(entity: Auditable): boolean {
    return entity.createdBy === +sessionStorage.getItem(GlobalSessionNames.ID_USER);
  }

  public getTimeDateFormatForTable(): string {
    if (!this.timeDateFormat) {
      moment.locale(this.getTimeLocale());
      const formatYear = moment.localeData().longDateFormat('L');
      const formatTime = moment.localeData().longDateFormat('LT');
      this.timeDateFormat = formatTime + ' ' + formatYear.replace(/YYYY/g, 'YY');
    }
    return this.timeDateFormat;
  }

  public getTimeSecondDateFormatForTable(): string {
    if (!this.timeSecondDateFormat) {
      moment.locale(this.getTimeLocale());
      const formatYear = moment.localeData().longDateFormat('L');
      const formatTime = moment.localeData().longDateFormat('LTS');
      this.timeSecondDateFormat = formatTime + ' ' + formatYear.replace(/YYYY/g, 'YY');
    }
    return this.timeSecondDateFormat;
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
      moment.locale(this.getTimeLocale());
      const formatYear = moment.localeData().longDateFormat('L');
      this.dateFormatCalendarTowNumber = formatYear.replace(/YYYY/g, 'Y');
    }
    return this.dateFormatCalendarTowNumber;
  }

  /**
   * Gets the key property name by the entity name from session storage
   *
   * @param entityName Name of entity
   */
  public getKeyNameByEntityName(entityName: string): string {
    if (!this.entityKeyMapping) {
      this.entityKeyMapping = JSON.parse(sessionStorage.getItem(GlobalSessionNames.ENTITY_KEY_MAPPING));
    }
    return this.entityKeyMapping[entityName];
  }



  public getFieldSize(fieldNameOrKey: string): number {
    if (!this.fieldSizeMap) {
      this.fieldSizeMap = JSON.parse(sessionStorage.getItem(GlobalSessionNames.FIELD_SIZE));
    }
    return this.fieldSizeMap[fieldNameOrKey];
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
    return this.prepareUseFeatures(FeatureType.WEBSOCKET);
  }

  /**
   * Opens an external help webpage in a new window.
   * The base URL is read from local storage (set during application initialization).
   * If no base URL is configured, the method will do nothing.
   *
   * @param language The language code for the help documentation (e.g., 'en', 'de')
   * @param helpIds The specific help page identifier
   *
   * TODO If the user manual is split up, it can be stored in the session storage.
   */
  public toExternalHelpWebpage(language: string, helpIds: string) {
    const helpUrlBase = localStorage.getItem(GlobalSessionNames.EXTERNAL_HELP_URL);
    if (helpUrlBase) {
      AppHelper.toExternalWebpage(helpUrlBase + '/' + language + '/' + helpIds, 'help');
    }
  }

  public useAlert(): boolean {
    return this.prepareUseFeatures(FeatureType.ALERT);
  }

  public useGtnet(): boolean {
    return this.prepareUseFeatures(FeatureType.GTNET);
  }

  /**
   * Whether the manage-client feature (advisor manages additional tenants with read-only client logins) is enabled
   * for this deployment (g.use.manageclient).
   */
  public useManageClient(): boolean {
    return this.prepareUseFeatures(FeatureType.MANAGECLIENT);
  }

  public isGtNetLogEnabled(): boolean {
    return sessionStorage.getItem(GlobalSessionNames.GT_NET_LOG_ENABLED) === 'true';
  }

  public hasGtNetHistoricalExchangePeer(): boolean {
    return sessionStorage.getItem(GlobalSessionNames.GT_NET_HAS_HISTORICAL_PEER) === 'true';
  }

  public hasGtNetLastpriceExchangePeer(): boolean {
    return sessionStorage.getItem(GlobalSessionNames.GT_NET_HAS_LASTPRICE_PEER) === 'true';
  }

  private prepareUseFeatures(featureType: FeatureType): boolean {
    // Check if the feature state is already cached
    if (!this.featureCache.has(featureType)) {
      const features = JSON.parse(sessionStorage.getItem(GlobalSessionNames.USE_FEATURES));
      this.featureCache.set(featureType, features.indexOf(FeatureType[featureType]) >= 0);
    }
    return this.featureCache.get(featureType);
  }

  public getNumberFormat(): NumberFormat {
    if (!this._numberFormat) {
      this._numberFormat = new Intl.NumberFormat(this.getLocale(), {
        minimumFractionDigits: BaseSettings.FID_STANDARD_FRACTION_DIGITS,
        maximumFractionDigits: BaseSettings.FID_MAX_FRACTION_DIGITS
      });
    }
    return this._numberFormat;
  }

  public getPasswordRegexProperties(): Observable<PasswordRegexProperties> {
    return <Observable<PasswordRegexProperties>>this.httpClient.get(`${BaseSettings.API_ENDPOINT}`
      + `${BaseSettings.GLOBALPARAMETERS_P_KEY}/passwordrequirements`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  public getCountriesForSelectBox(): Observable<ValueKeyHtmlSelectOptions[]> {
    return <Observable<ValueKeyHtmlSelectOptions[]>>this.httpClient.get(`${BaseSettings.API_ENDPOINT}`
      + `${BaseSettings.GLOBALPARAMETERS_P_KEY}/countries`, this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  public getTimezones(): Observable<ValueKeyHtmlSelectOptions[]> {
    return <Observable<ValueKeyHtmlSelectOptions[]>>this.httpClient.get(`${BaseSettings.API_ENDPOINT}`
      + `${BaseSettings.GLOBALPARAMETERS_P_KEY}/${BaseSettings.TIMESZONES_P_KEY}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  public getUserFormDefinitions(): Observable<FieldDescriptorInputAndShow[]> {
    return <Observable<FieldDescriptorInputAndShow[]>>this.httpClient.get(`${BaseSettings.API_ENDPOINT}`
      + `${BaseSettings.GLOBALPARAMETERS_P_KEY}/userformdefinition`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  /**
   * Returns the dynamic form definition of an entity for the given dialog, derived on the backend from the entity's
   * @DynamicFormField + Bean Validation annotations. The result is memoised per entity/dialog because the definition
   * is static metadata, so an edit dialog opened repeatedly fetches it only once.
   *
   * @param entityName simple name of the registered entity (e.g. 'Cashaccount')
   * @param dialog dialog id whose fields should be returned (default 1)
   * @returns the class descriptor with the ordered field descriptors for the dialog
   */
  public getEntityFormDefinition(entityName: string, dialog = 1): Observable<ClassDescriptorInputAndShow> {
    const cacheKey = `${entityName}:${dialog}`;
    if (!this.formDefinitionCache[cacheKey]) {
      this.formDefinitionCache[cacheKey] = (<Observable<ClassDescriptorInputAndShow>>this.httpClient.get(
        `${BaseSettings.API_ENDPOINT}${BaseSettings.GLOBALPARAMETERS_P_KEY}/formdefinition/${entityName}`,
        {headers: this.getHeaders().headers, params: new HttpParams().set('dialog', dialog)}))
        .pipe(catchError(this.handleError.bind(this)), shareReplay(1));
    }
    return this.formDefinitionCache[cacheKey];
  }

  public getSupportedLocales(): Observable<ValueKeyHtmlSelectOptions[]> {
    return <Observable<ValueKeyHtmlSelectOptions[]>>this.httpClient.get(`${BaseSettings.API_ENDPOINT}`
      + `${BaseSettings.GLOBALPARAMETERS_P_KEY}/${BaseSettings.LOCALES_P_KEY}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  update(globalparameters: Globalparameters): Observable<Globalparameters> {
    return <Observable<Globalparameters>>this.httpClient.put(`${BaseSettings.API_ENDPOINT}`
      + `${BaseSettings.GLOBALPARAMETERS_P_KEY}`, globalparameters,
      {headers: this.prepareHeaders()}).pipe(catchError(this.handleError.bind(this)));
  }

  private getDateFormatYearCalendar(yearReplace: string): string {
    moment.locale(this.getTimeLocale());
    const formatYear = moment.localeData().longDateFormat('L');
    return formatYear.replace(/YYYY/g, yearReplace);
  }
}


export interface PasswordRegexProperties {
  regex: string;
  languageErrorMsgMap: { [language: string]: string };
  forceRegex: boolean;
}
