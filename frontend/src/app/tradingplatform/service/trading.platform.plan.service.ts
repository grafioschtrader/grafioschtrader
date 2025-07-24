import {Injectable} from '@angular/core';
import {Observable} from 'rxjs';
import {AppSettings} from '../../shared/app.settings';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {DeleteService} from '../../lib/datashowbase/delete.service';
import {HttpClient} from '@angular/common/http';
import {AuthServiceWithLogout} from '../../lib/login/service/base.auth.service.with.logout';

import {TradingPlatformPlan} from '../../entities/tradingplatformplan';
import {ServiceEntityUpdate} from '../../lib/edit/service.entity.update';
import {catchError} from 'rxjs/operators';
import {LoginService} from '../../lib/login/service/log-in.service';
import {BaseSettings} from '../../lib/base.settings';


@Injectable()
export class TradingPlatformPlanService extends AuthServiceWithLogout<TradingPlatformPlan>
  implements ServiceEntityUpdate<TradingPlatformPlan>, DeleteService {

  constructor(loginService: LoginService, httpClient: HttpClient, messageToastService: MessageToastService) {
    super(loginService, httpClient, messageToastService);
  }

  public getAllTradingPlatform(): Observable<TradingPlatformPlan[]> {
    return <Observable<TradingPlatformPlan[]>>this.httpClient.get(`${BaseSettings.API_ENDPOINT}${AppSettings.TRADING_PLATFORM_PLAN_KEY}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  public deleteEntity(idTradingPlatform: number): Observable<any> {
    return this.httpClient.delete(`${BaseSettings.API_ENDPOINT}${AppSettings.TRADING_PLATFORM_PLAN_KEY}/${idTradingPlatform}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  update(tradingPlatformPlan: TradingPlatformPlan): Observable<TradingPlatformPlan> {
    return this.updateEntity(tradingPlatformPlan, tradingPlatformPlan.idTradingPlatformPlan, AppSettings.TRADING_PLATFORM_PLAN_KEY);
  }

}
