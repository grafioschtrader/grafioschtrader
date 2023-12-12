import {Injectable} from '@angular/core';
import {Observable} from 'rxjs';
import {AppSettings} from '../../shared/app.settings';
import {MessageToastService} from '../../shared/message/message.toast.service';
import {DeleteService} from '../../shared/datashowbase/delete.service';
import {HttpClient} from '@angular/common/http';
import {AuthServiceWithLogout} from '../../shared/login/service/base.auth.service.with.logout';

import {TradingPlatformPlan} from '../../entities/tradingplatformplan';
import {ServiceEntityUpdate} from '../../shared/edit/service.entity.update';
import {catchError} from 'rxjs/operators';
import {LoginService} from '../../shared/login/service/log-in.service';


@Injectable()
export class TradingPlatformPlanService extends AuthServiceWithLogout<TradingPlatformPlan>
  implements ServiceEntityUpdate<TradingPlatformPlan>, DeleteService {

  constructor(loginService: LoginService, httpClient: HttpClient, messageToastService: MessageToastService) {
    super(loginService, httpClient, messageToastService);
  }

  public getAllTradingPlatform(): Observable<TradingPlatformPlan[]> {
    return <Observable<TradingPlatformPlan[]>>this.httpClient.get(`${AppSettings.API_ENDPOINT}${AppSettings.TRADING_PLATFORM_PLAN_KEY}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  public deleteEntity(idTradingPlatform: number): Observable<any> {
    return this.httpClient.delete(`${AppSettings.API_ENDPOINT}${AppSettings.TRADING_PLATFORM_PLAN_KEY}/${idTradingPlatform}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  update(tradingPlatformPlan: TradingPlatformPlan): Observable<TradingPlatformPlan> {
    return this.updateEntity(tradingPlatformPlan, tradingPlatformPlan.idTradingPlatformPlan, AppSettings.TRADING_PLATFORM_PLAN_KEY);
  }

}
