import {Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {catchError} from 'rxjs/operators';

import {AuthServiceWithLogout} from '../../lib/login/service/base.auth.service.with.logout';
import {LoginService} from '../../lib/login/service/log-in.service';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {BaseSettings} from '../../lib/base.settings';
import {AppSettings} from '../../shared/app.settings';
import {GTNetExchange, GTNetSupplierWithDetails, GTSecuritiyCurrencyExchange} from '../model/gtnet';
import {DeleteService} from '../../lib/datashowbase/delete.service';

/**
 * Service for managing GTNetExchange configurations.
 *
 * Provides methods to retrieve, update, and manage exchange configurations
 * for securities and currency pairs participating in GTNet price data sharing.
 */
@Injectable({
  providedIn: 'root'
})
export class GTNetExchangeService extends AuthServiceWithLogout<GTNetExchange> implements DeleteService {

  constructor(loginService: LoginService, httpClient: HttpClient, messageToastService: MessageToastService) {
    super(loginService, httpClient, messageToastService);
  }

  /**
   * Retrieves all GTNetExchange entries for securities.
   *
   * @param activeOnly When true, returns only active securities
   * @returns Observable of GTSecuritiyCurrencyExchange
   */
  getSecurities(activeOnly: boolean = true): Observable<GTSecuritiyCurrencyExchange> {
    return this.httpClient.get<GTSecuritiyCurrencyExchange>(
      `${BaseSettings.API_ENDPOINT}${AppSettings.GT_NET_EXCHANGE_KEY}/securities?activeOnly=${activeOnly}`,
      this.getHeaders()
    ).pipe(catchError(this.handleError.bind(this)));
  }

  /**
   * Retrieves all GTNetExchange entries for currency pairs.
   *
   * @returns Observable of GTSecuritiyCurrencyExchange
   */
  getCurrencypairs(): Observable<GTSecuritiyCurrencyExchange> {
    return this.httpClient.get<GTSecuritiyCurrencyExchange>(
      `${BaseSettings.API_ENDPOINT}${AppSettings.GT_NET_EXCHANGE_KEY}/currencypairs`,
      this.getHeaders()
    ).pipe(catchError(this.handleError.bind(this)));
  }

  /**
   * Batch updates multiple GTNetExchange entries.
   *
   * @param exchanges Array of GTNetExchange entities to update
   * @returns Observable of updated GTNetExchange array
   */
  batchUpdate(exchanges: GTNetExchange[]): Observable<GTNetExchange[]> {
    return this.httpClient.post<GTNetExchange[]>(
      `${BaseSettings.API_ENDPOINT}${AppSettings.GT_NET_EXCHANGE_KEY}/batch`,
      exchanges,
      this.getHeaders()
    ).pipe(catchError(this.handleError.bind(this)));
  }

  /**
   * Adds a security to the GTNetExchange configuration.
   *
   * @param idSecuritycurrency The ID of the security to add
   * @returns Observable of the created GTNetExchange entry
   */
  addSecurity(idSecuritycurrency: number): Observable<GTNetExchange> {
    return this.httpClient.post<GTNetExchange>(
      `${BaseSettings.API_ENDPOINT}${AppSettings.GT_NET_EXCHANGE_KEY}/addsecurity/${idSecuritycurrency}`,
      {},
      this.getHeaders()
    ).pipe(catchError(this.handleError.bind(this)));
  }

  /**
   * Adds a currency pair to the GTNetExchange configuration.
   *
   * @param idSecuritycurrency The ID of the currency pair to add
   * @returns Observable of the created GTNetExchange entry
   */
  addCurrencypair(idSecuritycurrency: number): Observable<GTNetExchange> {
    return this.httpClient.post<GTNetExchange>(
      `${BaseSettings.API_ENDPOINT}${AppSettings.GT_NET_EXCHANGE_KEY}/addcurrencypair/${idSecuritycurrency}`,
      {},
      this.getHeaders()
    ).pipe(catchError(this.handleError.bind(this)));
  }

  /**
   * Deletes a GTNetExchange entry.
   *
   * @param id The ID of the GTNetExchange entry to delete
   * @returns Observable of void
   */
  deleteExchange(id: number): Observable<void> {
    return this.httpClient.delete<void>(
      `${BaseSettings.API_ENDPOINT}${AppSettings.GT_NET_EXCHANGE_KEY}/${id}`,
      this.getHeaders()
    ).pipe(catchError(this.handleError.bind(this)));
  }

  /**
   * Implements DeleteService interface.
   */
  deleteEntity(id: number | string): Observable<any> {
    return this.deleteExchange(typeof id === 'number' ? id : parseInt(id, 10));
  }

  /**
   * Retrieves supplier details for a given security or currency pair.
   *
   * @param idSecuritycurrency The ID of the security or currency pair
   * @returns Observable of GTNetSupplierWithDetails array
   */
  getSupplierDetails(idSecuritycurrency: number): Observable<GTNetSupplierWithDetails[]> {
    return this.httpClient.get<GTNetSupplierWithDetails[]>(
      `${BaseSettings.API_ENDPOINT}${AppSettings.GT_NET_EXCHANGE_KEY}/supplierdetails/${idSecuritycurrency}`,
      this.getHeaders()
    ).pipe(catchError(this.handleError.bind(this)));
  }

  /**
   * Triggers the exchange sync background job.
   *
   * Creates a background task to sync GTNetExchange configurations with GTNet peers.
   * This updates GTNetSupplierDetail entries based on what instruments each peer offers.
   *
   * @returns Observable of void
   */
  triggerSync(): Observable<void> {
    return this.httpClient.post<void>(
      `${BaseSettings.API_ENDPOINT}${AppSettings.GT_NET_EXCHANGE_KEY}/triggersync`,
      {},
      this.getHeaders()
    ).pipe(catchError(this.handleError.bind(this)));
  }
}
