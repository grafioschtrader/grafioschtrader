import {Component, Input, OnInit} from '@angular/core';
import {TradingCalendarBase} from '../../tradingcalendar/component/trading.calendar.base';
import {
  AddRemoveDay,
  SaveTradingDays,
  TradingDaysPlusService
} from '../../tradingcalendar/service/trading.days.plus.service';
import {ActivePanelService} from '../../shared/mainmenubar/service/active.panel.service';
import {MessageToastService} from '../../shared/message/message.toast.service';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {CopyTradingDaysFromSourceToTarget, TradingDaysMinusService} from '../service/trading.days.minus.service';
import {combineLatest, Observable} from 'rxjs';
import {RangeSelectDays} from '../../fullyearcalendar/Interface/range.select.days';
import {TradingCalendarGlobalComponent} from '../../tradingcalendar/component/trading.calendar.global.component';
import {InfoLevelType} from '../../shared/message/info.leve.type';
import {TradingDaysWithDateBoundaries} from '../../tradingcalendar/model/trading.days.with.date.boundaries';
import {DialogService} from 'primeng/dynamicdialog';
import {MenuItem} from 'primeng/api';
import {TranslateHelper} from '../../shared/helper/translate.helper';
import {TradingCalendarOtherExchangeDynamicComponent} from './trading.calendar.other.exchange.dynamic.component';
import {ValueKeyHtmlSelectOptions} from '../../dynamic-form/models/value.key.html.select.options';
import * as moment from 'moment';
import {AuditHelper} from '../../shared/helper/audit.helper';
import {Stockexchange} from '../../entities/stockexchange';
import {AppSettings} from '../../shared/app.settings';
import {CreateType} from '../../entities/dividend.split';

/**
 * The calendar component for the stock exchange.
 */
@Component({
  selector: 'trading-calendar-stockexchange',
  templateUrl: '../../tradingcalendar/view/trading.calendar.html'
})
export class TradingCalendarStockexchangeComponent extends TradingCalendarBase implements OnInit {
  static readonly SYSTEM_CREATED_COLOR = 'red';
  static readonly USER_CREATED_COLOR = 'blue';
  @Input() stockexchange: Stockexchange;
  @Input() sourceCopyStockexchanges: ValueKeyHtmlSelectOptions[];


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

  constructor(private tradingDaysMinusService: TradingDaysMinusService,
              private tradingDaysPlusService: TradingDaysPlusService,
              private dialogService: DialogService,
              translateService: TranslateService,
              gps: GlobalparameterService,
              activePanelService: ActivePanelService,
              messageToastService: MessageToastService) {
    super(translateService, gps, [TradingCalendarStockexchangeComponent.USER_CREATED_COLOR,
      TradingCalendarStockexchangeComponent.SYSTEM_CREATED_COLOR], activePanelService, messageToastService);
  }

  ngOnInit(): void {
    this.readData(false);
  }

  readData(yearChange: boolean): void {
    const plusObservable: Observable<TradingDaysWithDateBoundaries>
      = this.tradingDaysPlusService.getTradingDaysByYear(this.yearCalendarData.year);
    const minusObservable: Observable<TradingDaysWithDateBoundaries>
      = this.tradingDaysMinusService.getTradingDaysMinusByStockexchangeAndYear(
      this.stockexchange.idStockexchange, this.yearCalendarData.year);

    combineLatest([plusObservable, minusObservable]).subscribe((data: [TradingDaysWithDateBoundaries,
      TradingDaysWithDateBoundaries]) => {
      this.oldestYear = moment(data[0].oldestTradingCalendarDay).year();
      this.youngestYear = moment(data[0].youngestTradingCalendarDay).year();
      this.setYearsBoundariesAfterRead(data[0], yearChange);
      this.tradingDaysPlusList = data[0].dates;
      this.markDays(data[1].dates, data[1].createTypes);
    });
  }

