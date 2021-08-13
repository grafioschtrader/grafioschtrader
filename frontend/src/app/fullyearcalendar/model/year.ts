import {Month} from './month';
import {DayOfWeek} from './day.of.week';
import {Day} from './day';

export class Year {

  year: number;
  months: Month[] = [];

  constructor(y: number) {
    this.year = y;
    this._initYear();
    //will kind of remove/delete empty not valid days.
    // for(let i=0; i < this.months.length; i++) {
    //     for(let x=0; x < this.months[i].weeks.length; x++) {
    //         for(let y=0; y < this.months[i].weeks[x].daysOfWeek.length; y++) {
    //             if(!this.months[i].weeks[x].daysOfWeek[y].init) {
    //                 delete this.months[i].weeks[x].daysOfWeek[y];
    //             }
    //         }
    //     }
    // }
  }

  public static getDayNumberByDayOfWeek(dayOfWeek: DayOfWeek): number {
    const daysInAWeek = Object.keys(DayOfWeek).length;
    for (let i = 0; i < daysInAWeek; i++) {
      if (Year.getDayOfWeek(i) === dayOfWeek) {
        return i;
      }
    }
    return -1;
  }

  public static getDayOfWeek(day: number): DayOfWeek {
    switch (day) {
      case 0:
        return DayOfWeek.SUNDAY;
      case 1:
        return DayOfWeek.MONDAY;
      case 2:
        return DayOfWeek.TUESDAY;
      case 3:
        return DayOfWeek.WEDNESDAY;
      case 4:
        return DayOfWeek.THURSDAY;
      case 5:
        return DayOfWeek.FRIDAY;
      case 6:
        return DayOfWeek.SATURDAY;
    }
  }

  getMonthDays(month: number): Day[] {
    const date = new Date(this.year, month, 1);
    date.setHours(0, 0, 0, 0);
    const days: Day[] = [];
    while (date.getMonth() === month) {
      const day = new Day();
      day.day = new Date(date);
      day.dayOfWeek = Year.getDayOfWeek(day.day.getDay());
      days.push(day);
      date.setDate(date.getDate() + 1);
    }
    return days;
  }

  private _initYear(): void {
    for (let i = 0; i < 12; i++) {
      const days: Day[] = this.getMonthDays(i);
      const month = new Month(days);
      month.index = i;
      month.days = days;
      this.months.push(month);
    }
  }


}
