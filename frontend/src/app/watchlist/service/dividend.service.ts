import { AuthServiceWithLogout } from '../../lib/login/service/base.auth.service.with.logout';
import { Service } from '@angular/core';
import { Observable } from 'rxjs';
import { AppSettings } from '../../shared/app.settings';
import { catchError } from 'rxjs/operators';
import { Dividend } from '../../entities/dividend.split';
import { BaseSettings } from '../../lib/base.settings';
import type {
  SecurityDividendChart,
  SecurityDividendMetrics
} from '../../entities/view/securitydividends/security.dividend.metrics';

@Service()
export class DividendService extends AuthServiceWithLogout<Dividend> {
  getDividendsByIdSecuritycurrency(idSecuritycurrency: number): Observable<Dividend[]> {
    return this.httpClient
      .get<Dividend[]>(
        `${BaseSettings.API_ENDPOINT}${AppSettings.SECURITY_DIVIDEND_KEY}` + `/${idSecuritycurrency}`,
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  /**
   * Loads the dividend key figures of a security together with the series of the dividend chart. The figures come
   * from the shared dividend table, not from the distributions of the tenant.
   *
   * @param idSecuritycurrency - The security whose figures are requested
   */
  getDividendMetrics(idSecuritycurrency: number): Observable<SecurityDividendMetrics> {
    return this.httpClient
      .get<SecurityDividendMetrics>(
        `${BaseSettings.API_ENDPOINT}${AppSettings.SECURITY_DIVIDEND_KEY}/${idSecuritycurrency}/metrics`,
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  /**
   * Loads the series of the dividend chart of one or several securities, at most ten.
   *
   * @param idsSecuritycurrency - The securities in the order of the chart
   */
  getDividendChart(idsSecuritycurrency: number[]): Observable<SecurityDividendChart> {
    return this.httpClient
      .get<SecurityDividendChart>(`${BaseSettings.API_ENDPOINT}${AppSettings.SECURITY_DIVIDEND_KEY}/chart`, {
        ...this.getHeaders(),
        params: { ids: idsSecuritycurrency.join(',') }
      })
      .pipe(catchError(this.handleError.bind(this)));
  }
}
