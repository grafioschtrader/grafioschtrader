import { Service } from '@angular/core';
import { Watchlist } from '../../entities/watchlist';
import { AppSettings } from '../../shared/app.settings';
import { SecuritycurrencyGroup, SecuritycurrencyUDFGroup } from '../../entities/view/securitycurrency.group';
import { SecuritycurrencySearch } from '../../entities/search/securitycurrency.search';
import { SecuritycurrencyLists } from '../../entities/view/securitycurrency.lists';
import { CurrencypairWatchlist } from '../../entities/view/currencypair.watchlist';
import { Security } from '../../entities/security';
import { Currencypair } from '../../entities/currencypair';
import { Observable } from 'rxjs';
import { HttpParams } from '@angular/common/http';
import { AuthServiceWithLogout } from '../../lib/login/service/base.auth.service.with.logout';
import { Securitycurrency } from '../../entities/securitycurrency';
import { ServiceEntityUpdate } from '../../lib/edit/service.entity.update';
import { AppHelper } from '../../lib/helper/app.helper';
import { catchError } from 'rxjs/operators';
import { TenantLimit } from '../../shared/types/tenant.limit';
import { AddSearchToListService } from '../component/add-instrument-table.component';
import { IntraHistoricalWatchlistProblem } from '../model/intra.historical.watchlist.problem';
import { WatchlistMoveStatus } from '../model/watchlist.move.status';
import { BaseSettings } from '../../lib/base.settings';

