import { Component, computed, input, output } from '@angular/core';

import { Year } from './model/year';
import { Range } from './model/range';
import { YearCalendarData } from './Interface/year.calendar.data';
import { LocaleSettings } from './Interface/locale.settings';
import { DisabledDate } from './Interface/disabled.date';
import { DayOfWeek } from './model/day.of.week';
import { MonthlyCalendarComponent } from './month-calendar/monthly-calendar.component';
import { RangeSelectDays } from './Interface/range.select.days';

@Component({
  selector: 'ng-fullyearcalendar-lib',
  template: `
    @if (year(); as year) {
      <div class="flex-container">
        @for (month of year.months; track month) {
          <div class="grid-item">
            <month-calendar
              [underline]="underline()"
              (dayClicked)="dayClicked($event)"
              [month]="month"
              [disabledDaysOfWeek]="disabledDaysOfWeek()"
              [locale]="locale()" />
          </div>
        }
      </div>
    }
  `,
  styleUrls: ['./fullyearcalendar-lib.scss'],
  imports: [MonthlyCalendarComponent]
})
export class FullyearcalendarLibComponent {
  readonly underline = input(false);
  readonly locale = input<LocaleSettings>({
    dayNamesMin: ['Su', 'Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa'],
    monthNames: [
      'January',
      'February',
      'March',
      'April',
      'May',
      'June',
      'July',
      'August',
      'September',
      'October',
      'November',
      'December'
    ]
  });
  /**
   * Year, marked ranges and disabled days to render. The parents publish a new object on every change, so the
   * calendar is rebuilt from the input alone and needs no deep comparison.
   */
  readonly yearCalendarData = input.required<YearCalendarData>();
  readonly daySelect = output<Date>();

  /** Header names of the disabled week days, in the language of the locale input. */
  readonly disabledDaysOfWeek = computed(() =>
    this.yearCalendarData().disableWeekDays?.map(
      (dayOfWeek: DayOfWeek) => this.locale().dayNamesMin[Year.getDayNumberByDayOfWeek(dayOfWeek)]
    )
  );
  readonly year = computed(() => this.buildYear(this.yearCalendarData()));
  private colorBgToFgColor: Map<string, string> = new Map();

  isDisabled(date: Date, disabledDays: DisabledDate[]): boolean {
    return (
      date &&
      disabledDays &&
      disabledDays.find(
        (i) =>
          i.date.getFullYear() === date.getFullYear() &&
          i.date.getMonth() === date.getMonth() &&
          i.date.getDate() === date.getDate()
      ) != null
    );
  }

  isWeekdayDisabled(dayOfWeek: DayOfWeek, daysOfWeek: DayOfWeek[]): boolean {
    return daysOfWeek && daysOfWeek.indexOf(dayOfWeek) >= 0;
  }

  dayClicked(day: Date): void {
    this.daySelect.emit(day);
  }

  getForegroundColorByBackgroundColor(backgroundColor: string): string {
    let foregroundColor: string = this.colorBgToFgColor.get(backgroundColor);
    if (!foregroundColor) {
      const rgb = this.convertColorToRGBA(backgroundColor);
      foregroundColor = rgb[0] * 0.299 + rgb[1] * 0.587 + rgb[2] * 0.114 > 186 ? 'black' : 'white';
      this.colorBgToFgColor.set(backgroundColor, foregroundColor);
    }
    return foregroundColor;
  }

  /**
   * Returns the color as an array of [r, g, b, a] -- all range from 0 - 255
   * color must be a valid canvas fillStyle. This will cover most anything you'd want to use.
   * Examples:
   * colorToRGBA('red')  # [255, 0, 0, 255]
   * colorToRGBA('#f00') # [255, 0, 0, 255]
   */
  convertColorToRGBA(color: string): Uint8ClampedArray {
    const cvs = document.createElement('canvas');
    cvs.height = 1;
    cvs.width = 1;
    const ctx = cvs.getContext('2d', { willReadFrequently: true });
    ctx.fillStyle = color;
    ctx.fillRect(0, 0, 1, 1);
    return ctx.getImageData(0, 0, 1, 1).data;
  }

  private buildYear(val: YearCalendarData): Year {
    const year = new Year(val.year);
    for (const m of year.months) {
      for (const w of m.weeks) {
        for (const day of w.daysOfWeek) {
          if (val.dates && val.dates.length > 0) {
            for (const d of val.dates) {
              if (day.day >= d.start && day.day <= d.end) {
                const range = new Range();
                range.id = d.id;
                range.start = d.start;
                range.end = d.end;
                range.tooltip = d.tooltip;
                range.backgroundColor = d.color ? d.color : 'gray';
                range.foregroundColor = this.getForegroundColorByBackgroundColor(range.backgroundColor);
                range.day = day.day;
                range.select = (allRanges: RangeSelectDays[]): void => {
                  if (typeof d.select === 'function') {
                    d.select(range, allRanges);
                  }
                };
                if (!day.ranges) {
                  day.ranges = [];
                }
                day.ranges.push(range);
              }
            }
          }
          day.isDisabled =
            this.isDisabled(day.day, val.disabledDays) || this.isWeekdayDisabled(day.dayOfWeek, val.disableWeekDays);
        }
      }
    }
    return year;
  }
}
