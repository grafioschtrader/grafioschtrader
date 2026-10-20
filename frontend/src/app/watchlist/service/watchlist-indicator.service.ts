import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { catchError } from 'rxjs/operators';

import { WatchlistIndicatorColumn, SecuritycurrencyIndicatorGroup } from '../model/watchlist-indicator';
import { AuthServiceWithLogout } from '../../lib/login/service/base.auth.service.with.logout';
import { LoginService } from '../../lib/login/service/log-in.service';
import { MessageToastService } from '../../lib/message/message.toast.service';
import { BaseSettings } from '../../lib/base.settings';
import { DeleteService } from '../../lib/datashowbase/delete.service';
import { ServiceEntityUpdate } from '../../lib/edit/service.entity.update';

/** Tenant-owned indicator definition CRUD and atomic default/order operations. */
@Injectable({ providedIn: 'root' })
export class WatchlistIndicatorService
  extends AuthServiceWithLogout<WatchlistIndicatorColumn>
  implements DeleteService, ServiceEntityUpdate<WatchlistIndicatorColumn>
{
  private readonly url = BaseSettings.API_ENDPOINT + 'watchlistindicatorcolumn';
  constructor(loginService: LoginService, httpClient: HttpClient, messageToastService: MessageToastService) {
    super(loginService, httpClient, messageToastService);
  }
  markColors(): Observable<{ key: string; value: string }[]> {
    return this.httpClient.get<{ key: string; value: string }[]>(this.url + '/markcolors', this.getHeaders());
  }
  benchmarks(): Observable<Record<number, string>> {
    return this.httpClient
      .get<Record<number, string>>(this.url + '/benchmarks', this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }
  getAll(): Observable<WatchlistIndicatorColumn[]> {
    return this.httpClient
      .get<WatchlistIndicatorColumn[]>(this.url, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }
  report(id: number): Observable<SecuritycurrencyIndicatorGroup> {
    return this.httpClient
      .get<SecuritycurrencyIndicatorGroup>(
        BaseSettings.API_ENDPOINT + 'watchlist/' + id + '/indicator',
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }
  defaults(): Observable<WatchlistIndicatorColumn[]> {
    return this.httpClient
      .post<WatchlistIndicatorColumn[]>(this.url + '/defaults', {}, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }
  order(ids: number[]): Observable<WatchlistIndicatorColumn[]> {
    return this.httpClient
      .put<WatchlistIndicatorColumn[]>(this.url + '/order', ids, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }
  resultType(expression: string): Observable<{ resultType: string }> {
    return this.httpClient.post<{ resultType: string }>(this.url + '/resulttype', expression, this.getHeaders());
  }
  update(entity: WatchlistIndicatorColumn): Observable<WatchlistIndicatorColumn> {
    return this.updateEntity(entity, entity.idWatchlistIndicatorColumn, 'watchlistindicatorcolumn');
  }
  deleteEntity(id: number): Observable<any> {
    return this.httpClient.delete(this.url + '/' + id, this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }
}
