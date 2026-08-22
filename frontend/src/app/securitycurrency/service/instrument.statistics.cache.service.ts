import { Injectable } from '@angular/core';
import { catchError, Observable, shareReplay, throwError } from 'rxjs';
import { SecurityService } from './security.service';
import { InstrumentStatisticsResult } from '../../entities/view/instrument.statistics.result';

/**
 * Memoises the return and statistical data of an instrument for the lifetime of the component that provides this
 * service. Calculating these statistics is expensive on the server, so a view which offers the data on demand — for
 * example the collapsed statistics panel of the price feed watchlist — should not repeat the request every time the
 * user opens the panel again.
 *
 * <p>
 * This service is deliberately declared <b>without</b> {@code providedIn}. It must be listed in the {@code providers}
 * of the component whose lifetime defines the caching period; the cache is discarded together with that component.
 * {@code WatchlistTabMenuComponent} does so, which keeps the statistics for as long as the user stays within the
 * watchlist area, across watchlist and tab changes.
 * </p>
 */
@Injectable()
export class InstrumentStatisticsCacheService {
  /** Shared responses per instrument and period, keyed by {@link #getKey}. */
  private readonly cache = new Map<string, Observable<InstrumentStatisticsResult>>();

  constructor(private securityService: SecurityService) {}

  /**
   * Returns the statistics of an instrument, from the cache when they were requested before with the same period.
   * The first subscriber triggers the REST call, every later subscriber receives the replayed result. A failed
   * request is removed from the cache so that the next attempt reaches the server again.
   *
   * @param idSecuritycurrency the security or currency pair to analyse
   * @param dateFrom optional start of the evaluation period, the full history is used when omitted
   * @param dateTo optional end of the evaluation period, the full history is used when omitted
   * @returns the shared observable of the statistics result
   */
  getStatistics(
    idSecuritycurrency: number,
    dateFrom?: Date | string,
    dateTo?: Date | string
  ): Observable<InstrumentStatisticsResult> {
    const key = this.getKey(idSecuritycurrency, dateFrom, dateTo);
    let statistics = this.cache.get(key);
    if (!statistics) {
      statistics = this.securityService.getSecurityStatisticsReturnResult(idSecuritycurrency, dateFrom, dateTo).pipe(
        catchError((error) => {
          this.cache.delete(key);
          return throwError(() => error);
        }),
        shareReplay({ bufferSize: 1, refCount: false })
      );
      this.cache.set(key, statistics);
    }
    return statistics;
  }

  /**
   * Builds the cache key from the instrument and the evaluation period, since the same instrument yields different
   * statistics for different periods.
   *
   * @param idSecuritycurrency the security or currency pair to analyse
   * @param dateFrom optional start of the evaluation period
   * @param dateTo optional end of the evaluation period
   * @returns the key under which the response is cached
   */
  private getKey(idSecuritycurrency: number, dateFrom?: Date | string, dateTo?: Date | string): string {
    return `${idSecuritycurrency}|${dateFrom ?? ''}|${dateTo ?? ''}`;
  }
}
