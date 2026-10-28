import { inject } from '@angular/core';
import { AddRemoveDay } from '../service/trading.days.plus.service';
import { MessageToastService } from '../../lib/message/message.toast.service';
import { RangeSelectDays } from '../../lib/fullyearcalendar/Interface/range.select.days';
import { HelpIds } from '../../lib/help/help.ids';
import moment from 'moment';
import { CalendarNavigation } from './calendar.navigation';
import { TradingDaysWithDateBoundaries } from '../model/trading.days.with.date.boundaries';
import { BaseSettings } from '../../lib/base.settings';

/**
 * Base class for trading calendar
 */
export abstract class TradingCalendarBase extends CalendarNavigation {
  protected messageToastService: MessageToastService = inject(MessageToastService);

  protected constructor(
    markExistingColor: string[],
    public titleKey?: string
  ) {
    super(markExistingColor);
  }

  abstract hasRightsToModify(): boolean;

  abstract saveData(convertedAddRemoveDays: AddRemoveDay[]): void;

  getHelpContextId(): string {
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
    this.addRemoveDaysMap().forEach((value: boolean, key: number) =>
      convertedAddRemoveDays.push(new AddRemoveDay(moment(key).format(BaseSettings.FORMAT_DATE_SHORT_NATIVE), value))
    );
    this.saveData(convertedAddRemoveDays);
  }

  protected setYearsBoundariesAfterRead(
    tradingDaysWithDateBoundaries: TradingDaysWithDateBoundaries,
    yearChange: boolean
  ): void {
    if (!yearChange) {
      this.setYearsBoundaries(
        moment(tradingDaysWithDateBoundaries.oldestTradingCalendarDay).year(),
        moment(tradingDaysWithDateBoundaries.youngestTradingCalendarDay).year()
      );
    }
  }
}
