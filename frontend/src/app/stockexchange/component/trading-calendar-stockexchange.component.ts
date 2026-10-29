import { Component, OnInit, input, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TradingCalendarBase } from '../../tradingcalendar/component/trading.calendar.base';
import {
  AddRemoveDay,
  SaveTradingDays,
  TradingDaysPlusService
} from '../../tradingcalendar/service/trading.days.plus.service';
import { TranslateModule } from '@ngx-translate/core';
import { CopyTradingDaysFromSourceToTarget, TradingDaysMinusService } from '../service/trading.days.minus.service';
import { forkJoin, Observable, Subscription } from 'rxjs';
import { RangeSelectDays } from '../../lib/fullyearcalendar/Interface/range.select.days';
import { TradingCalendarGlobalComponent } from '../../tradingcalendar/component/trading.calendar.global.component';
import { InfoLevelType } from '../../lib/message/info.leve.type';
import { TradingDaysWithDateBoundaries } from '../../tradingcalendar/model/trading.days.with.date.boundaries';
import { DialogService } from '@openng/optimus-ui/dynamicdialog';
import { MenuItem } from '@openng/optimus-ui/api';
import { TradingCalendarOtherExchangeDynamicDialogComponent } from './trading-calendar-other-exchange-dynamic-dialog.component';
import { ValueKeyHtmlSelectOptions } from '../../lib/dynamic-form/models/value.key.html.select.options';
import moment from 'moment';
import { AuditHelper } from '../../lib/helper/audit.helper';
import { Stockexchange } from '../../entities/stockexchange';
import { CreateType } from '../../entities/dividend.split';
import { BaseSettings } from '../../lib/base.settings';
import { PanelModule } from '@openng/optimus-ui/panel';
import { SelectModule } from '@openng/optimus-ui/select';
import { FullyearcalendarLibComponent } from '../../lib/fullyearcalendar/fullyearcalendar-lib.component';
import { ButtonModule } from '@openng/optimus-ui/button';
import { ContextMenuModule } from '@openng/optimus-ui/contextmenu';

import { FormsModule } from '@angular/forms';

/**
 * The calendar component for the stock exchange.
 */
@Component({
  selector: 'trading-calendar-stockexchange',
  templateUrl: '../../tradingcalendar/view/trading.calendar.html',
  imports: [
    PanelModule,
    SelectModule,
    FullyearcalendarLibComponent,
    ButtonModule,
    ContextMenuModule,
    TranslateModule,
    FormsModule
  ]
})
export class TradingCalendarStockexchangeComponent extends TradingCalendarBase implements OnInit {
  private tradingDaysMinusService = inject(TradingDaysMinusService);
  private tradingDaysPlusService = inject(TradingDaysPlusService);
  private dialogService = inject(DialogService);

  static readonly SYSTEM_CREATED_COLOR = 'red';
  static readonly USER_CREATED_COLOR = 'blue';
  readonly stockexchange = input<Stockexchange>(undefined);
  readonly sourceCopyStockexchanges = input<ValueKeyHtmlSelectOptions[]>(undefined);

  readonly DATE_ATTRIBUTE = 'tradingDateMinus';
  readonly COPY_FULL_TITLE_KEY = 'TRADING_CALENDAR_FROM_OTHER_EXCHANGE_FULL';
  readonly COPY_YEAR_TITLE_KEY = 'TRADING_CALENDAR_FROM_OTHER_EXCHANGE_YEAR';

  consumedDay: number;
  /**
   * Contains the difference between every day of year but minus saturday and sunday and not closed global trading days.
   */
  globalTradingMinusDaysSet: Set<number> = new Set();
  tradingDaysPlusList: Date[];

  oldestYear: number;
  youngestYear: number;

  private dateCreateTypes: { [key: number]: CreateType };
  private readDataSub?: Subscription;

  constructor() {
    super([
      TradingCalendarStockexchangeComponent.USER_CREATED_COLOR,
      TradingCalendarStockexchangeComponent.SYSTEM_CREATED_COLOR
    ]);
  }

  ngOnInit(): void {
    this.readData(false);
  }

  readData(yearChange: boolean): void {
    this.readDataSub?.unsubscribe();
    const plusObservable: Observable<TradingDaysWithDateBoundaries> = this.tradingDaysPlusService.getTradingDaysByYear(
      this.yearCalendarData().year
    );
    const minusObservable: Observable<TradingDaysWithDateBoundaries> =
      this.tradingDaysMinusService.getTradingDaysMinusByStockexchangeAndYear(
        this.stockexchange().idStockexchange,
        this.yearCalendarData().year
      );

    this.readDataSub = forkJoin([plusObservable, minusObservable])
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((data: [TradingDaysWithDateBoundaries, TradingDaysWithDateBoundaries]) => {
        this.oldestYear = moment(data[0].oldestTradingCalendarDay).year();
        this.youngestYear = moment(data[0].youngestTradingCalendarDay).year();
        this.setYearsBoundariesAfterRead(data[0], yearChange);
        this.tradingDaysPlusList = data[0].dates;
        this.markDays(data[1].dates, data[1].createTypes);
      });
  }

