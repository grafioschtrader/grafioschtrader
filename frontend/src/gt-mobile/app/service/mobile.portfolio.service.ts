import { HttpParams } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import moment from 'moment';
import { Observable } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { BaseSettings } from '../../../app/lib/base.settings';
import { BaseAuthService } from '../../../app/lib/login/service/base.auth.service';
import { MobileAccountGrandSummary, MobilePortfolioGrouping } from '../portfolio/mobile.portfolio.summary';
import { MobileLoginService } from './mobile.login.service';

/** Reads the report of all cash and security accounts of the tenant, read only. */
@Service()
export class MobilePortfolioService extends BaseAuthService<MobileAccountGrandSummary> {
  private readonly loginService = inject(MobileLoginService);

  /**
   * The accounts of the tenant grouped by portfolio or by currency, with all transactions up to today. The desktop
   * view reads the same report and also offers an earlier date.
   *
   * @param grouping How the accounts are grouped
   */
  getSummary(grouping: MobilePortfolioGrouping): Observable<MobileAccountGrandSummary> {
    return this.httpClient
      .get<MobileAccountGrandSummary>(`${BaseSettings.API_ENDPOINT}portfolio/securitycashaccountsummary/${grouping}`, {
        headers: this.prepareHeaders(),
        params: new HttpParams().set('untilDate', moment().format(BaseSettings.FORMAT_DATE_SHORT_NATIVE))
      })
      .pipe(catchError(this.handleError.bind(this)));
  }

  protected override toLogout(): void {
    this.loginService.logout();
  }
}
