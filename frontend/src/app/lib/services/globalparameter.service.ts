import { Service } from '@angular/core';
import { Globalparameters } from '../entities/globalparameters';
import { ValueKeyHtmlSelectOptions } from '../dynamic-form/models/value.key.html.select.options';
import { Observable } from 'rxjs';
import { HttpContext, HttpParams } from '@angular/common/http';
import { SKIP_AUTH_HEADERS } from '../login/service/auth.http.interceptor';
import { GlobalSessionNames } from '../global.session.names';
import { catchError, shareReplay } from 'rxjs/operators';
import { ServiceEntityUpdate } from '../edit/service.entity.update';
import {
  ClassDescriptorInputAndShow,
  FieldDescriptorInputAndShow
} from '../dynamicfield/field.descriptor.input.and.show';
import { BaseSettings } from '../base.settings';
import { AppHelper } from '../helper/app.helper';
import { Auditable } from '../entities/auditable';
import { FeatureType } from '../login/model/configuration-with-login';
import { BaseAuthService } from '../login/service/base.auth.service';
import { LocaleFormat } from '../helper/locale.format';
import NumberFormat = Intl.NumberFormat;

@Service()
export class GlobalparameterService
  extends BaseAuthService<Globalparameters>
  implements ServiceEntityUpdate<Globalparameters>
{
  /** Number and date formats of the session; replaced on every login by {@link clearValues}. */
  private localeFormat = new LocaleFormat();
  private entityKeyMapping: { [entityName: string]: string } | null;

  private fieldSizeMap: { [fieldNameOrKey: string]: number } | null;
  private featureCache = new Map<FeatureType, boolean>();

  useDashboard(): boolean {
    const features: string[] = JSON.parse(sessionStorage.getItem(GlobalSessionNames.USE_FEATURES) || '[]');
    return features.includes('DASHBOARD');
  }
  private formDefinitionCache: { [entityAndDialog: string]: Observable<ClassDescriptorInputAndShow> } = {};

  public getAllGlobalparameters(): Observable<Globalparameters[]> {
    return this.httpClient
      .get<Globalparameters[]>(
        `${BaseSettings.API_ENDPOINT}` + `${BaseSettings.GLOBALPARAMETERS_P_KEY}`,
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  /**
   * Forgets every value this service read lazily from sessionStorage or derived from it, so that the next login, also
   * one in a tab that ran an earlier session, renders from the values of its own session. The members are named one
   * by one: a member that is still unset when this runs has no type at runtime, and the feature set, the entity keys
   * and the field sizes are objects. The feature set can differ between two logins, because GTNET, MCP and ACCESSTOKEN
   * follow global parameters that an administrator changes at runtime.
   */
  public clearValues(): void {
    this.localeFormat = new LocaleFormat();
    this.entityKeyMapping = null;
    this.fieldSizeMap = null;
    this.featureCache.clear();
    this.formDefinitionCache = {};
  }

  /** @see LocaleFormat.getCurrencyPrecision */
  public getCurrencyPrecision(currency: string): number {
    return this.localeFormat.getCurrencyPrecision(currency);
  }

  /** @see LocaleFormat.getNumberFormatForPrecision */
  public getNumberFormatForPrecision(precision: number): NumberFormat {
    return this.localeFormat.getNumberFormatForPrecision(precision);
  }

  public getMaxFractionDigits(): number {
    return BaseSettings.FID_MAX_FRACTION_DIGITS;
  }

  public getStandardFractionDigits(): number {
    return BaseSettings.FID_STANDARD_FRACTION_DIGITS;
  }

  public getIdTenant(): number | null {
    const idTenant = sessionStorage.getItem(GlobalSessionNames.ID_TENANT);
    return idTenant ? +idTenant : null;
  }

  /** Returns the active tenant id for actions that require an established tenant session. */
  public requireIdTenant(): number {
    const id = this.getIdTenant();
    if (id === null) {
      throw new Error('This action requires an active tenant session');
    }
    return id;
  }

  public getIdUser(): number {
    return Number(sessionStorage.getItem(GlobalSessionNames.ID_USER));
  }

  public getUserLang(): string {
    return sessionStorage.getItem(GlobalSessionNames.LANGUAGE) || 'en';
  }

  public getLocale(): string {
    return this.localeFormat.getLocale();
  }

  public getStandardTimeZone(): string | null {
    return sessionStorage.getItem(GlobalSessionNames.STANDARD_TIMEZONE);
  }

  public isUiShowMyProperty(): boolean {
    return sessionStorage.getItem(GlobalSessionNames.UI_SHOW_MY_PROPERTY) === 'true';
  }

  public hasRole(requiredRole: string) {
    const roles = sessionStorage.getItem(GlobalSessionNames.ROLES);
    return roles?.split(',').includes(requiredRole) ?? false;
  }

  public getMostPrivilegedRole(): string | null {
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
    return entity.createdBy === this.getIdUser();
  }

  public getTimeDateFormatForTable(): string {
    return this.localeFormat.getTimeDateFormatForTable();
  }

  public getTimeSecondDateFormatForTable(): string {
    return this.localeFormat.getTimeSecondDateFormatForTable();
  }

  public getDateFormatWithoutYear(): string {
    return this.localeFormat.getDateFormatWithoutYear();
  }

  /**
   * Short Date format depending on locale. Year from 00-99, like MM-DD-YY or MM.DD.YY.
   */
  public getDateFormat(): string {
    return this.localeFormat.getDateFormat();
  }

  public getDateFormatForCalendar(): string {
    return this.localeFormat.getDateFormatForCalendar();
  }

  public getCalendarTwoNumberDateFormat(): string {
    return this.localeFormat.getCalendarTwoNumberDateFormat();
  }

  /**
   * Gets the key property name by the entity name from session storage
   *
   * @param entityName Name of entity
   */
  public getKeyNameByEntityName(entityName: string): string {
    if (!this.entityKeyMapping) {
      this.entityKeyMapping = JSON.parse(sessionStorage.getItem(GlobalSessionNames.ENTITY_KEY_MAPPING) ?? 'null');
    }
    if (!this.entityKeyMapping) {
      throw new Error('Entity key mapping requires an initialized login session');
    }
    return this.entityKeyMapping[entityName];
  }

  public getFieldSize(fieldNameOrKey: string): number {
    if (!this.fieldSizeMap) {
      this.fieldSizeMap = JSON.parse(sessionStorage.getItem(GlobalSessionNames.FIELD_SIZE) ?? 'null');
    }
    if (!this.fieldSizeMap) {
      throw new Error('Field sizes require an initialized login session');
    }
    return this.fieldSizeMap[fieldNameOrKey];
  }

  public getThousandsSeparatorSymbol(): string {
    return this.localeFormat.getThousandsSeparatorSymbol();
  }

  public getDecimalSymbol(): string {
    return this.localeFormat.getDecimalSymbol();
  }

  public getNumberFormatRaw(): NumberFormat {
    return this.localeFormat.getNumberFormatRaw();
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
  public toExternalHelpWebpage(language: string, helpIds: string | null | undefined) {
    const helpUrlBase = localStorage.getItem(GlobalSessionNames.EXTERNAL_HELP_URL);
    if (helpUrlBase && helpIds) {
      AppHelper.toExternalWebpage(helpUrlBase + '/' + language + '/' + helpIds, 'help');
    }
  }

  public useAlert(): boolean {
    return this.prepareUseFeatures(FeatureType.ALERT);
  }

  /** Rule-based trading and its simulation, including the inputs of the simulation tax estimate. */
  public useAlgo(): boolean {
    return this.prepareUseFeatures(FeatureType.ALGO);
  }

  /** Personal access tokens for machine clients; reported only while they may be used. */
  public useAccessToken(): boolean {
    return this.prepareUseFeatures(FeatureType.ACCESSTOKEN);
  }

  /** MCP server for AI assistants, opened by the administrator (gt.mcp.use); it comes with personal access tokens. */
  public useMcp(): boolean {
    return this.prepareUseFeatures(FeatureType.MCP);
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
    let enabled = this.featureCache.get(featureType);
    if (enabled === undefined) {
      const features: string[] = JSON.parse(sessionStorage.getItem(GlobalSessionNames.USE_FEATURES) ?? '[]');
      enabled = features.includes(FeatureType[featureType]);
      this.featureCache.set(featureType, enabled);
    }
    return enabled;
  }

  public getNumberFormat(): NumberFormat {
    return this.localeFormat.getNumberFormat();
  }

  public getPasswordRegexProperties(): Observable<PasswordRegexProperties> {
    return this.httpClient
      .get<PasswordRegexProperties>(
        `${BaseSettings.API_ENDPOINT}` + `${BaseSettings.GLOBALPARAMETERS_P_KEY}/passwordrequirements`,
        {
          ...this.getHeaders(),
          context: new HttpContext().set(SKIP_AUTH_HEADERS, true)
        }
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  public getCountriesForSelectBox(): Observable<ValueKeyHtmlSelectOptions[]> {
    return this.httpClient
      .get<ValueKeyHtmlSelectOptions[]>(
        `${BaseSettings.API_ENDPOINT}` + `${BaseSettings.GLOBALPARAMETERS_P_KEY}/countries`,
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  public getTimezones(): Observable<ValueKeyHtmlSelectOptions[]> {
    return this.httpClient
      .get<ValueKeyHtmlSelectOptions[]>(
        `${BaseSettings.API_ENDPOINT}` + `${BaseSettings.GLOBALPARAMETERS_P_KEY}/${BaseSettings.TIMESZONES_P_KEY}`,
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  public getUserFormDefinitions(): Observable<FieldDescriptorInputAndShow[]> {
    return this.httpClient
      .get<FieldDescriptorInputAndShow[]>(
        `${BaseSettings.API_ENDPOINT}` + `${BaseSettings.GLOBALPARAMETERS_P_KEY}/userformdefinition`,
        {
          ...this.getHeaders(),
          context: new HttpContext().set(SKIP_AUTH_HEADERS, true)
        }
      )
      .pipe(catchError(this.handleError.bind(this)));
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
      this.formDefinitionCache[cacheKey] = (<Observable<ClassDescriptorInputAndShow>>(
        this.httpClient.get(
          `${BaseSettings.API_ENDPOINT}${BaseSettings.GLOBALPARAMETERS_P_KEY}/formdefinition/${entityName}`,
          { headers: this.getHeaders().headers, params: new HttpParams().set('dialog', dialog) }
        )
      )).pipe(catchError(this.handleError.bind(this)), shareReplay(1));
    }
    return this.formDefinitionCache[cacheKey];
  }

  public getSupportedLocales(): Observable<ValueKeyHtmlSelectOptions[]> {
    return this.httpClient
      .get<ValueKeyHtmlSelectOptions[]>(
        `${BaseSettings.API_ENDPOINT}` + `${BaseSettings.GLOBALPARAMETERS_P_KEY}/${BaseSettings.LOCALES_P_KEY}`,
        {
          ...this.getHeaders(),
          context: new HttpContext().set(SKIP_AUTH_HEADERS, true)
        }
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  update(globalparameters: Globalparameters): Observable<Globalparameters> {
    return this.httpClient
      .put<Globalparameters>(
        `${BaseSettings.API_ENDPOINT}` + `${BaseSettings.GLOBALPARAMETERS_P_KEY}`,
        globalparameters,
        {
          headers: this.prepareHeaders()
        }
      )
      .pipe(catchError(this.handleError.bind(this)));
  }
}

export interface PasswordRegexProperties {
  regex: string;
  languageErrorMsgMap: { [language: string]: string };
  forceRegex: boolean;
}
