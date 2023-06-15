import {Component, EventEmitter, Input, OnDestroy, Output} from '@angular/core';
import {Month} from '../model/month';
import {Day} from '../model/day';

@Component({
  selector: 'month-calendar',
  template: `
    <div *ngIf="month" class="calcontainer">
      <div style="text-align: center;">
        <b>{{locale['monthNames'][month.index]}}</b>
      </div>
      <table style="margin: 0px auto;">
        <thead>
        <tr>
          <th *ngFor="let wd of locale.dayNamesMin">
            <div [className]="disabledDaysOfWeek && disabledDaysOfWeek.indexOf(wd) >= 0 ? 'disabledHeaderDay' : ''">
              <b>{{wd}}</b>
            </div>
          </th>
        </tr>
        </thead>
        <tbody>
        <tr *ngFor="let week of month.weeks">
          <td *ngFor="let day of week.daysOfWeek" style="text-align: center;padding: 0px;">
            <div [className]="day && day.isDisabled ? 'daydisabled' : ''"
                 (click)="day && day.isDisabled ? $event.stopPropagation() : dayClick(day)" *ngIf="day.init">
              <div #activeday class="day caltooltip"
                   [ngStyle]="day.ranges ? (underline) ? {'border-bottom':'5px solid '
                   + day.ranges[day.ranges.length - 1].backgroundColor,'margin-bottom':'-5px'} :
                   {'background':day.ranges[day.ranges.length - 1].backgroundColor,
                   'color' : day.ranges[day.ranges.length - 1].foregroundColor}
                   : {'background':'transparent'}">
                {{day.day.getDate()}}
                <span *ngIf="day.ranges">
                                <span class="tooltiptext">
                                    <span *ngFor="let t of day.ranges">
                                        <div *ngIf="t.tooltip">
                                            {{t.tooltip}}
                                        </div>
                                    </span>
                                </span>
                            </span>
              </div>
            </div>
          </td>
        </tr>
        </tbody>
      </table>
    </div>
  `,
  styleUrls: ['./monthly-calendar.scss'],
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
