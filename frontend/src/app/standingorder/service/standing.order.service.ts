import {Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {catchError} from 'rxjs/operators';
import {AuthServiceWithLogout} from '../../lib/login/service/base.auth.service.with.logout';
import {DeleteService} from '../../lib/datashowbase/delete.service';
import {ServiceEntityUpdate} from '../../lib/edit/service.entity.update';
import {LoginService} from '../../lib/login/service/log-in.service';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {QuoteToleranceRange, StandingOrder, StandingOrderFailure} from '../../entities/standing.order';
import {Transaction} from '../../entities/transaction';
import {AppSettings} from '../../shared/app.settings';
import {BaseSettings} from '../../lib/base.settings';

@Injectable()
export class StandingOrderService extends AuthServiceWithLogout<StandingOrder> implements DeleteService,
  ServiceEntityUpdate<StandingOrder> {

  constructor(loginService: LoginService, httpClient: HttpClient, messageToastService: MessageToastService) {
    super(loginService, httpClient, messageToastService);
  }

  getAllForTenant(): Observable<StandingOrder[]> {
    return <Observable<StandingOrder[]>>
      this.httpClient.get(`${BaseSettings.API_ENDPOINT}${AppSettings.STANDING_ORDER_KEY}/tenant`,
        this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  update(standingOrder: StandingOrder): Observable<StandingOrder> {
    return this.updateEntity(standingOrder, standingOrder.idStandingOrder, AppSettings.STANDING_ORDER_KEY);
  }

  getTransactions(idStandingOrder: number): Observable<Transaction[]> {
    return <Observable<Transaction[]>>
      this.httpClient.get(`${BaseSettings.API_ENDPOINT}${AppSettings.STANDING_ORDER_KEY}/${idStandingOrder}/transactions`,
        this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  getFailures(idStandingOrder: number): Observable<StandingOrderFailure[]> {
    return <Observable<StandingOrderFailure[]>>
      this.httpClient.get(`${BaseSettings.API_ENDPOINT}${AppSettings.STANDING_ORDER_KEY}/${idStandingOrder}/failures`,
        this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  /**
   * Loads the range an administrator permits for the quote tolerance of a standing order. The edit dialog uses it to
   * narrow its input, the server enforces the same bounds on save.
   */
  getQuoteToleranceRange(): Observable<QuoteToleranceRange> {
    return <Observable<QuoteToleranceRange>>
      this.httpClient.get(`${BaseSettings.API_ENDPOINT}${AppSettings.STANDING_ORDER_KEY}/quotetolerancerange`,
        this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  deleteEntity(idStandingOrder: number): Observable<any> {
    return this.httpClient.delete(`${BaseSettings.API_ENDPOINT}${AppSettings.STANDING_ORDER_KEY}/${idStandingOrder}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }
}
