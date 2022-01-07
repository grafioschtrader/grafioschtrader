import {Component, OnInit} from '@angular/core';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {AddRemoveDay, SaveTradingDays, TradingDaysPlusService} from '../service/trading.days.plus.service';
import {MenuItem} from 'primeng/api';
import {InfoLevelType} from '../../shared/message/info.leve.type';
import {MessageToastService} from '../../shared/message/message.toast.service';
import {ActivePanelService} from '../../shared/mainmenubar/service/active.panel.service';
import {TranslateHelper} from '../../shared/helper/translate.helper';
import {TranslateService} from '@ngx-translate/core';
import {TradingCalendarBase} from './trading.calendar.base';
import {TradingDaysWithDateBoundaries} from '../model/trading.days.with.date.boundaries';
import {AuditHelper} from '../../shared/helper/audit.helper';

/**
 * Component for the  global trading calendar
 */
@Component({
  templateUrl: '../view/trading.calendar.html'
})
export class TradingCalendarGlobalComponent extends TradingCalendarBase implements OnInit {

  public static readonly GLOBAL_TRADING_DAYS_COLOR = 'lime';
  readonly DATE_ATTRIBUTE = 'tradingDate';

  constructor(private tradingDaysPlusService: TradingDaysPlusService,
              translateService: TranslateService,
              gps: GlobalparameterService,
              activePanelService: ActivePanelService,
              messageToastService: MessageToastService) {
    super(translateService, gps, [TradingCalendarGlobalComponent.GLOBAL_TRADING_DAYS_COLOR],
      activePanelService, messageToastService, 'TRADING_CALENDAR_GLOBAL');
  }

  ngOnInit(): void {
    this.readData(false);
  }

  readData(yearChange: boolean): void {
    this.tradingDaysPlusService.getTradingDaysByYear(this.yearCalendarData.year).subscribe(
      (tradingDaysWithDateBoundaries: TradingDaysWithDateBoundaries) => {
        this.clearDaysAndTransformReadData(tradingDaysWithDateBoundaries.dates);
        this.setYearsBoundariesAfterRead(tradingDaysWithDateBoundaries, yearChange);
      });
  }

  saveData(convertedAddRemoveDays: AddRemoveDay[]): void {
    this.tradingDaysPlusService.save(new SaveTradingDays(this.yearCalendarData.year, convertedAddRemoveDays)).subscribe(
      (tradingDaysWithDateBoundaries: TradingDaysWithDateBoundaries) => {
        this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'TRADING_CALENDAR_GLOBAL_SAVE',
          {year: this.yearCalendarData.year});
        this.clearDaysAndTransformReadData(tradingDaysWithDateBoundaries.dates);
      });
  }

  getEditMenu(): MenuItem[] {
    const menuItems: MenuItem[] = [];
    if (this.hasRightsToModify()) {
      menuItems.push({label: '_TRADING_CALENDAR_MARK_YEAR', command: (e) => this.markWorkingDaysOfFullYear()});
      TranslateHelper.translateMenuItems(menuItems, this.translateService);
    }
    this.contextMenuItems = menuItems;
    return menuItems;
  }

  public hasRightsToModify(): boolean {
    return AuditHelper.hasAdminRole(this.gps);
  }

  private markWorkingDaysOfFullYear(): void {
    const startDate = new Date(this.yearCalendarData.year, 0, 1);
    startDate.setHours(0, 0, 0, 0);
    const toDate = new Date(this.yearCalendarData.year, 11, 31);
    toDate.setHours(0, 0, 0, 0);
    while (startDate <= toDate) {
      const weekDay = startDate.getDay();
      if (weekDay !== 0 && weekDay !== 6) {
        if (!this.originalDaysMark.has(startDate.getTime()) && !this.addRemoveDaysMap.has(startDate.getTime())) {
          this.addRemoveOnOffDay(new Date(startDate));
        }
      }
      startDate.setDate(startDate.getDate() + 1);
    }
  }

}


