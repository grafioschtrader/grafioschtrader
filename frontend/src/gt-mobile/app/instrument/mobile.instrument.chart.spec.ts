import { describe, expect, it } from 'vitest';
import {
  mobileChart,
  mobileChartFromDate,
  mobileChartPriceRange,
  mobileChartRelayoutWindow
} from './mobile.instrument.chart';

const texts = {
  price: 'Kurs',
  holding: 'Bestand',
  types: { ACCUMULATE: 'Kaufen', REDUCE: 'Verkaufen', DIVIDEND: 'Dividende' }
};
const surface = { textColor: '#000', gridColor: '#ccc', gainColor: 'green', lossColor: 'red', separators: ".'" };

describe('mobileChartFromDate', () => {
  it('moves a start on a weekend to the following Monday, as the desktop does', () => {
    // 2026-10-30 is a Friday; one year earlier, 2025-10-30, is a Thursday.
    expect(mobileChartFromDate('1Y', '2026-10-30', null)).toBe('2025-10-30');
    // Seven days before Sunday 2026-11-01 is Sunday 2026-10-25, shown from Monday 2026-10-26.
    expect(mobileChartFromDate('5D', '2026-11-01', null)).toBe('2026-10-26');
  });

  it('shows the whole history for MAX and starts at the first trade for TRADE', () => {
    expect(mobileChartFromDate('MAX', '2026-10-30', '2023-02-14')).toBeNull();
    expect(mobileChartFromDate('TRADE', '2026-10-30', '2023-02-14')).toBe('2023-02-14');
  });
});

describe('mobileChart', () => {
  const response = {
    dateCloseList: [
      { date: '2024-01-02', close: 100 },
      { date: '2024-01-03', close: null },
      { date: '2024-01-04', close: 104 },
      { date: '2024-01-05', close: 106 }
    ],
    totalReturn: true,
    totalReturnFactors: [{ exDate: '2024-01-04', factor: 0.9 }]
  };
  const positions = [
    {
      transaction: { transactionTime: '2024-01-02T00:00:00', transactionType: 'ACCUMULATE', quotation: 210 },
      quotationSplitCorrection: 105,
      holdingsSplitAdjusted: 20
    },
    {
      transaction: { transactionTime: '2024-01-03T00:00:00', transactionType: 'DIVIDEND', quotation: 1.5 },
      holdingsSplitAdjusted: 20
    }
  ];

  it('puts a purchase at its split-adjusted price scaled like the line and a dividend on the line', () => {
    const { traces, withHolding } = mobileChart(response, positions, '2024-01-05', true, texts, surface);
    expect(withHolding).toBe(true);
    expect(traces.map((t) => t.name)).toEqual(['Kurs', 'Kaufen', 'Dividende', 'Bestand']);
    expect(traces[1].y).toEqual([105 * 0.9]);
    // The dividend day has no price; it sits between the closes around it.
    expect(traces[2].y).toEqual([102]);
    expect(traces[3].yaxis).toBe('y2');
  });

  it('leaves the price line a gap where a close is missing and omits the units held when switched off', () => {
    const { traces, withHolding } = mobileChart(response, positions, '2024-01-05', false, texts, surface);
    expect(withHolding).toBe(false);
    expect(traces[0].y).toEqual([100, null, 104, 106]);
    expect(traces.some((t) => t.name === 'Bestand')).toBe(false);
  });
});

describe('mobileChartPriceRange', () => {
  const series = [
    { x: ['2020-01-02', '2025-06-02', '2025-06-03'], y: [10, 100, null] },
    { x: ['2025-06-03'], y: [110] }
  ];

  it('fits the axis to the prices and markers within the window, not to the whole history', () => {
    expect(mobileChartPriceRange(series, '2025-01-01 00:00:00', '2025-12-31')).toEqual([99.5, 110.5]);
  });

  it('has no range for a window without prices', () => {
    expect(mobileChartPriceRange(series, '2021-01-01', '2021-12-31')).toBeNull();
  });
});

describe('mobileChartRelayoutWindow', () => {
  const whole: [string, string] = ['2020-01-02', '2026-10-30'];

  it('reads the window from a move by finger, by the range slider and from a double tap', () => {
    expect(
      mobileChartRelayoutWindow({ 'xaxis.range[0]': '2025-01-01 12:00', 'xaxis.range[1]': '2025-06-30' }, whole)
    ).toEqual(['2025-01-01 12:00', '2025-06-30']);
    expect(mobileChartRelayoutWindow({ 'xaxis.range': ['2024-01-01', '2024-12-31'] }, whole)).toEqual([
      '2024-01-01',
      '2024-12-31'
    ]);
    expect(mobileChartRelayoutWindow({ 'xaxis.autorange': true }, whole)).toEqual(whole);
  });

  it('ignores a relayout of the price axis, so fitting it does not start again', () => {
    expect(mobileChartRelayoutWindow({ 'yaxis.range': [1, 2], 'yaxis.autorange': false }, whole)).toBeNull();
  });
});
