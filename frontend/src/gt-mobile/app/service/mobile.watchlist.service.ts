import { HttpParams } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { Observable } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { BaseSettings } from '../../../app/lib/base.settings';
import { BaseAuthService } from '../../../app/lib/login/service/base.auth.service';
import { MobileWatchlist, MobileWatchlistGroup } from '../watchlist/mobile.watchlist.rows';
import { MobileLoginService } from './mobile.login.service';

/** Length of the custom time frame the price update asks for; the mobile application shows none of its figures. */
const DAYS_TIME_FRAME = 365;

/** Reads the watchlists of the tenant and their content, read only. */
@Service()
export class MobileWatchlistService extends BaseAuthService<MobileWatchlist> {
  private readonly loginService = inject(MobileLoginService);

  /** All watchlists of the tenant. */
  getWatchlists(): Observable<MobileWatchlist[]> {
    return this.httpClient
      .get<MobileWatchlist[]>(`${BaseSettings.API_ENDPOINT}watchlist/tenant`, { headers: this.prepareHeaders() })
      .pipe(catchError(this.handleError.bind(this)));
  }

  /** The content of a watchlist with the prices last stored, answered at once. */
  getWatchlist(idWatchlist: number): Observable<MobileWatchlistGroup> {
    return this.httpClient
      .get<MobileWatchlistGroup>(`${BaseSettings.API_ENDPOINT}watchlist/${idWatchlist}`, {
        headers: this.prepareHeaders()
      })
      .pipe(catchError(this.handleError.bind(this)));
  }

  /** The content of a watchlist after the server updated its prices; this can take several seconds. */
  getWatchlistWithUpdatedPrices(idWatchlist: number): Observable<MobileWatchlistGroup> {
    return this.httpClient
      .get<MobileWatchlistGroup>(`${BaseSettings.API_ENDPOINT}watchlist/${idWatchlist}/quote`, {
        headers: this.prepareHeaders(),
        params: new HttpParams().set('daysTimeFrame', String(DAYS_TIME_FRAME)).set('includeDividends', 'false')
      })
      .pipe(catchError(this.handleError.bind(this)));
  }

  protected override toLogout(): void {
    this.loginService.logout();
  }
}
