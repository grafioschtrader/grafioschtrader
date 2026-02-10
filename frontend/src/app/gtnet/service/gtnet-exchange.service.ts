import {Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {catchError} from 'rxjs/operators';

import {AuthServiceWithLogout} from '../../lib/login/service/base.auth.service.with.logout';
import {LoginService} from '../../lib/login/service/log-in.service';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {BaseSettings} from '../../lib/base.settings';
import {AppSettings} from '../../shared/app.settings';
import {GTNetSupplierWithDetails, GTSecuritiyCurrencyExchange} from '../../lib/gnet/model/gtnet';
import {Security} from '../../entities/security';
import {Currencypair} from '../../entities/currencypair';

/**
 * Service for managing GTNet exchange configurations on securities and currency pairs.
 *
 * The GTNet exchange fields (gtNetLastpriceRecv, gtNetHistoricalRecv, gtNetLastpriceSend,
 * gtNetHistoricalSend) are now directly on the Security and Currencypair entities.
 */
@Injectable({
  providedIn: 'root'
})
export class GTNetExchangeService extends AuthServiceWithLogout<Security> {

  constructor(loginService: LoginService, httpClient: HttpClient, messageToastService: MessageToastService) {
    super(loginService, httpClient, messageToastService);
  }

  /**
   * Retrieves all securities with their GTNet exchange configuration.
   *
   * @param activeOnly When true, returns only active securities
   * @returns Observable of GTSecuritiyCurrencyExchange
   */
  getSecurities(activeOnly: boolean = true): Observable<GTSecuritiyCurrencyExchange> {
    return this.httpClient.get<GTSecuritiyCurrencyExchange>(
      `${BaseSettings.API_ENDPOINT}${AppSettings.SECURITY_KEY}/gtnetexchange?activeOnly=${activeOnly}`,
      this.getHeaders()
    ).pipe(catchError(this.handleError.bind(this)));
  }

  /**
   * Retrieves all currency pairs with their GTNet exchange configuration.
   *
   * @returns Observable of GTSecuritiyCurrencyExchange
   */
  getCurrencypairs(): Observable<GTSecuritiyCurrencyExchange> {
    return this.httpClient.get<GTSecuritiyCurrencyExchange>(
      `${BaseSettings.API_ENDPOINT}${AppSettings.CURRENCYPAIR_KEY}/gtnetexchange`,
      this.getHeaders()
    ).pipe(catchError(this.handleError.bind(this)));
  }

  /**
   * Batch updates GTNet exchange fields for multiple securities.
   *
   * @param securities Array of Security entities with updated GTNet fields
   * @returns Observable of updated Security array
   */
  batchUpdateSecurities(securities: Security[]): Observable<Security[]> {
    return this.httpClient.post<Security[]>(
      `${BaseSettings.API_ENDPOINT}${AppSettings.SECURITY_KEY}/gtnetexchange/batch`,
      securities,
      this.getHeaders()
    ).pipe(catchError(this.handleError.bind(this)));
  }

  /**
   * Batch updates GTNet exchange fields for multiple currency pairs.
   *
   * @param currencypairs Array of Currencypair entities with updated GTNet fields
   * @returns Observable of updated Currencypair array
   */
  batchUpdateCurrencypairs(currencypairs: Currencypair[]): Observable<Currencypair[]> {
    return this.httpClient.post<Currencypair[]>(
      `${BaseSettings.API_ENDPOINT}${AppSettings.CURRENCYPAIR_KEY}/gtnetexchange/batch`,
      currencypairs,
      this.getHeaders()
    ).pipe(catchError(this.handleError.bind(this)));
  }

  /**
   * Retrieves supplier details for a given security.
   *
   * @param idSecuritycurrency The ID of the security
   * @returns Observable of GTNetSupplierWithDetails array
   */
  getSecuritySupplierDetails(idSecuritycurrency: number): Observable<GTNetSupplierWithDetails[]> {
    return this.httpClient.get<GTNetSupplierWithDetails[]>(
      `${BaseSettings.API_ENDPOINT}${AppSettings.SECURITY_KEY}/${idSecuritycurrency}/gtnetexchange/supplierdetails`,
      this.getHeaders()
    ).pipe(catchError(this.handleError.bind(this)));
  }

  /**
   * Retrieves supplier details for a given currency pair.
   *
   * @param idSecuritycurrency The ID of the currency pair
   * @returns Observable of GTNetSupplierWithDetails array
   */
  getCurrencypairSupplierDetails(idSecuritycurrency: number): Observable<GTNetSupplierWithDetails[]> {
    return this.httpClient.get<GTNetSupplierWithDetails[]>(
      `${BaseSettings.API_ENDPOINT}${AppSettings.CURRENCYPAIR_KEY}/${idSecuritycurrency}/gtnetexchange/supplierdetails`,
      this.getHeaders()
    ).pipe(catchError(this.handleError.bind(this)));
  }

  /**
   * Triggers the exchange sync background job.
   *
   * Creates a background task to sync GTNet exchange configurations with GTNet peers.
   * This updates GTNetSupplierDetail entries based on what instruments each peer offers.
   *
   * @returns Observable of void
   */
  triggerSync(): Observable<void> {
    return this.httpClient.post<void>(
      `${BaseSettings.API_ENDPOINT}${AppSettings.SECURITY_KEY}/gtnetexchange/triggersync`,
      {},
      this.getHeaders()
    ).pipe(catchError(this.handleError.bind(this)));
  }
}