  markGlobalTradingDay(): void {
    this.yearCalendarData.dates = [];
    this.tradingDaysPlusList.forEach((tradingDaysPlus, i) => {
      const date = new Date(tradingDaysPlus);
      date.setHours(0, 0, 0, 0);
      this.globalTradingMinusDaysSet.delete(date.getTime());
      this.yearCalendarData.dates.push({
        id: i, start: date, end: date,
        color: TradingCalendarGlobalComponent.GLOBAL_TRADING_DAYS_COLOR,
        select: (range: RangeSelectDays, ranges: RangeSelectDays[]) => this.onDayPlusSelect(range, ranges)
      });
    });
    this.yearCalendarData.disabledDays = [];
    this.globalTradingMinusDaysSet.forEach(time => this.yearCalendarData.disabledDays.push({date: new Date(time)}));
  }

  /**
   * A stock exchange trading day was clicked.
   */
  onRangeSelect(range: RangeSelectDays, ranges: RangeSelectDays[]): void {
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
    this.tradingDaysMinusService.save(this.stockexchange.idStockexchange, new SaveTradingDays(this.yearCalendarData.year,
      convertedAddRemoveDays)).subscribe(
      (tradingDaysMinus: TradingDaysWithDateBoundaries) => {
        this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'TRADING_CALENDAR_GLOBAL_SAVE',
          {year: this.yearCalendarData.year});
        this.markDays(tradingDaysMinus.dates, tradingDaysMinus.createTypes);
      });
  }

  getEditMenu(): MenuItem[] {
    let menuItems: MenuItem[];
    if (this.hasRightsToModify()) {
      menuItems = [
        {
          label: this.COPY_FULL_TITLE_KEY + AppSettings.DIALOG_MENU_SUFFIX,
          command: (e) => this.copyCalendarFromOtherExchange(true)
        },
        {
          label: this.COPY_YEAR_TITLE_KEY + AppSettings.DIALOG_MENU_SUFFIX,
          command: (e) => this.copyCalendarFromOtherExchange(false)
        }
      ];
      TranslateHelper.translateMenuItems(menuItems, this.translateService);
    }
    this.contextMenuItems = menuItems;
    return menuItems;
  }

  copyCalendarFromOtherExchange(fullCopy: boolean): void {
    this.translateService.get(fullCopy ? this.COPY_FULL_TITLE_KEY : this.COPY_YEAR_TITLE_KEY).subscribe(msg => {
      const ref = this.dialogService.open(TradingCalendarOtherExchangeDynamicComponent, {
        data: {
          copyTradingDaysFromSourceToTarget: new CopyTradingDaysFromSourceToTarget(this.stockexchange.idStockexchange,
            this.yearCalendarData.year, fullCopy),
          sourceCopyStockexchanges: this.sourceCopyStockexchanges,
          calendarRange: `${this.oldestYear} - ${this.youngestYear}`
        },
        header: msg, width: '400px'
      });

      ref.onClose.subscribe((tradingDaysWithDateBoundaries: TradingDaysWithDateBoundaries) => {
        if (tradingDaysWithDateBoundaries) {
          this.readData(false);
        }
      });
    });
  }

  public hasRightsToModify(): boolean {
    return AuditHelper.hasRightsForEditingOrDeleteEntity(this.gps, this.stockexchange);
  }

  protected getExistingColor(date: Date): string {
    return this.dateCreateTypes[date.getTime()] === CreateType.ADD_MODIFIED_USER ?
      TradingCalendarStockexchangeComponent.USER_CREATED_COLOR : TradingCalendarStockexchangeComponent.SYSTEM_CREATED_COLOR;
  }

  private markDays(tradingDaysMinus: Date[], createTypes: CreateType[]): void {
    this.dateCreateTypes = tradingDaysMinus.reduce((result, date, index) => {
      result[this.getZeroTimeDate(date).getTime()] = createTypes[index];
      return result;
    }, {});

    this.markWorkingDaysOfFullYear();
    this.markGlobalTradingDay();
    this.markOnOffSingleDays(tradingDaysMinus);
  }

  /**
   * Prepare mark ever day of year but saturday and sunday
   */
  private markWorkingDaysOfFullYear(): void {
    const startDate = new Date(this.yearCalendarData.year, 0, 1);
    startDate.setHours(0, 0, 0, 0);
    const toDate = new Date(this.yearCalendarData.year, 11, 31);
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
