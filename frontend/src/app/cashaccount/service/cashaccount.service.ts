import { Service } from '@angular/core';
import { AppSettings } from '../../shared/app.settings';
import { AccountPositionGroupSummary } from '../../entities/view/account.position.group.summary';
import { Cashaccount } from '../../entities/cashaccount';
import { Observable } from 'rxjs';
import { AuthServiceWithLogout } from '../../lib/login/service/base.auth.service.with.logout';
import { ServiceEntityUpdate } from '../../lib/edit/service.entity.update';
import { catchError } from 'rxjs/operators';
import { AppHelper } from '../../lib/helper/app.helper';
import { BaseSettings } from '../../lib/base.settings';

@Service()
export class CashaccountService extends AuthServiceWithLogout<Cashaccount> implements ServiceEntityUpdate<Cashaccount> {
  getCashaccountPositionGroupSummary(idCashaccount: number, untilDate: Date): Observable<AccountPositionGroupSummary> {
    return <Observable<AccountPositionGroupSummary>>(
      this.httpClient
        .get(
          `${BaseSettings.API_ENDPOINT}${AppSettings.CASHACCOUNT_KEY}/` +
            `${idCashaccount}/portfoliocashaccountsummary`,
          AppHelper.getOptionsWithUntilDate(untilDate, this.prepareHeaders())
        )
        .pipe(catchError(this.handleError.bind(this)))
    );
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
