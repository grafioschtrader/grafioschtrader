import {Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {catchError} from 'rxjs/operators';
import {AuthServiceWithLogout} from '../../lib/login/service/base.auth.service.with.logout';
import {DeleteService} from '../../lib/datashowbase/delete.service';
import {ServiceEntityUpdate} from '../../lib/edit/service.entity.update';
import {LoginService} from '../../lib/login/service/log-in.service';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {StandingOrder, StandingOrderFailure} from '../../entities/standing.order';
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

  getFailures(idStandingOrder: number): Observable<StandingOrderFailure[]> {
    return <Observable<StandingOrderFailure[]>>
      this.httpClient.get(`${BaseSettings.API_ENDPOINT}${AppSettings.STANDING_ORDER_KEY}/${idStandingOrder}/failures`,
        this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  deleteEntity(idStandingOrder: number): Observable<any> {
    return this.httpClient.delete(`${BaseSettings.API_ENDPOINT}${AppSettings.STANDING_ORDER_KEY}/${idStandingOrder}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }
}
