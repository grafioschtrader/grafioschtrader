import { HttpHeaders, HttpParams, HttpResourceRequest } from '@angular/common/http';
import { Service } from '@angular/core';
import moment from 'moment';
import { Observable } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { SecuritycurrencySearch } from '../../entities/search/securitycurrency.search';
import { SeasonalPeriodType } from '../../entities/seasonal.returns.result';
import { Security } from '../../entities/security';
import { InstrumentStatisticsResult } from '../../entities/view/instrument.statistics.result';
import { SecurityOpenPositionPerSecurityaccount } from '../../entities/view/security.open.position.per.securityaccount';
import { SecurityTransactionSummary } from '../../entities/view/security.transaction.summary';
import { BaseSettings } from '../../lib/base.settings';
import { ColumnConfig } from '../../lib/datashowbase/column.config';
import { AppHelper } from '../../lib/helper/app.helper';
import { ITaskExtendService } from '../../lib/taskdatamonitor/component/itask.extend.service';
import { AppSettings } from '../../shared/app.settings';
import { IFeedConnector } from '../../shared/securitycurrency/ifeed.connector';
import {
  HisotryqouteLinearFilledSummary,
  HistoryquoteFillGapsBounds,
  HistoryquoteFillGapsParam,
  type HistoryquoteQualityHead,
  type HistoryquoteQualityIds,
  IHistoryquoteQualityWithSecurityProp
} from '../model/historyquote.quality.group';
import type { SecurityCurrencypairDerivedLinks } from '../model/security.currencypair.derived.links';
import { SecurityDataProviderUrls } from '../model/security.data.provider.urls';
import { SecurityCurrencyService } from './security.currency.service';

@Service()
export class SecurityService extends SecurityCurrencyService<Security> implements ITaskExtendService {
  /** Builds the cancellable read used by signal-driven views and observable callers. */
  getHistoryquoteQualityByIdsRequest(historyquoteQualityIds: HistoryquoteQualityIds) {
    return {
      url: `${BaseSettings.API_ENDPOINT}${AppSettings.SECURITY_KEY}/historyquotequalityids`,
      headers: this.prepareHeaders(),
      params: AppHelper.getHttpParamsOfObject(historyquoteQualityIds)
    } satisfies HttpResourceRequest;
  }

