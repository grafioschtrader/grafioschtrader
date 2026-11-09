import { Service } from '@angular/core';
import { HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { catchError } from 'rxjs/operators';

import { AuthServiceWithLogout } from '../../lib/login/service/base.auth.service.with.logout';
import { BaseSettings } from '../../lib/base.settings';
import { AppSettings } from '../../shared/app.settings';
import { CurrencypairLookupResponse, SecurityLookupResponse } from '../model/security-lookup';

/**
 * Asks the backend to look up a security at the data providers by ISIN, currency, exchange and asset class, or the
 * connectors of a currency pair by its two currencies. The backend proposes master data and, per feed, the connectors
 * that deliver data; nothing is saved.
 */
@Service()
export class SecurityLookupService extends AuthServiceWithLogout<any> {
  /**
   * Looks up the listings of an instrument.
   *
   * @param isin - ISIN of the instrument
   * @param currency - Trading currency
   * @param mic - MIC of the exchange the security is created on; null when the exchange has none, then the parameter
   *   is omitted and the listings are not restricted to one exchange
   * @param idAssetClass - Asset class chosen in the form; selects the providers and connectors
   * @param ticker - Ticker entered in the form, a fallback search term; may be empty
   * @returns The candidates and the status of every provider that was asked
   */
  lookup(
    isin: string,
    currency: string,
    mic: string | null | undefined,
    idAssetClass: number,
    ticker?: string
  ): Observable<SecurityLookupResponse> {
    let params = new HttpParams().set('isin', isin).set('currency', currency).set('idAssetClass', idAssetClass);
    if (mic != null) {
      params = params.set('mic', mic);
    }
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

  /**
   * Looks up the connectors of a currency pair. Every connector is tried with a request; the history probe loads the
   * whole history and therefore takes a few seconds.
   *
   * @param fromCurrency - Base currency, e.g. EUR or BTC
   * @param toCurrency - Quote currency
   * @returns The probed connectors of both feeds and the status of the search providers
   */
  lookupCurrencypair(fromCurrency: string, toCurrency: string): Observable<CurrencypairLookupResponse> {
    const params = new HttpParams().set('fromCurrency', fromCurrency).set('toCurrency', toCurrency);
    return this.httpClient
      .get<CurrencypairLookupResponse>(`${BaseSettings.API_ENDPOINT}${AppSettings.CURRENCYPAIR_LOOKUP_KEY}/lookup`, {
        headers: this.prepareHeaders(),
        params
      })
      .pipe(catchError(this.handleError.bind(this)));
  }
}
