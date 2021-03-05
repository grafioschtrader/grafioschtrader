import {Security} from '../../entities/security';
import {Injectable} from '@angular/core';
import {AppSettings} from '../../shared/app.settings';
import {MessageToastService} from '../../shared/message/message.toast.service';
import {SecurityOpenPositionPerSecurityaccount} from '../../entities/view/security.open.position.per.securityaccount';
import {Observable} from 'rxjs';
import {IFeedConnector} from '../component/ifeed.connector';
import {AuthServiceWithLogout} from '../../shared/login/service/base.auth.service.with.logout';
import {HttpClient, HttpParams} from '@angular/common/http';
import {SecurityTransactionSummary} from '../../entities/view/security.transaction.summary';
import {SecuritycurrencySearch} from '../../entities/search/securitycurrency.search';
import {AppHelper} from '../../shared/helper/app.helper';
import {catchError} from 'rxjs/operators';
import {LoginService} from '../../shared/login/service/log-in.service';
import {
  HisotryqouteLinearFilledSummary,
  HistoryquoteQualityHead,
  HistoryquoteQualityIds,
  IHistoryquoteQualityWithSecurityProp
} from '../model/historyquote.quality.group';
import {SecurityCurrencypairDerivedLinks} from '../model/security.currencypair.derived.links';

@Injectable()
export class SecurityService extends AuthServiceWithLogout<Security> {

  constructor(loginService: LoginService, httpClient: HttpClient, messageToastService: MessageToastService) {
    super(loginService, httpClient, messageToastService);
  }


