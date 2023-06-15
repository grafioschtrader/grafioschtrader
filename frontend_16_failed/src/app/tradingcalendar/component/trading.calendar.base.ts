import {AddRemoveDay} from '../service/trading.days.plus.service';
import {ActivePanelService} from '../../shared/mainmenubar/service/active.panel.service';
import {MessageToastService} from '../../shared/message/message.toast.service';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {AppSettings} from '../../shared/app.settings';
import {RangeSelectDays} from '../../fullyearcalendar/Interface/range.select.days';
import {HelpIds} from '../../shared/help/help.ids';
import * as moment from 'moment';
import {CalendarNavigation} from './calendar.navigation';
import {TradingDaysWithDateBoundaries} from '../model/trading.days.with.date.boundaries';

/**
 * Base class for trading calendar
 */
export abstract class TradingCalendarBase extends CalendarNavigation {

  constructor(translateService: TranslateService,
              gps: GlobalparameterService,
              markExistingColor: string[],
              activePanelService: ActivePanelService,
              protected messageToastService: MessageToastService,
              public titleKey?: string) {
    super(translateService, gps, activePanelService, markExistingColor);
  }

  abstract hasRightsToModify(): boolean;

  abstract saveData(convertedAddRemoveDays: AddRemoveDay[]): void;

  getHelpContextId(): HelpIds {
    return HelpIds.HELP_TRADING_CALENDAR;
  }

  onDaySelect(day: Date): void {
    if (this.hasRightsToModify()) {
      this.addRemoveOnOffDay(day);
    }
  }

  onRangeSelect(range: RangeSelectDays, ranges: RangeSelectDays[]): void {
    if (this.hasRightsToModify()) {
      this.addRemoveOnOffDay(range.start);
    }
  }

  submit(): void {
    const convertedAddRemoveDays: AddRemoveDay[] = [];
    this.addRemoveDaysMap.forEach((value: boolean, key: number) =>
      convertedAddRemoveDays.push(new AddRemoveDay(moment(key).format(AppSettings.FORMAT_DATE_SHORT_NATIVE), value))
    );
    this.saveData((convertedAddRemoveDays));
  }

  protected setYearsBoundariesAfterRead(tradingDaysWithDateBoundaries: TradingDaysWithDateBoundaries,
                                        yearChange: boolean): void {
    if (!yearChange) {
      this.setYearsBoundaries(moment(tradingDaysWithDateBoundaries.oldestTradingCalendarDay).year(),
        moment(tradingDaysWithDateBoundaries.youngestTradingCalendarDay).year());
    }
  }
}
