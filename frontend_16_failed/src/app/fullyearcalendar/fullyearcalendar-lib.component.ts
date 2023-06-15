import {Component, DoCheck, EventEmitter, Input, OnDestroy, Output} from '@angular/core';
import {Year} from './model/year';
import {Range} from './model/range';
import {YearCalendarData} from './Interface/year.calendar.data';
import {LocaleSettings} from './Interface/locale.settings';
import {DisabledDate} from './Interface/disabled.date';
import {DayOfWeek} from './model/day.of.week';

@Component({
  selector: 'ng-fullyearcalendar-lib',
  template: `
    <div *ngIf="year" class="flex-container">
      <div *ngFor="let month of year.months" class="grid-item">
        <month-calendar [underline]="underline" (dayClicked)="dayClicked($event)" [month]="month"
                        [disabledDaysOfWeek]="disabledDaysOfWeek" [locale]="locale"></month-calendar>
      </div>
    </div>
  `,
  styleUrls: ['./fullyearcalendar-lib.scss']
})
export class FullyearcalendarLibComponent implements OnDestroy, DoCheck {

  @Input() underline = false;
  @Input() locale: LocaleSettings = {
    dayNamesMin: ['Su', 'Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa'],
    monthNames: ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'],
  };
  _yearCalendarData: YearCalendarData;
  @Output() daySelect: EventEmitter<Date> = new EventEmitter<Date>();
  public year: Year;
  disabledDaysOfWeek: string[];
  private initial_data: string;
  private colorBgToFgColor: Map<string, string> = new Map();

  constructor() {
  }

  @Input() set yearCalendarData(val: YearCalendarData) {
    if (val.disableWeekDays) {
      this.disabledDaysOfWeek = [];
      val.disableWeekDays.forEach((dayOfWeek: DayOfWeek) =>
        this.disabledDaysOfWeek.push(this.locale.dayNamesMin[Year.getDayNumberByDayOfWeek(dayOfWeek)]));
    }
    this._yearCalendarData = val;

    this.initValue(val);
  }

  ngOnDestroy(): void {
    this.daySelect.unsubscribe();
  }

  /**
   * from on push in values, comparing if there is any difference
   */
  ngDoCheck(): void {
    const stringData = JSON.stringify(this._yearCalendarData);
    if (this.initial_data !== stringData) {
      this.initial_data = stringData;
      this.initValue(this._yearCalendarData, stringData);
    }
  }

  isDisabled(date: Date, disabledDays: DisabledDate[]): boolean {
    return date && disabledDays && ((disabledDays.find(i => i.date.getFullYear() === date.getFullYear()
      && i.date.getMonth() === date.getMonth() && i.date.getDate() === date.getDate())) != null);
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
    const ctx = cvs.getContext('2d');
    ctx.fillStyle = color;
    ctx.fillRect(0, 0, 1, 1);
    const rgb: Uint8ClampedArray = ctx.getImageData(0, 0, 1, 1).data;

    return ctx.getImageData(0, 0, 1, 1).data;
  }

  private initValue(val: YearCalendarData, oldValue: string = null): void {
    this.year = new Year(val.year);
    this.initial_data = oldValue != null ? oldValue : JSON.stringify(val);
    for (const m of this.year.months) {
      for (const w of m.weeks) {
        for (const day of w.daysOfWeek) {
          if (this._yearCalendarData.dates && this._yearCalendarData.dates.length > 0) {
            for (const d of this._yearCalendarData.dates) {

              if (day.day >= d.start && day.day <= d.end) {
                const range = new Range();
                range.id = d.id;
                range.start = d.start;
                range.end = d.end;
                range.tooltip = d.tooltip;
                range.backgroundColor = (d.color) ? d.color : 'gray';
                range.foregroundColor = this.getForegroundColorByBackgroundColor(range.backgroundColor);
                range.day = day.day;
                range.select = (allRanges): void => {
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
          day.isDisabled = this.isDisabled(day.day, this._yearCalendarData.disabledDays)
            || this.isWeekdayDisabled(day.dayOfWeek, this._yearCalendarData.disableWeekDays);
        }
      }
    }
  }


}
