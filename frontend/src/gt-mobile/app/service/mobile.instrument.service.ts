import { HttpParams } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { Observable } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { BaseSettings } from '../../../app/lib/base.settings';
import { BaseAuthService } from '../../../app/lib/login/service/base.auth.service';
import { MobileChartResponse } from '../instrument/mobile.instrument.chart';
import { MobileSecurityTransactionSummary } from '../instrument/mobile.instrument.transactions';
import { MobileLoginService } from './mobile.login.service';

/** Reads the transactions of the tenant on one security and the price history of an instrument, read only. */
@Service()
export class MobileInstrumentService extends BaseAuthService<MobileSecurityTransactionSummary> {
  private readonly loginService = inject(MobileLoginService);

  /**
   * All transactions of the tenant on a security across its portfolios, with the position they lead to.
   *
   * @param idSecuritycurrency Id of the security
   * @param forChart True to adjust the prices of the transactions for later splits, so they match the adjusted price
   *                 history of the chart; the list of transactions shows the prices as booked
   */
  getTransactions(idSecuritycurrency: number, forChart = false): Observable<MobileSecurityTransactionSummary> {
    return this.httpClient
      .get<MobileSecurityTransactionSummary>(
        `${BaseSettings.API_ENDPOINT}security/tenantsecurity/${idSecuritycurrency}`,
        {
          headers: this.prepareHeaders(),
          params: new HttpParams().set('forchart', String(forChart))
        }
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  /**
   * The price history of a security or currency pair for the chart, adjusted for splits.
   *
   * @param idSecuritycurrency Id of the instrument
   * @param includeDividends True for the prices with reinvested dividends (total return)
   */
  getChart(idSecuritycurrency: number, includeDividends: boolean): Observable<MobileChartResponse> {
    return this.httpClient
      .get<MobileChartResponse>(
        `${BaseSettings.API_ENDPOINT}historyquote/securitycurrency/${idSecuritycurrency}/forchart`,
        {
          headers: this.prepareHeaders(),
          params: new HttpParams().set('includeDividends', String(includeDividends))
        }
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  protected override toLogout(): void {
    this.loginService.logout();
  }
}
