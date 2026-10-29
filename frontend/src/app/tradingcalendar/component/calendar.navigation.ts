import type { PanelClickEvent } from '../../lib/mainmenubar/component/iglobal.menu.attach';
import { LocaleSettings } from '../../lib/fullyearcalendar/Interface/locale.settings';
import { YearCalendarData } from '../../lib/fullyearcalendar/Interface/year.calendar.data';
import { GlobalparameterService } from '../../lib/services/globalparameter.service';
import { Helper } from '../../lib/helper/helper';
import { MenuItem, SelectItem } from '@openng/optimus-ui/api';
import { DayOfWeek } from '../../lib/fullyearcalendar/model/day.of.week';
import { RangeSelectDays } from '../../lib/fullyearcalendar/Interface/range.select.days';
import { TranslateHelper } from '../../lib/helper/translate.helper';
import { TranslateService } from '@ngx-translate/core';
import { ActivePanelService } from '../../lib/mainmenubar/service/active.panel.service';
import { IGlobalMenuAttach } from '../../lib/mainmenubar/component/iglobal.menu.attach';
import { computed, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { forkJoin, Subscription } from 'rxjs';

export abstract class CalendarNavigation implements IGlobalMenuAttach {
  public translateService: TranslateService = inject(TranslateService);
  protected gps: GlobalparameterService = inject(GlobalparameterService);
  protected activePanelService: ActivePanelService = inject(ActivePanelService);

  protected readonly destroyRef = inject(DestroyRef);
  private menuSubscription?: Subscription;
  readonly NEW_DATE_COLOR = 'yellow';

  locale: LocaleSettings;
  readonly yearCalendarData = signal<YearCalendarData>({
    year: new Date().getFullYear(),
    dates: [],
    disableWeekDays: [DayOfWeek.SATURDAY, DayOfWeek.SUNDAY]
  });
  readonly underline = signal(false);
  readonly possibleYears = signal<SelectItem[]>([]);
  readonly selectedYear = computed(() => this.yearCalendarData().year);

  readonly addRemoveDaysMap = signal(new Map<number, boolean>());
  originalDaysMark: Set<number> = new Set();

  // Used when a component click event is consumed by the child and the parent should ignore it.
  readonly consumedGT = 'consumedGT';
  readonly contextMenuItems = signal<MenuItem[]>([]);

  protected constructor(protected markExistingColors: string[]) {
    const language: string = this.gps.getUserLang();

    this.locale = {
      dayNamesMin: Helper.CALENDAR_LANG[language].dayNamesMin,
      monthNames: Helper.CALENDAR_LANG[language].monthNames
    };
    this.destroyRef.onDestroy(() => this.activePanelService.destroyPanel(this));
  }

  abstract getHelpContextId(): string;

  abstract readData(yearChange: boolean): void;

  abstract onRangeSelect(range: RangeSelectDays, ranges: RangeSelectDays[]): void;

  setYearsBoundaries(fromYear: number, toYear: number) {
    this.possibleYears.set(
      Array.from({ length: toYear - fromYear + 1 }, (_, index) => ({
        value: fromYear + index,
        label: (fromYear + index).toString()
      }))
    );
  }

  containsYear(year: number): boolean {
    return this.possibleYears().some((selectItem) => selectItem.value === year);
  }

  yearChanged(event: { value: number }): void {
    this.yearCalendarData.update((data) => ({ ...data, year: event.value, dates: [], disabledDays: [] }));
    this.readData(true);
  }

  addMinusYear(addMinusYear: number): void {
    this.yearChanged({ value: this.selectedYear() + addMinusYear });
  }

  clearDaysAndTransformReadData(dayToMarkList: Date[]): void {
    this.yearCalendarData.update((data) => ({ ...data, dates: [] }));
    this.markOnOffSingleDays(dayToMarkList);
  }

  isActivated(): boolean {
    return this.activePanelService.isActivated(this);
  }

  onComponentClick(event: PanelClickEvent): void {
    event[this.consumedGT] = true;
    this.resetMenu();
  }

  hideContextMenu(): void {}

  callMeDeactivate(): void {}

  onRightClick(event: unknown): void {
    this.resetMenu();
  }

  addRemoveDays(days: Date[]): void {
    days.forEach((date) => this.addRemoveOnOffDay(date));
  }

  /**
   * Add or remove a day from marked days.
   *
   * @param date Date to be added or removed
   */
  addRemoveOnOffDay(date: Date): void {
    let i = 0;

    const data = this.yearCalendarData();
    // Rebuilding the calendar input also rebuilds its Date instances; compare the represented day.
    for (; i < data.dates.length; i++) {
      if (
        data.dates[i].start.getTime() === date.getTime() &&
        (this.markExistingColors.includes(data.dates[i].color) || data.dates[i].color === this.NEW_DATE_COLOR)
      ) {
        break;
      }
    }
    if (i < data.dates.length) {
      // Date was marked -> it will be removed
      this.adjustAddRemoveMap(date, false);
      this.yearCalendarData.set({ ...data, dates: data.dates.filter((_, index) => index !== i) });
    } else {
      // Date was not marked -> it will be marked
      this.adjustAddRemoveMap(date, true);
      this.yearCalendarData.set({
        ...data,
        dates: [
          ...data.dates,
          {
            id: date.getTime(),
            start: date,
            end: date,
            color: this.originalDaysMark.has(date.getTime()) ? this.getExistingColor(date) : this.NEW_DATE_COLOR,
            select: (range: RangeSelectDays, ranges: RangeSelectDays[]) => this.onRangeSelect(range, ranges)
          }
        ]
      });
    }
  }

  protected resetMenu(): void {
    this.menuSubscription?.unsubscribe();
    this.menuSubscription = forkJoin({
      showMenu: TranslateHelper.translateMenuItems$(this.getMenuShowOptions(), this.translateService),
      editMenu: TranslateHelper.translateMenuItems$(this.getEditMenu(), this.translateService)
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((menus) => {
        this.contextMenuItems.set(this.getContextMenuItems(menus.showMenu, menus.editMenu));
        this.activePanelService.activatePanel(this, menus);
      });
  }

  protected getContextMenuItems(showMenu: MenuItem[], editMenu: MenuItem[] | null): MenuItem[] {
    return editMenu ?? [];
  }

  protected getEditMenu(): MenuItem[] | null {
    return [];
  }

  protected getMenuShowOptions(): MenuItem[] {
    const menuItems: MenuItem[] = [
      {
        label: 'TRADING_CALENDAR_UNDERLINE',
        command: () => {
          this.underline.update((underline) => !underline);
        }
      }
    ];
    return menuItems;
  }

  protected markOnOffSingleDays(dayToMarkList: Date[]): void {
    this.addRemoveDaysMap.set(new Map());
    this.originalDaysMark = new Set();

    const dates = dayToMarkList.map((tradingDay, i): RangeSelectDays => {
      const date = this.getZeroTimeDate(tradingDay);

      this.originalDaysMark.add(date.getTime());
      return {
        id: i,
        start: date,
        end: date,
        color: this.getExistingColor(date),
        select: (range: RangeSelectDays, ranges: RangeSelectDays[]) => this.onRangeSelect(range, ranges)
      };
    });
    this.yearCalendarData.update((data) => ({ ...data, dates: [...data.dates, ...dates] }));
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
    const changes = new Map(this.addRemoveDaysMap());
    if (changes.has(date.getTime())) {
      changes.delete(date.getTime());
    } else {
      changes.set(date.getTime(), addDay);
    }
    this.addRemoveDaysMap.set(changes);
  }
}
