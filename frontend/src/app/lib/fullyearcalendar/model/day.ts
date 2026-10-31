import { Range } from './range';
import { DayOfWeek } from './day.of.week';

export class Day {
  day?: Date;
  dayOfWeek: DayOfWeek | null = null;
  init = false;
  ranges?: Range[];
  isDisabled = false;
}