  markGlobalTradingDay(): void {
    const dates = this.tradingDaysPlusList.map((tradingDaysPlus, i): RangeSelectDays => {
      const date = this.getZeroTimeDate(tradingDaysPlus);
      this.globalTradingMinusDaysSet.delete(date.getTime());
      return {
        id: i,
        start: date,
        end: date,
        color: TradingCalendarGlobalComponent.GLOBAL_TRADING_DAYS_COLOR,
        select: (range: RangeSelectDays, ranges: RangeSelectDays[]) => this.onDayPlusSelect(range, ranges)
      };
    });
    const disabledDays = Array.from(this.globalTradingMinusDaysSet, (time) => ({ date: new Date(time) }));
    this.yearCalendarData.update((data) => ({ ...data, dates, disabledDays }));
  }

  /**
   * A stock exchange trading day was clicked.
   */
  override onRangeSelect(range: RangeSelectDays, ranges: RangeSelectDays[]): void {
    if (ranges.length === 2 && this.hasRightsToModify()) {
      this.addRemoveOnOffDay(range.start);
    }
  }

  /**
   * A global trading day was clicked.
   */
  onDayPlusSelect(range: RangeSelectDays, ranges: RangeSelectDays[]) {
    if (ranges.length === 1 && this.hasRightsToModify()) {
      // React only when clicked day has a global trading day range.
      this.addRemoveOnOffDay(range.start);
    }
  }

  saveData(convertedAddRemoveDays: AddRemoveDay[]): void {
    const year = this.selectedYear();
    this.tradingDaysMinusService
      .save(this.stockexchange().idStockexchange, new SaveTradingDays(year, convertedAddRemoveDays))
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((tradingDaysMinus: TradingDaysWithDateBoundaries) => {
        this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'TRADING_CALENDAR_GLOBAL_SAVE', {
          year
        });
        if (this.selectedYear() === year) {
          this.markDays(tradingDaysMinus.dates, tradingDaysMinus.createTypes);
        }
      });
  }

  override getEditMenu(): MenuItem[] {
    let menuItems: MenuItem[];
    if (this.hasRightsToModify()) {
      menuItems = [
        {
          label: this.COPY_FULL_TITLE_KEY + BaseSettings.DIALOG_MENU_SUFFIX,
          command: (e) => this.copyCalendarFromOtherExchange(true)
        },
        {
          label: this.COPY_YEAR_TITLE_KEY + BaseSettings.DIALOG_MENU_SUFFIX,
          command: (e) => this.copyCalendarFromOtherExchange(false)
        }
      ];
    }
    return menuItems;
  }

  copyCalendarFromOtherExchange(fullCopy: boolean): void {
    this.translateService
      .get(fullCopy ? this.COPY_FULL_TITLE_KEY : this.COPY_YEAR_TITLE_KEY)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((msg) => {
        const ref = this.dialogService.open(TradingCalendarOtherExchangeDynamicDialogComponent, {
          data: {
            copyTradingDaysFromSourceToTarget: new CopyTradingDaysFromSourceToTarget(
              this.stockexchange().idStockexchange,
              this.yearCalendarData().year,
              fullCopy
            ),
            sourceCopyStockexchanges: this.sourceCopyStockexchanges(),
            calendarRange: `${this.oldestYear} - ${this.youngestYear}`
          },
          modal: true,
          closable: true,
          closeOnEscape: true,
          header: msg,
          width: '400px'
        });

        ref.onClose
          .pipe(takeUntilDestroyed(this.destroyRef))
          .subscribe((tradingDaysWithDateBoundaries: TradingDaysWithDateBoundaries) => {
            if (tradingDaysWithDateBoundaries) {
              this.readData(false);
            }
          });
      });
  }

  public hasRightsToModify(): boolean {
    return AuditHelper.hasRightsForEditingOrDeleteEntity(this.gps, this.stockexchange());
  }

  protected override getExistingColor(date: Date): string {
    return this.dateCreateTypes[date.getTime()] === CreateType.ADD_MODIFIED_USER
      ? TradingCalendarStockexchangeComponent.USER_CREATED_COLOR
      : TradingCalendarStockexchangeComponent.SYSTEM_CREATED_COLOR;
  }

  private markDays(tradingDaysMinus: Date[], createTypes: CreateType[]): void {
    this.dateCreateTypes = tradingDaysMinus.reduce<Record<number, CreateType>>((result, date, index) => {
      result[this.getZeroTimeDate(date).getTime()] = createTypes[index];
      return result;
    }, {});

    this.markWorkingDaysOfFullYear();
    this.markGlobalTradingDay();
    this.markOnOffSingleDays(tradingDaysMinus);
  }

  /**
   * Prepare mark every day of year but saturday and sunday
   */
  private markWorkingDaysOfFullYear(): void {
    this.globalTradingMinusDaysSet = new Set();
    const startDate = new Date(this.yearCalendarData().year, 0, 1);
    startDate.setHours(0, 0, 0, 0);
    const toDate = new Date(this.yearCalendarData().year, 11, 31);
    toDate.setHours(0, 0, 0, 0);
    while (startDate <= toDate) {
      const weekDay = startDate.getDay();
      if (weekDay !== 0 && weekDay !== 6) {
        this.globalTradingMinusDaysSet.add(startDate.getTime());
      }
      startDate.setDate(startDate.getDate() + 1);
    }
  }
}
