import { Week } from './week';
import { Day } from './day';

export class Month {
  days: Day[] = [];
  weeks: Week[] = [];

  constructor(
    public index: number,
    daysOfmonth: Day[] = []
  ) {
    this.days = daysOfmonth;
    for (let i = 0; i < 6; i++) {
      this.weeks.push(new Week());
    }
    let weekIndex = 0;
    for (const d of daysOfmonth) {
      d.init = true;
      if (this.weeks[weekIndex].isFull()) {
        if (++weekIndex > 5) {
          break;
        }
        this.weeks[weekIndex].add(d);
      } else {
        this.weeks[weekIndex].add(d);
      }
    }
  }
}
