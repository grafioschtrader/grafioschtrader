import { Service } from '@angular/core';
import { Observable } from 'rxjs';
import { AppSettings } from '../../shared/app.settings';
import { catchError } from 'rxjs/operators';
import type { TradingDaysPlus } from '../../entities/trading.days.plus';
import { AuthServiceWithLogout } from '../../lib/login/service/base.auth.service.with.logout';
import { TradingDaysWithDateBoundaries } from '../model/trading.days.with.date.boundaries';
import { BaseSettings } from '../../lib/base.settings';

@Service()
export class TradingDaysPlusService extends AuthServiceWithLogout<TradingDaysPlus> {
  getTradingDaysByYear(year: number): Observable<TradingDaysWithDateBoundaries> {
    return this.httpClient
      .get<TradingDaysWithDateBoundaries>(
        `${BaseSettings.API_ENDPOINT}` + `${AppSettings.TRADING_DAYS_PLUS_KEY}/${year}`,
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  save(saveTradingDays: SaveTradingDays): Observable<TradingDaysWithDateBoundaries> {
    return this.httpClient
      .put<TradingDaysWithDateBoundaries>(
        `${BaseSettings.API_ENDPOINT}` + `${AppSettings.TRADING_DAYS_PLUS_KEY}`,
        saveTradingDays,
        {
          headers: this.prepareHeaders()
        }
      )
      .pipe(catchError(this.handleError.bind(this)));
  }
}

export class SaveTradingDays {
  constructor(
    private year: number,
    private addRemoveDays: AddRemoveDay[]
  ) {}
}

export class AddRemoveDay {
  constructor(
    private date: string,
    private add: boolean
  ) {}
}
