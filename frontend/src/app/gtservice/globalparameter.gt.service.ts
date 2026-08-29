import { Observable, of } from 'rxjs';
import { GlobalSessionNames } from '../lib/global.session.names';
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { MessageToastService } from '../lib/message/message.toast.service';
import { ValueKeyHtmlSelectOptions } from '../lib/dynamic-form/models/value.key.html.select.options';
import { AppSettings } from '../shared/app.settings';
import { catchError, tap } from 'rxjs/operators';
import { BaseAuthService } from '../lib/login/service/base.auth.service';
import { Globalparameters } from '../lib/entities/globalparameters';
import { AssetclassType } from '../shared/types/assetclass.type';
import { SpecialInvestmentInstruments } from '../shared/types/special.investment.instruments';
import { BaseSettings } from '../lib/base.settings';
import { GlobalGTSessionNames } from '../shared/global.gt.session.names';
import { MaxInstrumentLimits } from './max.instrument.limits';

@Injectable()
export class GlobalparameterGTService extends BaseAuthService<Globalparameters> {
  constructor(httpClient: HttpClient, messageToastService: MessageToastService) {
    super(httpClient, messageToastService);
  }

  public getIntraUpdateTimeout(): Observable<number> {
    return this.getGlobalparameterNumber(
      GlobalGTSessionNames.UPDATE_TIME_OUT,
      AppSettings.GT_GLOBALPARAMETERS_P_KEY,
      'updatetimeout'
    );
  }

  public getStartFeedDateAsTime(): Observable<number> {
    return this.getGlobalparameterNumber(
      GlobalGTSessionNames.START_FEED_DATE,
      AppSettings.GT_GLOBALPARAMETERS_P_KEY,
      'startfeeddate'
    );
  }

  /**
   * Returns the per-instrument editing limits (max splits and max history-quote periods) from the backend. The
   * result is cached in sessionStorage so the Security edit dialog fetches it at most once per session.
   */
  public getMaxInstrumentLimits(): Observable<MaxInstrumentLimits> {
    const cached = sessionStorage.getItem(GlobalGTSessionNames.MAX_INSTRUMENT_LIMITS);
    if (cached) {
      return of(JSON.parse(cached));
    }
    return <Observable<MaxInstrumentLimits>>(
      this.httpClient
        .get(
          `${BaseSettings.API_ENDPOINT}` + `${AppSettings.GT_GLOBALPARAMETERS_P_KEY}/maxinstrumentlimits`,
          this.getHeaders()
        )
        .pipe(
          tap((value) => sessionStorage.setItem(GlobalGTSessionNames.MAX_INSTRUMENT_LIMITS, JSON.stringify(value))),
          catchError(this.handleError.bind(this))
        )
    );
  }

  public getCurrencies(): Observable<ValueKeyHtmlSelectOptions[]> {
    return <Observable<ValueKeyHtmlSelectOptions[]>>(
      this.httpClient
        .get(
          `${BaseSettings.API_ENDPOINT}` + `${AppSettings.GT_GLOBALPARAMETERS_P_KEY}/${AppSettings.CURRENCIES_P_KEY}`,
          this.getHeaders()
        )
        .pipe(catchError(this.handleError.bind(this)))
    );
  }

  public getPossibleAssetclassInstrumentMap(): Observable<{ [key in AssetclassType]: SpecialInvestmentInstruments[] }> {
    return <Observable<{ [key in AssetclassType]: SpecialInvestmentInstruments[] }>>(
      this.httpClient
        .get(
          `${BaseSettings.API_ENDPOINT}${AppSettings.GT_GLOBALPARAMETERS_P_KEY}/possibleassetclassspezinstrument`,
          this.getHeaders()
        )
        .pipe(catchError(this.handleError.bind(this)))
    );
  }

