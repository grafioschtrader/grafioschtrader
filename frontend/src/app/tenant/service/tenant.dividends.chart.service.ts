import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable, Subject } from 'rxjs';
import { IdsAccounts } from '../model/ids.accounts';

/**
 * Couples the dividends table with its charts in the lower display area. The table publishes the account selection,
 * the year the user clicked and every change of the underlying transactions; the chart component listens and reloads
 * or switches its year. The account selection is kept as the latest value, so a chart opened later still receives it.
 */
@Injectable()
export class TenantDividendsChartService {
  private accounts = new BehaviorSubject<IdsAccounts>(null);
  private yearSelected = new BehaviorSubject<number>(null);
  private dataChanged = new Subject<void>();

  /** Latest account selection of the dividends table; null until the table has initialized. */
  readonly accounts$: Observable<IdsAccounts> = this.accounts.asObservable();

  /**
   * Year of the row the user last selected in the dividends table; null until a row was selected. Kept as the latest
   * value, so a chart opened after the selection still shows that year.
   */
  readonly yearSelected$: Observable<number> = this.yearSelected.asObservable();

  /** Emitted when a transaction shown in the dividends table was changed. */
  readonly dataChanged$: Observable<void> = this.dataChanged.asObservable();

  /**
   * Publishes the account selection of the dividends table.
   *
   * @param idsAccounts - The selected security and cash accounts
   */
  setAccounts(idsAccounts: IdsAccounts): void {
    this.accounts.next(idsAccounts);
  }

  /**
   * Publishes the year of the row selected in the dividends table.
   *
   * @param year - The selected year
   */
  selectYear(year: number): void {
    this.yearSelected.next(year);
  }

  /** Tells the charts that the data of the dividends table has changed. */
  notifyDataChanged(): void {
    this.dataChanged.next();
  }
}
