import { describe, expect, it } from 'vitest';
import { performanceTotalChartTraces } from './performance.total.chart.plot';

describe('performanceTotalChartTraces', () => {
  it('puts total, invested capital and their difference into the tooltip of the total line', () => {
    const traces = performanceTotalChartTraces(
      [
        { date: '2026-10-28', totalBalanceMC: 105000, investedCapitalMC: 100000 },
        { date: '2026-10-29', totalBalanceMC: 98000, investedCapitalMC: 100000 }
      ],
      { totalBalance: 'Total', investedCapital: 'Invested', difference: 'Difference' },
      { date: (point) => point.date, amount: (value) => String(value) }
    );
    expect(traces[0].customdata).toEqual([
      ['2026-10-28', '105000', '100000', '5000'],
      ['2026-10-29', '98000', '100000', '-2000']
    ]);
    expect(traces[1].y).toEqual([100000, 100000]);
    expect(traces[1].hoverinfo).toBe('skip');
  });
});
