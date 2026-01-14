import {Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {catchError} from 'rxjs/operators';

import {AuthServiceWithLogout} from '../../lib/login/service/base.auth.service.with.logout';
import {LoginService} from '../../lib/login/service/log-in.service';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {BaseSettings} from '../../lib/base.settings';
import {AppSettings} from '../../shared/app.settings';
import {SecurityGtnetLookupRequest, SecurityGtnetLookupResponse} from '../model/gtnet-security-lookup';

/**
 * Service for looking up security metadata from local database and GTNet peers.
 * Provides functionality to search for securities by ISIN, currency, and ticker symbol.
 */
@Injectable()
export class GtnetSecurityLookupService extends AuthServiceWithLogout<any> {

  constructor(loginService: LoginService, httpClient: HttpClient, messageToastService: MessageToastService) {
    super(loginService, httpClient, messageToastService);
  }

  /**
   * Lookup security metadata by ISIN, currency, and/or ticker symbol.
   * Searches local database first, then queries configured GTNet peers.
   *
   * @param request the search criteria
   * @returns Observable of response containing matching securities and query statistics
   */
  lookupSecurity(request: SecurityGtnetLookupRequest): Observable<SecurityGtnetLookupResponse> {
    return this.httpClient.post<SecurityGtnetLookupResponse>(
      `${BaseSettings.API_ENDPOINT}${AppSettings.GT_NET_SECURITY_LOOKUP_KEY}/lookup`,
      request,
      this.getHeaders()
    ).pipe(catchError(this.handleError.bind(this)));
  }

  /**
   * Checks if there are accessible GTNet peers that support SECURITY_METADATA exchange.
   * Used to determine if the GTNet lookup button should be visible.
   *
   * @returns Observable of boolean indicating if accessible peers exist
   */
  hasAccessiblePeers(): Observable<boolean> {
    return this.httpClient.get<boolean>(
      `${BaseSettings.API_ENDPOINT}${AppSettings.GT_NET_SECURITY_LOOKUP_KEY}/haspeers`,
      this.getHeaders()
    ).pipe(catchError(this.handleError.bind(this)));
  }
}
