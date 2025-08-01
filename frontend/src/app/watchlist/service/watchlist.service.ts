import {Injectable} from '@angular/core';
import {Watchlist} from '../../entities/watchlist';
import {AppSettings} from '../../shared/app.settings';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {SecuritycurrencyGroup, SecuritycurrencyUDFGroup} from '../../entities/view/securitycurrency.group';
import {SecuritycurrencySearch} from '../../entities/search/securitycurrency.search';
import {SecuritycurrencyLists} from '../../entities/view/securitycurrency.lists';
import {CurrencypairWatchlist} from '../../entities/view/currencypair.watchlist';
import {Security} from '../../entities/security';
import {Currencypair} from '../../entities/currencypair';
import {Observable} from 'rxjs';
import {HttpClient, HttpParams} from '@angular/common/http';
import {AuthServiceWithLogout} from '../../lib/login/service/base.auth.service.with.logout';
import {Securitycurrency} from '../../entities/securitycurrency';
import {ServiceEntityUpdate} from '../../lib/edit/service.entity.update';
import {AppHelper} from '../../lib/helper/app.helper';
import {catchError} from 'rxjs/operators';
import {LoginService} from '../../lib/login/service/log-in.service';
import {TenantLimit} from '../../entities/backend/tenant.limit';
import {AddSearchToListService} from '../component/add-instrument-table.component';
import {IntraHistoricalWatchlistProblem} from '../model/intra.historical.watchlist.problem';
import {BaseSettings} from '../../lib/base.settings';


