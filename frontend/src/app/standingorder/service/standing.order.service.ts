import { Service } from '@angular/core';
import { Observable } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { AuthServiceWithLogout } from '../../lib/login/service/base.auth.service.with.logout';
import { DeleteService } from '../../lib/datashowbase/delete.service';
import { ServiceEntityUpdate } from '../../lib/edit/service.entity.update';
import {
  QuoteToleranceRange,
  StandingOrder,
  StandingOrderCapabilities,
  StandingOrderFailure
} from '../../entities/standing.order';
import { Transaction } from '../../entities/transaction';
import { AppSettings } from '../../shared/app.settings';
import { BaseSettings } from '../../lib/base.settings';

@Service()
export class StandingOrderService
  extends AuthServiceWithLogout<StandingOrder>
  implements DeleteService, ServiceEntityUpdate<StandingOrder>
{
  getAllForTenant(): Observable<StandingOrder[]> {
    return this.httpClient
      .get<StandingOrder[]>(`${BaseSettings.API_ENDPOINT}${AppSettings.STANDING_ORDER_KEY}/tenant`, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }

  update(standingOrder: StandingOrder): Observable<StandingOrder> {
    return this.updateEntity(standingOrder, standingOrder.idStandingOrder, AppSettings.STANDING_ORDER_KEY);
  }

  getTransactions(idStandingOrder: number): Observable<Transaction[]> {
    return this.httpClient
      .get<Transaction[]>(
        `${BaseSettings.API_ENDPOINT}${AppSettings.STANDING_ORDER_KEY}/${idStandingOrder}/transactions`,
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  getFailures(idStandingOrder: number): Observable<StandingOrderFailure[]> {
    return this.httpClient
      .get<StandingOrderFailure[]>(
        `${BaseSettings.API_ENDPOINT}${AppSettings.STANDING_ORDER_KEY}/${idStandingOrder}/failures`,
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  /**
   * Loads the range an administrator permits for the quote tolerance of a standing order. The edit dialog uses it to
   * narrow its input, the server enforces the same bounds on save.
   */
  getQuoteToleranceRange(): Observable<QuoteToleranceRange> {
    return this.httpClient
      .get<QuoteToleranceRange>(
        `${BaseSettings.API_ENDPOINT}${AppSettings.STANDING_ORDER_KEY}/quotetolerancerange`,
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  getCapabilities(): Observable<StandingOrderCapabilities> {
    return this.httpClient
      .get<StandingOrderCapabilities>(
        `${BaseSettings.API_ENDPOINT}${AppSettings.STANDING_ORDER_KEY}/capabilities`,
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  deleteEntity(idStandingOrder: number): Observable<any> {
    return this.httpClient
      .delete(`${BaseSettings.API_ENDPOINT}${AppSettings.STANDING_ORDER_KEY}/${idStandingOrder}`, this.getHeaders())
      .pipe(catchError(this.handleError.bind(this)));
  }
}
