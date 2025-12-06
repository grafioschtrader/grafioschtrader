import {Component, OnInit} from '@angular/core';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {AddRemoveDay, SaveTradingDays, TradingDaysPlusService} from '../service/trading.days.plus.service';
import {MenuItem} from 'primeng/api';
import {InfoLevelType} from '../../lib/message/info.leve.type';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {ActivePanelService} from '../../lib/mainmenubar/service/active.panel.service';
import {TranslateHelper} from '../../lib/helper/translate.helper';
import {TranslateService, TranslateModule} from '@ngx-translate/core';
import {TradingCalendarBase} from './trading.calendar.base';
import {TradingDaysWithDateBoundaries} from '../model/trading.days.with.date.boundaries';
import {AuditHelper} from '../../lib/helper/audit.helper';
import {CommonModule} from '@angular/common';
import {PanelModule} from 'primeng/panel';
import {SelectModule} from 'primeng/select';
import {FormsModule} from '@angular/forms';
import {ButtonModule} from 'primeng/button';
import {FullyearcalendarLibComponent} from '../../lib/fullyearcalendar/fullyearcalendar-lib.component';
import {ContextMenuModule} from 'primeng/contextmenu';

/**
 * Component for the  global trading calendar
 */
@Component({
    templateUrl: '../view/trading.calendar.html',
    standalone: true,
    imports: [CommonModule, TranslateModule, PanelModule, SelectModule, FormsModule, ButtonModule, FullyearcalendarLibComponent, ContextMenuModule]
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

  override getEditMenu(): MenuItem[] {
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