@Injectable()
export class WatchlistService extends AuthServiceWithLogout<Watchlist> implements ServiceEntityUpdate<Watchlist>,
  AddSearchToListService<Watchlist> {

  constructor(loginService: LoginService, httpClient: HttpClient, messageToastService: MessageToastService) {
    super(loginService, httpClient, messageToastService);
  }

  getSecuritiesCurrenciesWatchlistLimits(idWatchlist: number): Observable<TenantLimit[]> {
    return <Observable<TenantLimit[]>>this.httpClient.get(`${BaseSettings.API_ENDPOINT}${AppSettings.WATCHLIST_KEY}/` +
      `${idWatchlist}/limitsecuritiescurrencies`, this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }


  getWatchlistsByIdTenant(): Observable<Watchlist[]> {
    return <Observable<Watchlist[]>>this.httpClient.get(`${BaseSettings.API_ENDPOINT}${AppSettings.WATCHLIST_KEY}/` +
      `${AppSettings.TENANT_KEY}`, this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  getWatchlistsOfTenantHasSecurity(): Observable<{ idWatchlist: number, hasSecurity: number }[]> {
    return <Observable<{ idWatchlist: number, hasSecurity: number }[]>>this.httpClient.get(
      `${BaseSettings.API_ENDPOINT}${AppSettings.WATCHLIST_KEY}/hassecurity`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  getAllWatchlistsWithSecurityByIdSecuritycurrency(idSecuritycurrency: number): Observable<number[]> {
    return <Observable<number[]>>this.httpClient.get(`${BaseSettings.API_ENDPOINT}${AppSettings.WATCHLIST_KEY}`
      + `/existssecurity/${idSecuritycurrency}`, this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  getWatchlistWithoutUpdate(idWatchlist: number): Observable<SecuritycurrencyGroup> {
    return <Observable<SecuritycurrencyGroup>>this.httpClient.get(`${BaseSettings.API_ENDPOINT}${AppSettings.WATCHLIST_KEY}/${idWatchlist}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  getWatchlistWithUDFData(idWatchlist: number): Observable<SecuritycurrencyUDFGroup> {
    return <Observable<SecuritycurrencyUDFGroup>>this.httpClient.get(`${BaseSettings.API_ENDPOINT}${AppSettings.WATCHLIST_KEY}/`
      + `${idWatchlist}/udf`, this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  getWatchlistWithoutUpdateAndMaxHistoryquote(idWatchlist: number): Observable<SecuritycurrencyGroup> {
    return <Observable<SecuritycurrencyGroup>>this.httpClient.get(`${BaseSettings.API_ENDPOINT}${AppSettings.WATCHLIST_KEY}/`
      + `${idWatchlist}/maxhistoryquote`, this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  getWatchlistWithPeriodPerformance(idWatchlist: number, daysTimeFrame: number): Observable<SecuritycurrencyGroup> {
    return <Observable<SecuritycurrencyGroup>>this.httpClient.get(`${BaseSettings.API_ENDPOINT}${AppSettings.WATCHLIST_KEY}/`
      + `${idWatchlist}/quote`, this.getOptionsWithDaysTimeFrame(daysTimeFrame)).pipe(catchError(this.handleError.bind(this)));
  }

  getWatchlistForSplitAndDividend(idWatchlist: number): Observable<SecuritycurrencyGroup> {
    return <Observable<SecuritycurrencyGroup>>this.httpClient.get(`${BaseSettings.API_ENDPOINT}${AppSettings.WATCHLIST_KEY}/`
      + `${idWatchlist}/dividendsplit`, this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  tryUpToDateHistoricalDataWhenRetryHistoryLoadGreaterThan0(idWatchlist: number): Observable<SecuritycurrencyLists> {
    return <Observable<SecuritycurrencyLists>>this.httpClient.get(`${BaseSettings.API_ENDPOINT}${AppSettings.WATCHLIST_KEY}/`
      + `${idWatchlist}/tryuptodatehistoricaldata`, this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  tryUpToIntradayDataWhenRetryIntraLoadGreaterThan0(idWatchlist: number): Observable<SecuritycurrencyLists> {
    return <Observable<SecuritycurrencyLists>>this.httpClient.get(`${BaseSettings.API_ENDPOINT}${AppSettings.WATCHLIST_KEY}/`
      + `${idWatchlist}/tryuptodateintradata`, this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  update(watchlist: Watchlist) {
    return super.updateEntity(watchlist, watchlist.idWatchlist, AppSettings.WATCHLIST_KEY);
  }

  delete(idWatchlist: number) {
    return this.httpClient.delete(`${BaseSettings.API_ENDPOINT}${AppSettings.WATCHLIST_KEY}/${idWatchlist}`, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }

  searchByCriteria(idWatchlist: number, securitycurrencySearch: SecuritycurrencySearch): Observable<SecuritycurrencyLists> {
    return <Observable<SecuritycurrencyLists>>this.httpClient.get(`${BaseSettings.API_ENDPOINT}${AppSettings.WATCHLIST_KEY}`
      + `/${idWatchlist}/search`, {
      headers: this.prepareHeaders(),
      params: AppHelper.getHttpParamsOfObjectAllowBooleanNullFields(securitycurrencySearch, ['onlyTenantPrivate'])
    }).pipe(catchError(this.handleError.bind(this)));
  }

  addSecurityToWatchlist(idWatchlist: number, security: Security): Observable<Watchlist> {
    const securitycurrencyLists = new SecuritycurrencyLists();
    securitycurrencyLists.securityList = [security];
    return this.addSecuritycurrenciesToList(idWatchlist, securitycurrencyLists);
  }

  getDataProviderLinkForUser(idSecuritycurrency: number, isIntraday: boolean, isSecurity: boolean): Observable<string> {
    let httpParams = new HttpParams();
    httpParams = httpParams.append('isIntraday', isIntraday.toString());
    httpParams = httpParams.append('isSecurity', isSecurity.toString());
    return <Observable<string>>this.httpClient.get(`${BaseSettings.API_ENDPOINT}${AppSettings.WATCHLIST_KEY}`
      + `/dataproviderlink/${idSecuritycurrency}`, {
        headers: this.prepareHeaders(), params: httpParams,
        responseType: 'text'
      }
    ).pipe(catchError(this.handleError.bind(this)));
  }

  addInstrumentsWithPriceDataProblems(idWatchlist: number, intraHistoricalWatchlistProblem: IntraHistoricalWatchlistProblem): Observable<Watchlist> {
    return <Observable<Watchlist>>this.httpClient.patch(`${BaseSettings.API_ENDPOINT}`
      + `${AppSettings.WATCHLIST_KEY}/${idWatchlist}/pricedataproblems`, intraHistoricalWatchlistProblem,
      {headers: this.prepareHeaders()}).pipe(catchError(this.handleError.bind(this)));
  }

  addSecuritycurrenciesToList(idWatchlist: number, securitycurrencyLists: SecuritycurrencyLists): Observable<Watchlist> {
    return <Observable<Watchlist>>this.httpClient.put(`${BaseSettings.API_ENDPOINT}${AppSettings.WATCHLIST_KEY}/${idWatchlist}`
      + `/addSecuritycurrency`, securitycurrencyLists, this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  removeSecuritycurrencyFromWatchlistAndDelete(idWatchlist: number, securitycurrency: Securitycurrency) {
    const urlSecuritycurrency = securitycurrency instanceof Currencypair ? 'removeDeleteCurrencypair' : 'removeDeleteSecurity';
    return this.httpClient.delete(`${BaseSettings.API_ENDPOINT}${AppSettings.WATCHLIST_KEY}/${idWatchlist}/${urlSecuritycurrency}/`
      + `${securitycurrency.idSecuritycurrency}`, this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  moveSecuritycurrency(idWatchlistSource: number, idWatchlistTarget: number, idSecuritycurrency: number): Observable<boolean> {
    return this.httpClient.put(`${BaseSettings.API_ENDPOINT}${AppSettings.WATCHLIST_KEY}/${idWatchlistSource}/moveto/`
      + `${idWatchlistTarget}/securitycurrency/${idSecuritycurrency}`, null,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  removeMultipleFromWatchlist(idWatchlist: number, idsSecuritycurrencies: number[]) {
    let httpParams = new HttpParams();
    httpParams = httpParams.append('idsSecuritycurrencies', idsSecuritycurrencies.join(','));
    return this.httpClient.delete(`${BaseSettings.API_ENDPOINT}${AppSettings.WATCHLIST_KEY}/${idWatchlist}/removemultiple`,
      {headers: this.prepareHeaders(), params: httpParams})
      .pipe(catchError(this.handleError.bind(this)));
  }

  removeSecuritycurrenciesFromWatchlist(idWatchlist: number, securitycurrency: Security | Currencypair) {
    const target: string = securitycurrency instanceof CurrencypairWatchlist ? 'removeCurrencypair' : 'removeSecurity';

    return this.httpClient.delete(`${BaseSettings.API_ENDPOINT}${AppSettings.WATCHLIST_KEY}/${idWatchlist}/${target}/`
      + `${securitycurrency.idSecuritycurrency}`, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }

  private getOptionsWithDaysTimeFrame(daysTimeFrame: number) {
    const httpParams = new HttpParams().set('daysTimeFrame', '' + daysTimeFrame);
    return {headers: this.prepareHeaders(), params: httpParams};
  }


}
