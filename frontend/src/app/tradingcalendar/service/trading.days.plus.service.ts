import {Injectable} from '@angular/core';
import {Observable} from 'rxjs';
import {AppSettings} from '../../shared/app.settings';
import {catchError} from 'rxjs/operators';
import {TradingDaysPlus} from '../../entities/trading.days.plus';
import {AuthServiceWithLogout} from '../../lib/login/service/base.auth.service.with.logout';
import {LoginService} from '../../lib/login/service/log-in.service';
import {HttpClient} from '@angular/common/http';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {TradingDaysWithDateBoundaries} from '../model/trading.days.with.date.boundaries';
import {BaseSettings} from '../../lib/base.settings';

@Injectable()
export class TradingDaysPlusService extends AuthServiceWithLogout<TradingDaysPlus> {

  constructor(loginService: LoginService, httpClient: HttpClient, messageToastService: MessageToastService) {
    super(loginService, httpClient, messageToastService);
  }

  getTradingDaysByYear(year: number): Observable<TradingDaysWithDateBoundaries> {
    return <Observable<TradingDaysWithDateBoundaries>>this.httpClient.get(`${BaseSettings.API_ENDPOINT}`
      + `${AppSettings.TRADING_DAYS_PLUS_KEY}/${year}`,
      this.getHeaders()).pipe(catchError(this.handleError.bind(this)));
  }

  save(saveTradingDays: SaveTradingDays): Observable<TradingDaysWithDateBoundaries> {
    return <Observable<TradingDaysWithDateBoundaries>>this.httpClient.put(`${BaseSettings.API_ENDPOINT}`
      + `${AppSettings.TRADING_DAYS_PLUS_KEY}`, saveTradingDays, {headers: this.prepareHeaders()}
    ).pipe(catchError(this.handleError.bind(this)));
  }

}

export class SaveTradingDays {
  constructor(private year: number, private addRemoveDays: AddRemoveDay[]) {
  }
}

export class AddRemoveDay {
  constructor(private date: string, private add: boolean) {
  }
}
