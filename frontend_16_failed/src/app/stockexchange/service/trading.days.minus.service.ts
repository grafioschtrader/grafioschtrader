import {Injectable} from '@angular/core';
import {Observable} from 'rxjs';
import {TradingDaysMinus} from '../../entities/trading.days.minus';
import {AppSettings} from '../../shared/app.settings';
import {catchError} from 'rxjs/operators';
import {AuthServiceWithLogout} from '../../shared/login/service/base.auth.service.with.logout';
import {LoginService} from '../../shared/login/service/log-in.service';
import {HttpClient} from '@angular/common/http';
import {MessageToastService} from '../../shared/message/message.toast.service';
import {SaveTradingDays} from '../../tradingcalendar/service/trading.days.plus.service';
import {TradingDaysWithDateBoundaries} from '../../tradingcalendar/model/trading.days.with.date.boundaries';

@Injectable()
export class TradingDaysMinusService extends AuthServiceWithLogout<TradingDaysMinus> {

  constructor(loginService: LoginService, httpClient: HttpClient, messageToastService: MessageToastService) {
    super(loginService, httpClient, messageToastService);
  }

  getTradingDaysMinusByStockexchangeAndYear(idStockexchange: number, year: number): Observable<TradingDaysWithDateBoundaries> {
    return <Observable<TradingDaysWithDateBoundaries>>this.httpClient.get(`${AppSettings.API_ENDPOINT}`
      + `${AppSettings.TRADING_DAYS_MINUS_KEY}/${idStockexchange}/${year}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  save(idStockexchange: number, saveTradingDays: SaveTradingDays): Observable<TradingDaysWithDateBoundaries> {
    return <Observable<TradingDaysWithDateBoundaries>>this.httpClient.put(`${AppSettings.API_ENDPOINT}`
      + `${AppSettings.TRADING_DAYS_MINUS_KEY}/${idStockexchange}/`, saveTradingDays,
      {headers: this.prepareHeaders()}).pipe(catchError(this.handleError.bind(this)));
  }

  copyAllTradingDaysMinusToOtherStockexchange(copyTradingDaysFromSourceToTarget: CopyTradingDaysFromSourceToTarget):
    Observable<TradingDaysWithDateBoundaries> {
    return <Observable<TradingDaysWithDateBoundaries>>this.httpClient.put(`${AppSettings.API_ENDPOINT}`
      + `${AppSettings.TRADING_DAYS_MINUS_KEY}/copytradingdays`, copyTradingDaysFromSourceToTarget,
      {headers: this.prepareHeaders()}).pipe(catchError(this.handleError.bind(this)));
  }
}

export class CopyTradingDaysFromSourceToTarget {
  sourceIdStockexchange: number;

  constructor(public targetIdStockexchange: number, public returnOrCopyYear: number, public fullCopy: boolean) {
  }
}
