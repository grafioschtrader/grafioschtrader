import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { catchError } from 'rxjs/operators';

import { AuthServiceWithLogout } from '../../lib/login/service/base.auth.service.with.logout';
import { LoginService } from '../../lib/login/service/log-in.service';
import { MessageToastService } from '../../lib/message/message.toast.service';
import { BaseSettings } from '../../lib/base.settings';
import { AppSettings } from '../../shared/app.settings';
import { SecurityLookupResponse } from '../model/security-lookup';

/**
 * Asks the backend to look up a security at the data providers by ISIN, currency, exchange and asset class. The backend
 * proposes master data and, per feed, the connectors that deliver data for the listing; nothing is saved.
 */
@Injectable()
export class SecurityLookupService extends AuthServiceWithLogout<any> {
  constructor(loginService: LoginService, httpClient: HttpClient, messageToastService: MessageToastService) {
    super(loginService, httpClient, messageToastService);
  }

  /**
   * Looks up the listings of an instrument.
   *
   * @param isin - ISIN of the instrument
   * @param currency - Trading currency
   * @param mic - MIC of the exchange the security is created on
   * @param idAssetClass - Asset class chosen in the form; selects the providers and connectors
   * @param ticker - Ticker entered in the form, a fallback search term; may be empty
   * @returns The candidates and the status of every provider that was asked
   */
  lookup(
    isin: string,
    currency: string,
    mic: string,
    idAssetClass: number,
    ticker?: string
  ): Observable<SecurityLookupResponse> {
    let params = new HttpParams()
      .set('isin', isin)
      .set('currency', currency)
      .set('mic', mic)
      .set('idAssetClass', idAssetClass);
    if (ticker?.trim()) {
      params = params.set('ticker', ticker.trim());
    }
    return this.httpClient
      .get<SecurityLookupResponse>(`${BaseSettings.API_ENDPOINT}${AppSettings.SECURITY_LOOKUP_KEY}/lookup`, {
        headers: this.prepareHeaders(),
        params
      })
      .pipe(catchError(this.handleError.bind(this)));
  }
}
