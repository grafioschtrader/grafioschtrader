import { Day } from './day';

export class Week {
  daysOfWeek: Day[] = [];

  constructor() {
    for (let i = 0; i < 7; i++) {
      this.daysOfWeek.push(new Day());
    }
  }

  isFull(): boolean {
    return this.daysOfWeek[6].init;
  }

  add(day: Day): void {
    const date = day.day;
    if (!date) throw new Error('A calendar week can only receive a dated day');
    this.daysOfWeek[date.getDay()] = day;
  }
}
