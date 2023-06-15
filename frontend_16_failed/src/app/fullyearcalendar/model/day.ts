import {Range} from './range';
import {DayOfWeek} from './day.of.week';

export class Day {
  day: Date;
  dayOfWeek: DayOfWeek;
  init = false;
  color: string;
  tooltip: string;
  ranges: Range[];
  isDisabled = false;
}
