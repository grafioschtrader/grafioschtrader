export interface RangeSelectDays {
  id?: number;
  tooltip?: string;
  start: Date;
  end: Date;
  color?: string;
  day?: Date;

  select?(range: RangeSelectDays, ranges: RangeSelectDays[]): void;
}