  private getGlobalparameterNumber<T extends string>(
    globalSessionNames: T,
    requestMapping: string,
    uriPart: string
  ): Observable<number> {
    if (sessionStorage.getItem(globalSessionNames)) {
      return of(+sessionStorage.getItem(globalSessionNames));
    } else {
      return <Observable<number>>(
        this.httpClient.get(`${BaseSettings.API_ENDPOINT}` + `${requestMapping}/${uriPart}`, this.getHeaders()).pipe(
          tap((value) => sessionStorage.setItem(globalSessionNames, '' + value)),
          catchError(this.handleError.bind(this))
        )
      );
    }
  }

  /**
   * Gets the tenant-level closed-until date for transaction period locking.
   *
   * @returns The tenant's closedUntil date, or null if not set
   */
  public getTenantClosedUntil(): Date | null {
    const dateStr = sessionStorage.getItem(GlobalGTSessionNames.TENANT_CLOSED_UNTIL);
    return dateStr ? new Date(dateStr) : null;
  }

  /**
   * Gets the import platform of this instance holding the Grafioschtrader authored import templates, chosen by an
   * administrator and stored at login.
   *
   * @returns The ID of the instance's Grafioschtrader import platform, or null if no platform is configured
   */
  public getGtImportPlatformId(): number | null {
    const raw = sessionStorage.getItem(GlobalGTSessionNames.GT_IMPORT_PLATFORM_ID);
    return raw ? Number(raw) : null;
  }

  /**
   * Records the import platform an administrator has just chosen for this instance. Only this session is updated;
   * every other session picks the new platform up at its next login.
   *
   * @param gtImportPlatformId the platform now configured, or null when the setting was cleared
   */
  public setGtImportPlatformId(gtImportPlatformId: number | null): void {
    sessionStorage.setItem(
      GlobalGTSessionNames.GT_IMPORT_PLATFORM_ID,
      gtImportPlatformId != null ? String(gtImportPlatformId) : ''
    );
  }

  /**
   * Records whether this tenant opted in to the Grafioschtrader import templates. Written at login and refreshed
   * by the tenant edit dialog.
   *
   * @param useGtImportTemplates the tenant's opt-in
   */
  public setTenantUseGtImportTemplates(useGtImportTemplates: boolean): void {
    sessionStorage.setItem(GlobalGTSessionNames.TENANT_USE_GT_IMPORT_TEMPLATES, String(!!useGtImportTemplates));
  }

  /**
   * Gets the Grafioschtrader import platform usable by the current tenant. Two independent settings have to agree:
   * an administrator must have chosen the platform for this instance, and the tenant must have opted in. When both
   * hold, the transaction import entry points offer the "use GT platform" choice.
   *
   * @returns The ID of the Grafioschtrader import platform, or null when it is not available to this tenant
   */
  public getTenantGtImportPlatformId(): number | null {
    return sessionStorage.getItem(GlobalGTSessionNames.TENANT_USE_GT_IMPORT_TEMPLATES) === 'true'
      ? this.getGtImportPlatformId()
      : null;
  }

  /**
   * Returns the connector / asset class enforcement mode set by gt.force.connector.match.
   * 0 = off, 1 = server-side only, 2 = server-side + frontend dropdown pre-filter.
   */
  public getForceConnectorMatch(): number {
    const raw = sessionStorage.getItem(GlobalGTSessionNames.FORCE_CONNECTOR_MATCH);
    return raw == null ? 0 : Number(raw);
  }

  /**
   * True when the given currency code is one of the supported cryptocurrencies stored at login.
   */
  public isCryptocurrency(currencyCode: string): boolean {
    if (!currencyCode) {
      return false;
    }
    const raw = sessionStorage.getItem(GlobalGTSessionNames.CRYPTOS);
    if (!raw) {
      return false;
    }
    try {
      const list: string[] = JSON.parse(raw);
      return Array.isArray(list) && list.includes(currencyCode);
    } catch {
      return false;
    }
  }
}
