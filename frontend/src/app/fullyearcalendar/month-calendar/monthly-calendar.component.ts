import {Component, EventEmitter, Input, OnDestroy, Output} from '@angular/core';
import {Month} from '../model/month';
import {Day} from '../model/day';

@Component({
  selector: 'month-calendar',
  template: `
    @if (month) {
      <div class="calcontainer">
        <div style="text-align: center;">
          <b>{{ locale['monthNames'][month.index] }}</b>
        </div>
        <table style="margin: 0px auto;">
          <thead>
          <tr>
            @for (wd of locale.dayNamesMin; track wd) {
              <th>
                <div [className]="disabledDaysOfWeek && disabledDaysOfWeek.indexOf(wd) >= 0 ? 'disabledHeaderDay' : ''">
                  <b>{{ wd }}</b>
                </div>
              </th>
            }
          </tr>
          </thead>
          <tbody>
            @for (week of month.weeks; track week) {
              <tr>
                @for (day of week.daysOfWeek; track day) {
                  <td style="text-align: center;padding: 0px;">
                    @if (day.init) {
                      <div [className]="day && day.isDisabled ? 'daydisabled' : ''"
                           (click)="day && day.isDisabled ? $event.stopPropagation() : dayClick(day)">
                        <div #activeday class="day caltooltip"
                             [ngStyle]="day.ranges ? (underline) ? {'border-bottom':'5px solid '
                         + day.ranges[day.ranges.length - 1].backgroundColor,'margin-bottom':'-5px'} :
                         {'background':day.ranges[day.ranges.length - 1].backgroundColor,
                         'color' : day.ranges[day.ranges.length - 1].foregroundColor}
                         : {'background':'transparent'}">
                          {{ day.day.getDate() }}
                          @if (day.ranges) {
                            <span>
                          <span class="tooltiptext">
                            @for (t of day.ranges; track t) {
                              @if (t.tooltip) {
                                <div>
                                  {{ t.tooltip }}
                                </div>
                              }
                            }
                          </span>
                        </span>
                          }
                        </div>
                      </div>
                    }
                  </td>
                }
              </tr>
            }
          </tbody>
        </table>
      </div>
    }
  `,
  styleUrls: ['./monthly-calendar.scss'],
  standalone: false
})
export class MonthlyCalendarComponent implements OnDestroy {

  @Input() underline = false;
  @Input() locale: any;
  @Input() month: Month;
  @Input() disabledDaysOfWeek: string[];

  @Output()
  dayClicked: EventEmitter<Date> = new EventEmitter<Date>();

  ngOnDestroy(): void {
    this.dayClicked.unsubscribe();
  }

  dayClick(day: Day): void {
    if (day.ranges) {
      for (const r of day.ranges) {
        r.select(day.ranges);
      }
    } else {
      this.dayClicked.emit(day.day);
    }
  }

}