  getTradableSecuritiesByTenantAndIdWatschlist(idWatchlist: number): Observable<Security[]> {
    return <Observable<Security[]>>this.httpClient.get(`${AppSettings.API_ENDPOINT}${AppSettings.SECURITY_KEY}/`
      + `${AppSettings.WATCHLIST_KEY}/${idWatchlist}`, this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }


  getUnusedSecurityForAlgo(idAlgoAssetclassSecurity: number): Observable<Security[]> {
    return <Observable<Security[]>>this.httpClient.get(`${AppSettings.API_ENDPOINT}${AppSettings.SECURITY_KEY}/`
      + `algounused/${idAlgoAssetclassSecurity}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  getSecurityByIdSecuritycurrency(idSecuritycurrency: number): Observable<Security> {
    return <Observable<Security>>this.httpClient.get(`${AppSettings.API_ENDPOINT}${AppSettings.SECURITY_KEY}/${idSecuritycurrency}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  getOpenPositionByIdSecuritycurrencyAndIdTenant(idSecuritycurrency: number, dateString: string, before: boolean,
                                                 idTransaction: number, idOpenMarginTransaction: number):
    Observable<SecurityOpenPositionPerSecurityaccount> {
    return <Observable<SecurityOpenPositionPerSecurityaccount>>
      this.httpClient.get(`${AppSettings.API_ENDPOINT}${AppSettings.SECURITY_KEY}/${idSecuritycurrency}/date/`
        + `${dateString}/${before}`, {
        headers: this.prepareHeaders(), params: this.getHttpParamsTransactions(idTransaction, idOpenMarginTransaction)
      })
        .pipe(catchError(this.handleError.bind(this)));
  }

  getFeedConnectors(): Observable<IFeedConnector[]> {
    return <Observable<IFeedConnector[]>>this.httpClient.get(`${AppSettings.API_ENDPOINT}${AppSettings.SECURITY_KEY}/feedConnectors`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  /**
   * Update the passed security.
   */
  update(security: Security): Observable<Security> {
    return this.updateEntity(security, security.idSecuritycurrency, AppSettings.SECURITY_KEY);
  }

  getTransactionsByIdTenantAndIdSecurity(idSecuritycurrency: number, forChart: boolean): Observable<SecurityTransactionSummary> {
    return <Observable<SecurityTransactionSummary>>this.httpClient.get(`${AppSettings.API_ENDPOINT}`
      + `${AppSettings.SECURITY_KEY}/tenantsecurity/${idSecuritycurrency}`,
      this.getOptionsWithIncludeForChart(forChart)).pipe(catchError(this.handleError.bind(this)));
  }

  getTransactionsByIdPortfolioAndIdSecurity(idPortfolio: number, idSecuritycurrency: number,
                                            forChart: boolean): Observable<SecurityTransactionSummary> {
    return <Observable<SecurityTransactionSummary>>this.httpClient.get(
      `${AppSettings.API_ENDPOINT}${AppSettings.SECURITY_KEY}/${idPortfolio}/portfoliosecurity/${idSecuritycurrency}`,
      this.getOptionsWithIncludeForChart(forChart)).pipe(catchError(this.handleError.bind(this)));
  }

  getTransactionsByIdSecurityaccountsAndIdSecurity(idsSecurityaccount: number[], idSecuritycurrency: number,
                                                   forChart: boolean): Observable<SecurityTransactionSummary> {
    const options = this.getOptionsWithIncludeForChart(forChart);
    options.params = options.params.append('idsSecurityaccount', idsSecurityaccount.join(','));
    return <Observable<SecurityTransactionSummary>>this.httpClient.get(
      `${AppSettings.API_ENDPOINT}${AppSettings.SECURITY_KEY}/securityaccountsecurity/${idSecuritycurrency}`,
      options).pipe(catchError(this.handleError.bind(this)));
  }

  searchByCriteria(securitycurrencySearch: SecuritycurrencySearch): Observable<Security[]> {
    return <Observable<Security[]>>this.httpClient.get(`${AppSettings.API_ENDPOINT}${AppSettings.SECURITY_KEY}/search`,
      {
        headers: this.prepareHeaders(),
        params: AppHelper.getHttpParamsOfObject(securitycurrencySearch)
      }).pipe(catchError(this.handleError.bind(this)));
  }

  getHistoryquoteQualityHead(groupedBy: string): Observable<HistoryquoteQualityHead> {
    return <Observable<HistoryquoteQualityHead>>this.httpClient.get(
      `${AppSettings.API_ENDPOINT}${AppSettings.SECURITY_KEY}/historyquotequality`, this.getOptionHistoryqouteQuality(groupedBy))
      .pipe(catchError(this.handleError.bind(this)));
  }

  getHistoryquoteQualityByIds(historyquoteQualityIds: HistoryquoteQualityIds): Observable<IHistoryquoteQualityWithSecurityProp[]> {
    return <Observable<IHistoryquoteQualityWithSecurityProp[]>>this.httpClient.get(
      `${AppSettings.API_ENDPOINT}${AppSettings.SECURITY_KEY}/historyquotequalityids`,
      {
        headers: this.prepareHeaders(),
        params: AppHelper.getHttpParamsOfObject(historyquoteQualityIds)
      }).pipe(catchError(this.handleError.bind(this)));
  }

  fillHistoryquoteGapsLinear(idSecuritycurreny: number, moveWeekendToFriday: boolean): Observable<HisotryqouteLinearFilledSummary> {
    return <Observable<HisotryqouteLinearFilledSummary>>this.httpClient.post(
      `${AppSettings.API_ENDPOINT}${AppSettings.SECURITY_KEY}/${idSecuritycurreny}/fillgapes`,
      moveWeekendToFriday, this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  getDerivedInstrumentsLinksForSecurity(idSecuritycurrency: number): Observable<SecurityCurrencypairDerivedLinks> {
    return <Observable<SecurityCurrencypairDerivedLinks>>this.httpClient.get(`${AppSettings.API_ENDPOINT}${AppSettings.SECURITY_KEY}/`
      + `${idSecuritycurrency}/derivedlinks`, this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  private getHttpParams(idTransaction: number): HttpParams {
    return new HttpParams().set('idTransaction', idTransaction ? '' + idTransaction : '');
  }

  private getHttpParamsTransactions(idTransaction: number, idOpenMarginTransaction): HttpParams {
    return new HttpParams().set('idTransaction', idTransaction ? '' + idTransaction : '')
      .set('idOpenMarginTransaction', idOpenMarginTransaction ? '' + idOpenMarginTransaction : '');
  }

  private getOptionsWithIncludeForChart(forchart: boolean) {
    const httpParams = new HttpParams().set('forchart', forchart.toString());
    return {headers: this.prepareHeaders(), params: httpParams};
  }

  private getOptionHistoryqouteQuality(groupedBy: string) {
    const httpParams = new HttpParams().set('groupedBy', groupedBy);
    return {headers: this.prepareHeaders(), params: httpParams};
  }


}