@Service()
export class WatchlistService
  extends AuthServiceWithLogout<Watchlist>
  implements ServiceEntityUpdate<Watchlist>, AddSearchToListService<Watchlist>
{
  getSecuritiesCurrenciesWatchlistLimits(idWatchlist: number): Observable<TenantLimit[]> {
    return this.httpClient
      .get<TenantLimit[]>(
        `${BaseSettings.API_ENDPOINT}${AppSettings.WATCHLIST_KEY}/` + `${idWatchlist}/limitsecuritiescurrencies`,
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  getWatchlistsByIdTenant(): Observable<Watchlist[]> {
    return this.httpClient
      .get<Watchlist[]>(
        `${BaseSettings.API_ENDPOINT}${AppSettings.WATCHLIST_KEY}/` + `${BaseSettings.TENANT_KEY}`,
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  getWatchlistsOfTenantHasSecurity(): Observable<{ idWatchlist: number; hasSecurity: number }[]> {
    return this.httpClient
      .get<{ idWatchlist: number; hasSecurity: number }[]>(
        `${BaseSettings.API_ENDPOINT}${AppSettings.WATCHLIST_KEY}/hassecurity`,
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  getWatchlistWithoutUpdate(idWatchlist: number): Observable<SecuritycurrencyGroup> {
    return this.httpClient
      .get<SecuritycurrencyGroup>(
        `${BaseSettings.API_ENDPOINT}${AppSettings.WATCHLIST_KEY}/${idWatchlist}`,
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  getWatchlistWithUDFData(idWatchlist: number): Observable<SecuritycurrencyUDFGroup> {
    return this.httpClient
      .get<SecuritycurrencyUDFGroup>(
        `${BaseSettings.API_ENDPOINT}${AppSettings.WATCHLIST_KEY}/` + `${idWatchlist}/udf`,
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  getWatchlistWithoutUpdateAndMaxHistoryquote(idWatchlist: number): Observable<SecuritycurrencyGroup> {
    return this.httpClient
      .get<SecuritycurrencyGroup>(
        `${BaseSettings.API_ENDPOINT}${AppSettings.WATCHLIST_KEY}/` + `${idWatchlist}/maxhistoryquote`,
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  /**
   * Loads the watchlist with updated prices and the daily, YTD and time frame changes.
   *
   * @param idWatchlist the watchlist
   * @param daysTimeFrame length of the custom time frame in days
   * @param includeDividends true to add the reinvested dividends of the securities to every change (total return)
   */
  getWatchlistWithPeriodPerformance(
    idWatchlist: number,
    daysTimeFrame: number,
    includeDividends = false
  ): Observable<SecuritycurrencyGroup> {
    return this.httpClient
      .get<SecuritycurrencyGroup>(
        `${BaseSettings.API_ENDPOINT}${AppSettings.WATCHLIST_KEY}/` + `${idWatchlist}/quote`,
        {
          headers: this.prepareHeaders(),
          params: new HttpParams()
            .set('daysTimeFrame', '' + daysTimeFrame)
            .set('includeDividends', includeDividends.toString())
        }
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  getWatchlistForSplitAndDividend(idWatchlist: number): Observable<SecuritycurrencyGroup> {
    return this.httpClient
      .get<SecuritycurrencyGroup>(
        `${BaseSettings.API_ENDPOINT}${AppSettings.WATCHLIST_KEY}/` + `${idWatchlist}/dividendsplit`,
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  tryUpToDateHistoricalDataWhenRetryHistoryLoadGreaterThan0(idWatchlist: number): Observable<SecuritycurrencyLists> {
    return this.httpClient
      .get<SecuritycurrencyLists>(
        `${BaseSettings.API_ENDPOINT}${AppSettings.WATCHLIST_KEY}/` + `${idWatchlist}/tryuptodatehistoricaldata`,
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  tryUpToIntradayDataWhenRetryIntraLoadGreaterThan0(idWatchlist: number): Observable<SecuritycurrencyLists> {
    return this.httpClient
      .get<SecuritycurrencyLists>(
        `${BaseSettings.API_ENDPOINT}${AppSettings.WATCHLIST_KEY}/` + `${idWatchlist}/tryuptodateintradata`,
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  update(watchlist: Watchlist) {
    return super.updateEntity(watchlist, watchlist.idWatchlist, AppSettings.WATCHLIST_KEY);
  }

  delete(idWatchlist: number) {
    return this.httpClient
      .delete(`${BaseSettings.API_ENDPOINT}${AppSettings.WATCHLIST_KEY}/${idWatchlist}`, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }

  searchByCriteria(
    idWatchlist: number,
    securitycurrencySearch: SecuritycurrencySearch
  ): Observable<SecuritycurrencyLists> {
    return this.httpClient
      .get<SecuritycurrencyLists>(
        `${BaseSettings.API_ENDPOINT}${AppSettings.WATCHLIST_KEY}` + `/${idWatchlist}/search`,
        {
          headers: this.prepareHeaders(),
          params: AppHelper.getHttpParamsOfObjectAllowBooleanNullFields(securitycurrencySearch, ['onlyTenantPrivate'])
        }
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  addSecurityToWatchlist(idWatchlist: number, securitycurrency: Security | Currencypair): Observable<Watchlist> {
    return this.addSecuritycurrenciesToList(idWatchlist, SecuritycurrencyLists.fromSecuritycurrency(securitycurrency));
  }

  getDataProviderLinkForUser(idSecuritycurrency: number, isIntraday: boolean, isSecurity: boolean): Observable<string> {
    let httpParams = new HttpParams();
    httpParams = httpParams.append('isIntraday', isIntraday.toString());
    httpParams = httpParams.append('isSecurity', isSecurity.toString());
    return <Observable<string>>this.httpClient
      .get(`${BaseSettings.API_ENDPOINT}${AppSettings.WATCHLIST_KEY}` + `/dataproviderlink/${idSecuritycurrency}`, {
        headers: this.prepareHeaders(),
        params: httpParams,
        responseType: 'text'
      })
      .pipe(catchError(this.handleError.bind(this)));
  }

  addInstrumentsWithPriceDataProblems(
    idWatchlist: number,
    intraHistoricalWatchlistProblem: IntraHistoricalWatchlistProblem
  ): Observable<Watchlist> {
    return this.httpClient
      .patch<Watchlist>(
        `${BaseSettings.API_ENDPOINT}` + `${AppSettings.WATCHLIST_KEY}/${idWatchlist}/pricedataproblems`,
        intraHistoricalWatchlistProblem,
        { headers: this.prepareHeaders() }
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  addSecuritycurrenciesToList(
    idWatchlist: number,
    securitycurrencyLists: SecuritycurrencyLists
  ): Observable<Watchlist> {
    return this.httpClient
      .put<Watchlist>(
        `${BaseSettings.API_ENDPOINT}${AppSettings.WATCHLIST_KEY}/${idWatchlist}` + `/addSecuritycurrency`,
        securitycurrencyLists,
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  removeSecuritycurrencyFromWatchlistAndDelete(idWatchlist: number, securitycurrency: Securitycurrency) {
    const urlSecuritycurrency =
      securitycurrency instanceof Currencypair ? 'removeDeleteCurrencypair' : 'removeDeleteSecurity';
    return this.httpClient
      .delete(
        `${BaseSettings.API_ENDPOINT}${AppSettings.WATCHLIST_KEY}/${idWatchlist}/${urlSecuritycurrency}/` +
          `${securitycurrency.idSecuritycurrency}`,
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  /**
   * Moves a single instrument from one watchlist to another. The returned status reports whether the instrument was
   * moved or why nothing was changed, so no additional existence check is required before the call.
   *
   * @param idWatchlistSource - Watchlist the instrument currently belongs to
   * @param idWatchlistTarget - Watchlist the instrument should be moved to
   * @param idSecuritycurrency - Security or currency pair to move
   * @returns Observable of the move outcome
   */
  moveSecuritycurrency(
    idWatchlistSource: number,
    idWatchlistTarget: number,
    idSecuritycurrency: number
  ): Observable<WatchlistMoveStatus> {
    return this.httpClient
      .put<WatchlistMoveStatus>(
        `${BaseSettings.API_ENDPOINT}${AppSettings.WATCHLIST_KEY}/${idWatchlistSource}/moveto/` +
          `${idWatchlistTarget}/securitycurrency/${idSecuritycurrency}`,
        null,
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  removeMultipleFromWatchlist(idWatchlist: number, idsSecuritycurrencies: number[]) {
    let httpParams = new HttpParams();
    httpParams = httpParams.append('idsSecuritycurrencies', idsSecuritycurrencies.join(','));
    return this.httpClient
      .delete(`${BaseSettings.API_ENDPOINT}${AppSettings.WATCHLIST_KEY}/${idWatchlist}/removemultiple`, {
        headers: this.prepareHeaders(),
        params: httpParams
      })
      .pipe(catchError(this.handleError.bind(this)));
  }

  removeSecuritycurrenciesFromWatchlist(idWatchlist: number, securitycurrency: Security | Currencypair) {
    const target: string = securitycurrency instanceof CurrencypairWatchlist ? 'removeCurrencypair' : 'removeSecurity';

    return this.httpClient
      .delete(
        `${BaseSettings.API_ENDPOINT}${AppSettings.WATCHLIST_KEY}/${idWatchlist}/${target}/` +
          `${securitycurrency.idSecuritycurrency}`,
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }
}
