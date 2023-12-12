import {LocaleSettings} from '../../fullyearcalendar/Interface/locale.settings';
import {YearCalendarData} from '../../fullyearcalendar/Interface/year.calendar.data';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {Helper} from '../../helper/helper';
import {MenuItem, SelectItem} from 'primeng/api';
import {DayOfWeek} from '../../fullyearcalendar/model/day.of.week';
import {RangeSelectDays} from '../../fullyearcalendar/Interface/range.select.days';
import {TranslateHelper} from '../../shared/helper/translate.helper';
import {TranslateService} from '@ngx-translate/core';
import {ActivePanelService} from '../../shared/mainmenubar/service/active.panel.service';
import {IGlobalMenuAttach} from '../../shared/mainmenubar/component/iglobal.menu.attach';
import {HelpIds} from '../../shared/help/help.ids';

export abstract class CalendarNavigation implements IGlobalMenuAttach {
  readonly NEW_DATE_COLOR = 'yellow';

  locale: LocaleSettings;
  yearCalendarData: YearCalendarData;
  underline = false;
  possibleYears: SelectItem[] = [];
  selectedYear: number;

  addRemoveDaysMap: Map<number, boolean> = new Map();
  originalDaysMark: Set<number> = new Set();

  // Used when an component click event is consumed by the child and the parent should ignored it.
  readonly consumedGT = 'consumedGT';
  contextMenuItems: MenuItem[];

  constructor(public translateService: TranslateService,
              protected gps: GlobalparameterService,
              protected activePanelService: ActivePanelService,
              protected markExistingColors: string[]) {

    const language: string = gps.getUserLang();

    this.locale = {
      dayNamesMin: Helper.CALENDAR_LANG[language].dayNamesMin,
      monthNames: Helper.CALENDAR_LANG[language].monthNames
    };
    this.yearCalendarData = {year: new Date().getFullYear(), disableWeekDays: [DayOfWeek.SATURDAY, DayOfWeek.SUNDAY]};
  }

  abstract getHelpContextId(): HelpIds;

  abstract readData(yearChange: boolean): void;

  abstract onRangeSelect(range: RangeSelectDays, ranges: RangeSelectDays[]): void;

  setYearsBoundaries(fromYear: number, toYear: number) {
    for (let i = fromYear; i <= toYear; i++) {
      this.possibleYears.push({label: i.toString(), value: i});
    }
    this.selectedYear = this.yearCalendarData.year;

  }

  containsYear(year: number): boolean {
    return this.possibleYears.find(selectItem => selectItem.value === year) != null;
  }

  yearChanged(event): void {
    this.yearCalendarData.year = event.value;
    this.readData(true);
  }

  addMinusYear(addMinusYear: number): void {
    this.yearCalendarData.year += addMinusYear;
    this.selectedYear = this.yearCalendarData.year;
    this.readData(true);
  }

  clearDaysAndTransformReadData(dayToMarkList: Date[]): void {
    this.yearCalendarData.dates = [];
    this.markOnOffSingleDays(dayToMarkList);
  }


  isActivated(): boolean {
    return this.activePanelService.isActivated(this);
  }

  onComponentClick(event): void {
    event[this.consumedGT] = true;
    this.resetMenu();
  }

  hideContextMenu(): void {
  }

  callMeDeactivate(): void {
  }

  onRightClick(event): void {
    this.resetMenu();
  }

  addRemoveDays(days: Date[]): void {
    days.forEach(date => this.addRemoveOnOffDay(date));
  }

  /**
   * Add or remove a day from marked days.
   *
   * @param date Date to be added or removed
   */
  addRemoveOnOffDay(date: Date): void {
    let i = 0;

    for (; i < this.yearCalendarData.dates.length; i++) {
      if (this.yearCalendarData.dates[i].start === date && (this.markExistingColors.includes(this.yearCalendarData.dates[i].color)
        || this.yearCalendarData.dates[i].color === this.NEW_DATE_COLOR)) {
        break;
      }
    }
    if (i < this.yearCalendarData.dates.length) {
      // Date was marked -> it will be removed
      this.adjustAddRemoveMap(date, false);
      this.yearCalendarData.dates = this.yearCalendarData.dates.slice(0, i).concat(this.yearCalendarData.dates.slice(i + 1,
        this.yearCalendarData.dates.length));
    } else {
      // Date was not marked -> it will be marked
      this.adjustAddRemoveMap(date, true);
      this.yearCalendarData.dates = [...this.yearCalendarData.dates, {
        id: date.getTime(), start: date, end: date,
        color: this.originalDaysMark.has(date.getTime()) ? this.getExistingColor(date) : this.NEW_DATE_COLOR,
        select: (range: RangeSelectDays, ranges: RangeSelectDays[]) => this.onRangeSelect(range, ranges)
      }];
    }
  }

  protected resetMenu(): void {
    this.activePanelService.activatePanel(this, {
      showMenu: this.getMenuShowOptions(),
      editMenu: this.getEditMenu()
    });
  }

  protected getEditMenu(): MenuItem[] {
    return this.contextMenuItems;
  }

  protected getMenuShowOptions(): MenuItem[] {
    const menuItems: MenuItem[] = [
      {label: 'TRADING_CALENDAR_UNDERLINE', command: (e) => this.underline = !this.underline}
    ];
    TranslateHelper.translateMenuItems(menuItems, this.translateService);
    return menuItems;
  }

  protected markOnOffSingleDays(dayToMarkList: Date[]): void {
    this.addRemoveDaysMap = new Map();
    this.originalDaysMark = new Set();

    dayToMarkList.forEach((tradingDay, i) => {
      const date = this.getZeroTimeDate(tradingDay);

      this.originalDaysMark.add(date.getTime());
      this.yearCalendarData.dates.push({
        id: i, start: date, end: date,
        color: this.getExistingColor(date),
        select: (range: RangeSelectDays, ranges: RangeSelectDays[]) => this.onRangeSelect(range, ranges)
      });
    });
  }

  protected getZeroTimeDate(dateToSet: Date): Date {
    const date = new Date(dateToSet);
    date.setHours(0, 0, 0, 0);
    return date;
  }

  protected getExistingColor(date: Date): string {
    return this.markExistingColors[0];
  }

  private adjustAddRemoveMap(date: Date, addDay: boolean) {
    if (this.addRemoveDaysMap.has(date.getTime())) {
      this.addRemoveDaysMap.delete(date.getTime());
    } else {
      this.addRemoveDaysMap.set(date.getTime(), addDay);
    }
  }
}
