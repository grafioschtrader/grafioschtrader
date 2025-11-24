import {RangeSelectDays} from './range.select.days';
import {DisabledDate} from './disabled.date';
import {DayOfWeek} from '../model/day.of.week';

export interface YearCalendarData {
  year: number;
  dates?: RangeSelectDays[];
  disabledDays?: DisabledDate[];
  disableWeekDays?: DayOfWeek[];
}
