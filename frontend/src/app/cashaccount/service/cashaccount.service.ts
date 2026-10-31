import { HttpResourceRequest } from '@angular/common/http';
import { Service } from '@angular/core';
import { Observable } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { Cashaccount } from '../../entities/cashaccount';
import type { AccountPositionGroupSummary } from '../../entities/view/account.position.group.summary';
import { BaseSettings } from '../../lib/base.settings';
import { ServiceEntityUpdate } from '../../lib/edit/service.entity.update';
import { AppHelper } from '../../lib/helper/app.helper';
import { AuthServiceWithLogout } from '../../lib/login/service/base.auth.service.with.logout';
import { AppSettings } from '../../shared/app.settings';

@Service()
export class CashaccountService extends AuthServiceWithLogout<Cashaccount> implements ServiceEntityUpdate<Cashaccount> {
  /** Builds the cancellable read used by signal-driven views and observable callers. */
  getCashaccountPositionGroupSummaryRequest(idCashaccount: number, untilDate: Date) {
    return {
      url: `${BaseSettings.API_ENDPOINT}${AppSettings.CASHACCOUNT_KEY}/${idCashaccount}/portfoliocashaccountsummary`,
      ...AppHelper.getOptionsWithUntilDate(untilDate, this.prepareHeaders())
    } satisfies HttpResourceRequest;
  }

  getCashaccountPositionGroupSummary(idCashaccount: number, untilDate: Date): Observable<AccountPositionGroupSummary> {
    const { url, ...options } = this.getCashaccountPositionGroupSummaryRequest(idCashaccount, untilDate);
    return this.httpClient.get<AccountPositionGroupSummary>(url, options).pipe(catchError(this.handleError.bind(this)));
  }

  /**
   * Update the passed Cashaccount.
   */
  update(cashaccount: Cashaccount): Observable<Cashaccount> {
    return this.updateEntity(cashaccount, cashaccount.idSecuritycashAccount, AppSettings.CASHACCOUNT_KEY);
  }

  deleteCashaccount(idSecuritycashaccount: number): Observable<any> {
    return this.httpClient
      .delete(`${BaseSettings.API_ENDPOINT}${AppSettings.CASHACCOUNT_KEY}/${idSecuritycashaccount}`, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }
}
