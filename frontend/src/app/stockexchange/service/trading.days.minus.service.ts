import { Service } from '@angular/core';
import { Observable } from 'rxjs';
import type { TradingDaysMinus } from '../../entities/trading.days.minus';
import { AppSettings } from '../../shared/app.settings';
import { catchError } from 'rxjs/operators';
import { AuthServiceWithLogout } from '../../lib/login/service/base.auth.service.with.logout';
import { SaveTradingDays } from '../../tradingcalendar/service/trading.days.plus.service';
import { TradingDaysWithDateBoundaries } from '../../tradingcalendar/model/trading.days.with.date.boundaries';
import { BaseSettings } from '../../lib/base.settings';
import { requireInitialized } from '../../lib/helper/required-state';

@Service()
export class TradingDaysMinusService extends AuthServiceWithLogout<TradingDaysMinus> {
  getTradingDaysMinusByStockexchangeAndYear(
    idStockexchange: number,
    year: number
  ): Observable<TradingDaysWithDateBoundaries> {
    return this.httpClient
      .get<TradingDaysWithDateBoundaries>(
        `${BaseSettings.API_ENDPOINT}` + `${AppSettings.TRADING_DAYS_MINUS_KEY}/${idStockexchange}/${year}`,
        this.getHeaders()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  save(idStockexchange: number, saveTradingDays: SaveTradingDays): Observable<TradingDaysWithDateBoundaries> {
    return this.httpClient
      .put<TradingDaysWithDateBoundaries>(
        `${BaseSettings.API_ENDPOINT}` + `${AppSettings.TRADING_DAYS_MINUS_KEY}/${idStockexchange}`,
        saveTradingDays,
        { headers: this.prepareHeaders() }
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  copyAllTradingDaysMinusToOtherStockexchange(
    copyTradingDaysFromSourceToTarget: CopyTradingDaysFromSourceToTarget
  ): Observable<TradingDaysWithDateBoundaries> {
    requireInitialized(copyTradingDaysFromSourceToTarget.sourceIdStockexchange, 'Trading-calendar copy source');
    return this.httpClient
      .put<TradingDaysWithDateBoundaries>(
        `${BaseSettings.API_ENDPOINT}` + `${AppSettings.TRADING_DAYS_MINUS_KEY}/copytradingdays`,
        copyTradingDaysFromSourceToTarget,
        { headers: this.prepareHeaders() }
      )
      .pipe(catchError(this.handleError.bind(this)));
  }
}

export class CopyTradingDaysFromSourceToTarget {
  sourceIdStockexchange?: number;

  constructor(
    public targetIdStockexchange: number,
    public returnOrCopyYear: number,
    public fullCopy: boolean
  ) {}
}
