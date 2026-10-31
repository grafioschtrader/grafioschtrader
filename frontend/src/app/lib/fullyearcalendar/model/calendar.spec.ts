import { describe, expect, it, vi } from 'vitest';
import { Year } from './year';
import { Range } from './range';
import type { RangeSelectDays } from '../Interface/range.select.days';

describe('calendar dates and padding', () => {
  it.each([
    [2024, 366, 29],
    [2025, 365, 28],
    [2000, 366, 29],
    [2100, 365, 28]
  ])('places every date of %i once, including leap-year boundaries', (yearNumber, count, februaryDays) => {
    const year = new Year(yearNumber);
    expect(year.months[1].days).toHaveLength(februaryDays);
    const dates = year.months.flatMap((month) =>
      month.weeks.flatMap((week) => week.daysOfWeek.flatMap((day) => (day.day ? [day.day] : [])))
    );
    expect(dates).toHaveLength(count);
    expect(new Set(dates.map((date) => date.getTime())).size).toBe(count);
    expect(dates[0]).toEqual(new Date(yearNumber, 0, 1));
    expect(dates.at(-1)).toEqual(new Date(yearNumber, 11, 31));
    for (const month of year.months) {
      for (const week of month.weeks) {
        week.daysOfWeek.forEach((day, column) => {
          if (day.day) {
            expect(day.day.getMonth()).toBe(month.index);
            expect(day.day.getDay()).toBe(column);
          } else {
            expect(day.dayOfWeek).toBeNull();
            expect(day.ranges).toBeUndefined();
            expect(day.init).toBe(false);
          }
        });
      }
    }
  });

  it('retains the sixth week of a month beginning on Saturday', () => {
    const august = new Year(2026).months[7];
    expect(august.weeks[0].daysOfWeek[6].day).toEqual(new Date(2026, 7, 1));
    expect(august.weeks[5].daysOfWeek[1].day).toEqual(new Date(2026, 7, 31));
    expect(august.weeks[5].daysOfWeek[2].day).toBeUndefined();
  });

  it('forwards the selected occurrence and all overlapping ranges to the original callback', () => {
    const date = new Date(2026, 9, 30);
    const select = vi.fn();
    const source: RangeSelectDays = { id: 7, start: date, end: date, select };
    const global = new Range(source, date, 'red', 'white');
    const local = new Range({ start: date, end: date }, date, 'blue', 'white');
    const ranges = [global, local];
    global.select(global, ranges);
    local.select(local, ranges);
    expect(select).toHaveBeenCalledExactlyOnceWith(global, ranges);
    expect(select.mock.contexts[0]).toBe(source);
  });
});