  getTradableSecuritiesByTenantAndIdWatchlist(idWatchlist: number): Observable<Security[]> {
    return this.httpClient
      .get<Security[]>(
        `${BaseSettings.API_ENDPOINT}${AppSettings.SECURITY_KEY}/` + `${AppSettings.WATCHLIST_KEY}/${idWatchlist}`,
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  getUnusedSecurityForAlgo(idAlgoAssetclassSecurity: number): Observable<Security[]> {
    return this.httpClient
      .get<Security[]>(
        `${BaseSettings.API_ENDPOINT}${AppSettings.SECURITY_KEY}/` + `algounused/${idAlgoAssetclassSecurity}`,
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  getUnusedSecurityForAlgoCustom(idWatchlist: number, idAlgoAssetclassSecurity: number): Observable<Security[]> {
    return this.httpClient
      .get<Security[]>(
        `${BaseSettings.API_ENDPOINT}${AppSettings.SECURITY_KEY}/` +
          `algounusedcustom/${idWatchlist}/${idAlgoAssetclassSecurity}`,
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  getSecurityByIdSecuritycurrency(idSecuritycurrency: number): Observable<Security> {
    return this.httpClient
      .get<Security>(`${BaseSettings.API_ENDPOINT}${AppSettings.SECURITY_KEY}/${idSecuritycurrency}`, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }

  getOpenPositionByIdSecuritycurrencyAndIdTenant(
    idSecuritycurrency: number,
    dateString: string,
    before: boolean,
    idTransaction: number | null,
    idOpenMarginTransaction: number | null
  ): Observable<SecurityOpenPositionPerSecurityaccount> {
    return this.httpClient
      .get<SecurityOpenPositionPerSecurityaccount>(
        `${BaseSettings.API_ENDPOINT}${AppSettings.SECURITY_KEY}/${idSecuritycurrency}/date/` +
          `${dateString}/${before}`,
        {
          headers: this.prepareHeaders(),
          params: this.getHttpParamsTransactions(idTransaction, idOpenMarginTransaction)
        }
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  getFeedConnectors(
    idStockexchange?: number | null,
    assetclassType?: string,
    specInvInstrument?: string
  ): Observable<IFeedConnector[]> {
    let params = new HttpParams();
    if (idStockexchange != null) {
      params = params.set('idStockexchange', String(idStockexchange));
    }
    if (assetclassType != null) {
      params = params.set('assetclassType', assetclassType);
    }
    if (specInvInstrument != null) {
      params = params.set('specInvInstrument', specInvInstrument);
    }
    return this.httpClient
      .get<IFeedConnector[]>(`${BaseSettings.API_ENDPOINT}${AppSettings.SECURITY_KEY}/feedConnectors`, {
        headers: this.prepareHeaders(),
        params
      })
      .pipe(catchError(this.handleError.bind(this)));
  }

  /**
   * Returns whether this instrument is bound to the kind of stock exchange it belongs to, that is at least one history
   * quote, one period entered by the user, or one transaction exists. The system created period of an instrument on a
   * stock exchange without market value does not count on its own. Once it is true the instrument may only move to a
   * stock exchange of its own kind.
   *
   * @param idSecuritycurrency the instrument to check
   * @returns true when the instrument may only move within its own kind
   */
  isStockexchangeCategoryLocked(idSecuritycurrency: number): Observable<boolean> {
    return this.httpClient
      .get<boolean>(
        `${BaseSettings.API_ENDPOINT}${AppSettings.SECURITY_KEY}/${idSecuritycurrency}/stockexchangecategorylocked`,
        { headers: this.prepareHeaders() }
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  /**
   * Update the passed security.
   */
  update(security: Security): Observable<Security> {
    return this.updateEntity(security, security.idSecuritycurrency, AppSettings.SECURITY_KEY);
  }

  getTransactionsByIdTenantAndIdSecurity(
    idSecuritycurrency: number,
    forChart: boolean,
    untilDate?: string
  ): Observable<SecurityTransactionSummary> {
    const options = this.getOptionsWithIncludeForChart(forChart);
    if (untilDate) {
      options.params = options.params.append('untilDate', untilDate);
    }
    return this.httpClient
      .get<SecurityTransactionSummary>(
        `${BaseSettings.API_ENDPOINT}` + `${AppSettings.SECURITY_KEY}/tenantsecurity/${idSecuritycurrency}`,
        options
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  getTransactionsByIdPortfolioAndIdSecurity(
    idPortfolio: number,
    idSecuritycurrency: number,
    forChart: boolean,
    untilDate?: string
  ): Observable<SecurityTransactionSummary> {
    const options = this.getOptionsWithIncludeForChart(forChart);
    if (untilDate) {
      options.params = options.params.append('untilDate', untilDate);
    }
    return this.httpClient
      .get<SecurityTransactionSummary>(
        `${BaseSettings.API_ENDPOINT}${AppSettings.SECURITY_KEY}/${idPortfolio}/portfoliosecurity/${idSecuritycurrency}`,
        options
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  getTransactionsByIdSecurityaccountsAndIdSecurity(
    idsSecurityaccount: number[],
    idSecuritycurrency: number,
    forChart: boolean,
    untilDate?: string
  ): Observable<SecurityTransactionSummary> {
    const options = this.getOptionsWithIncludeForChart(forChart);
    if (untilDate) {
      options.params = options.params.append('untilDate', untilDate);
    }
    options.params = options.params.append('idsSecurityaccount', idsSecurityaccount.join(','));
    return this.httpClient
      .get<SecurityTransactionSummary>(
        `${BaseSettings.API_ENDPOINT}${AppSettings.SECURITY_KEY}/securityaccountsecurity/${idSecuritycurrency}`,
        options
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  searchByCriteria(securitycurrencySearch: SecuritycurrencySearch): Observable<Security[]> {
    return this.httpClient
      .get<Security[]>(`${BaseSettings.API_ENDPOINT}${AppSettings.SECURITY_KEY}/search`, {
        headers: this.prepareHeaders(),
        params: AppHelper.getHttpParamsOfObjectAllowBooleanNullFields(securitycurrencySearch, ['onlyTenantPrivate'])
      })
      .pipe(catchError(this.handleError.bind(this)));
  }

  getHistoryquoteQualityHead(groupedBy: string): Observable<HistoryquoteQualityHead> {
    return this.httpClient
      .get<HistoryquoteQualityHead>(
        `${BaseSettings.API_ENDPOINT}${AppSettings.SECURITY_KEY}/historyquotequality`,
        this.getOptionHistoryqouteQuality(groupedBy)
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  getHistoryquoteQualityByIds(
    historyquoteQualityIds: HistoryquoteQualityIds
  ): Observable<IHistoryquoteQualityWithSecurityProp[]> {
    const { url, ...options } = this.getHistoryquoteQualityByIdsRequest(historyquoteQualityIds);
    return this.httpClient
      .get<IHistoryquoteQualityWithSecurityProp[]>(url, options)
      .pipe(catchError(this.handleError.bind(this)));
  }

  fillHistoryquoteGapsLinear(
    idSecuritycurreny: number,
    fillGapsParam: HistoryquoteFillGapsParam
  ): Observable<HisotryqouteLinearFilledSummary> {
    return this.httpClient
      .post<HisotryqouteLinearFilledSummary>(
        `${BaseSettings.API_ENDPOINT}${AppSettings.SECURITY_KEY}/${idSecuritycurreny}/fillgapes`,
        fillGapsParam,
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  getFillGapsBounds(idSecuritycurreny: number): Observable<HistoryquoteFillGapsBounds> {
    return this.httpClient
      .get<HistoryquoteFillGapsBounds>(
        `${BaseSettings.API_ENDPOINT}${AppSettings.SECURITY_KEY}/${idSecuritycurreny}/fillgapsbounds`,
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  getSecurityStatisticsReturnResult(
    idSecuritycurrency: number,
    dateFrom: Date | string | undefined,
    dateTo: Date | string | undefined
  ): Observable<InstrumentStatisticsResult> {
    return this.httpClient
      .get<InstrumentStatisticsResult>(
        `${BaseSettings.API_ENDPOINT}` + `${AppSettings.SECURITY_KEY}/${idSecuritycurrency}/securitystatistics`,
        this.getDateFromAndTo(dateFrom, dateTo, this.prepareHeaders())
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  /**
   * Builds the request of the seasonal return matrix for an `httpResource` returning a `SeasonalReturnsResult`. Its
   * failures are reported through `handleResourceError`.
   *
   * @param idSecuritycurrency the analysed security or currency pair
   * @param periodType month or quarter columns
   * @param includeDividends whether the returns include dividends; ignored by the server when none are available
   * @param inTenantCurrency whether the returns are converted to the tenant's main currency
   * @returns the GET request of the matrix
   */
  getSeasonalReturnsRequest(
    idSecuritycurrency: number,
    periodType: SeasonalPeriodType,
    includeDividends: boolean,
    inTenantCurrency: boolean
  ) {
    return {
      url: `${BaseSettings.API_ENDPOINT}${AppSettings.SECURITY_KEY}/${idSecuritycurrency}/seasonalreturns`,
      headers: this.prepareHeaders(),
      params: { periodType, includeDividends, inTenantCurrency }
    } satisfies HttpResourceRequest;
  }

  private getDateFromAndTo(
    dateFrom: Date | string | undefined,
    dateTo: Date | string | undefined,
    httpHeaders: HttpHeaders
  ) {
    let httpParams = new HttpParams();
    dateFrom &&
      (httpParams = httpParams.append('dateFrom', moment(dateFrom).format(BaseSettings.FORMAT_DATE_SHORT_NATIVE)));
    dateTo && (httpParams = httpParams.append('dateTo', moment(dateTo).format(BaseSettings.FORMAT_DATE_SHORT_NATIVE)));
    return { headers: httpHeaders, params: httpParams };
  }

  getDerivedInstrumentsLinksForSecurity(idSecuritycurrency: number): Observable<SecurityCurrencypairDerivedLinks> {
    return this.httpClient
      .get<SecurityCurrencypairDerivedLinks>(
        `${BaseSettings.API_ENDPOINT}${AppSettings.SECURITY_KEY}/` + `${idSecuritycurrency}/derivedlinks`,
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  /**
   * Gets the data provider URLs for a security. Returns URLs for intraday, historical, dividend, and split data.
   */
  getDataProviderUrls(idSecuritycurrency: number): Observable<SecurityDataProviderUrls> {
    return this.httpClient
      .get<SecurityDataProviderUrls>(
        `${BaseSettings.API_ENDPOINT}${AppSettings.SECURITY_KEY}/${idSecuritycurrency}/dataproviderurls`,
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  private getHttpParams(idTransaction: number): HttpParams {
    return new HttpParams().set('idTransaction', idTransaction ? '' + idTransaction : '');
  }

  private getHttpParamsTransactions(
    idTransaction: number | null,
    idOpenMarginTransaction: string | number | null
  ): HttpParams {
    return new HttpParams()
      .set('idTransaction', idTransaction != null ? '' + idTransaction : '')
      .set('idOpenMarginTransaction', idOpenMarginTransaction != null ? '' + idOpenMarginTransaction : '');
  }

  private getOptionHistoryqouteQuality(groupedBy: string) {
    const httpParams = new HttpParams().set('groupedBy', groupedBy);
    return { headers: this.prepareHeaders(), params: httpParams };
  }

  supportAdditionalToolTipData(): boolean {
    return true;
  }

  getAdditionalData(): Observable<{ [key: number]: string }> {
    return this.httpClient
      .get<{ [key: number]: string }>(
        `${BaseSettings.API_ENDPOINT}${AppSettings.SECURITY_KEY}/tooltip`,
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  getToolTipByPath(dataobject: any, field: ColumnConfig, additionalData: { [key: number]: string }): any {
    if (field.field === 'idEntity' && (dataobject.entity === 'Security' || dataobject.entity === 'Currencypair')) {
      return additionalData[dataobject['idEntity']];
    }
    return null;
  }
}
