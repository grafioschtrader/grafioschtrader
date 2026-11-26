import { Component, computed, inject, input, output } from '@angular/core';

import { Year } from './model/year';
import { Range } from './model/range';
import { YearCalendarData } from './Interface/year.calendar.data';
import { LocaleSettings } from './Interface/locale.settings';
import { DisabledDate } from './Interface/disabled.date';
import { DayOfWeek } from './model/day.of.week';
import { MonthlyCalendarComponent } from './month-calendar/monthly-calendar.component';
import { ThemeService } from '../theme/theme.service';
import { ThemeHelper } from '../theme/theme.helper';

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
  /**
   * Display colors of the semantic color keys in the dark scheme. The callers keep their keys in the data, because
   * they identify marked days by comparing them; only the rendering resolves a key to a color of the active scheme.
   * Keys without an entry are shown as they are.
   */
  private static readonly DARK_DISPLAY_COLORS: Readonly<Record<string, string>> = {
    yellow: '#b59b1d',
    lime: '#2e7d32',
    red: '#b91c1c',
    blue: '#1d4ed8'
  };

  private readonly themeService = inject(ThemeService);
  /** Rebuilt when the data or the color scheme changes. */
  readonly year = computed(() => this.buildYear(this.yearCalendarData(), this.themeService.isDark()));
  private colorBgToFgColor: Map<string, string> = new Map();

  isDisabled(date: Date | null | undefined, disabledDays: DisabledDate[] | undefined): boolean {
    return (
      date != null &&
      disabledDays?.find(
        (i) =>
          i.date.getFullYear() === date.getFullYear() &&
          i.date.getMonth() === date.getMonth() &&
          i.date.getDate() === date.getDate()
      ) != null
    );
  }

  isWeekdayDisabled(dayOfWeek: DayOfWeek | null, daysOfWeek: DayOfWeek[] | undefined): boolean {
    return dayOfWeek !== null && (daysOfWeek?.includes(dayOfWeek) ?? false);
  }

  dayClicked(day: Date): void {
    this.daySelect.emit(day);
  }

  /**
   * Text color for a day on a range color: black or white, whichever reaches the higher WCAG contrast.
   *
   * @param backgroundColor - Any valid CSS color
   * @returns `#000000` or `#ffffff`
   */
  getForegroundColorByBackgroundColor(backgroundColor: string): string {
    let foregroundColor = this.colorBgToFgColor.get(backgroundColor);
    if (!foregroundColor) {
      const rgb = this.convertColorToRGBA(backgroundColor);
      foregroundColor = ThemeHelper.contrastingForeground([rgb[0], rgb[1], rgb[2]]);
      this.colorBgToFgColor.set(backgroundColor, foregroundColor);
    }
    return foregroundColor;
  }

  /**
   * Resolves the color key of a range to the color shown in the active scheme.
   *
   * @param colorKey - The color stored with the range, `gray` when it has none
   * @param isDark - Whether the dark scheme is active
   * @returns The display color
   */
  static displayColor(colorKey: string, isDark: boolean): string {
    return (isDark && FullyearcalendarLibComponent.DARK_DISPLAY_COLORS[colorKey]) || colorKey;
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
    if (!ctx) {
      throw new Error('Calendar colors require a 2D canvas context');
    }
    ctx.fillStyle = color;
    ctx.fillRect(0, 0, 1, 1);
    return ctx.getImageData(0, 0, 1, 1).data;
  }

  private buildYear(val: YearCalendarData, isDark: boolean): Year {
    const year = new Year(val.year);
    for (const m of year.months) {
      for (const w of m.weeks) {
        for (const day of w.daysOfWeek) {
          const date = day.day;
          if (!date) continue;
          if (val.dates && val.dates.length > 0) {
            for (const d of val.dates) {
              if (date >= d.start && date <= d.end) {
                const backgroundColor = FullyearcalendarLibComponent.displayColor(d.color ? d.color : 'gray', isDark);
                const range = new Range(
                  d,
                  date,
                  backgroundColor,
                  this.getForegroundColorByBackgroundColor(backgroundColor)
                );
                if (!day.ranges) {
                  day.ranges = [];
                }
                day.ranges.push(range);
              }
            }
          }
          day.isDisabled =
            this.isDisabled(date, val.disabledDays) || this.isWeekdayDisabled(day.dayOfWeek, val.disableWeekDays);
        }
      }
    }
    return year;
  }
}
