import type { RangeSelectDays } from '../Interface/range.select.days';

export class Range implements RangeSelectDays {
  id: number | undefined;
  start: Date;
  end: Date;
  tooltip: string | undefined;
  constructor(
    private readonly source: RangeSelectDays,
    public day: Date,
    public backgroundColor: string,
    public foregroundColor: string
  ) {
    this.id = source.id;
    this.start = source.start;
    this.end = source.end;
    this.tooltip = source.tooltip;
  }

  select(range: RangeSelectDays, ranges: RangeSelectDays[]): void {
    if (typeof this.source.select === 'function') this.source.select(range, ranges);
  }
}
