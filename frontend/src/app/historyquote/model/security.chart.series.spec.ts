import { describe, expect, it } from 'vitest';
import { holdingSteps, interpolatedClose, totalReturnFactorAt } from './security.chart.series';

const trade = (time: string, type: string, holdings: number) => ({
  transaction: { transactionTime: time, transactionType: type },
  holdingsSplitAdjusted: holdings
});

describe('holdingSteps', () => {
  const positions = [
    trade('2023-02-14T10:00:00', 'ACCUMULATE', 100),
    trade('2024-04-26T00:00:00', 'DIVIDEND', 100),
    trade('2024-09-03T00:00:00', 'ACCUMULATE', 180),
    trade('2025-11-12T00:00:00', 'REDUCE', 150)
  ];

  it('starts with the units held before the period and steps at each purchase and sale', () => {
    expect(holdingSteps(positions, '2024-01-01', '2026-10-30')).toEqual({
      x: ['2024-01-01', '2024-09-03', '2024-09-03', '2025-11-12', '2025-11-12', '2026-10-30'],
      y: [100, 100, 180, 180, 150, 150]
    });
  });

  it('draws a flat line through a period without trades while units are held', () => {
    expect(holdingSteps(positions, '2026-01-01', '2026-10-30')).toEqual({
      x: ['2026-01-01', '2026-10-30'],
      y: [150, 150]
    });
  });

  it('has no steps once everything was sold before the period', () => {
    expect(
      holdingSteps(
        [trade('2023-02-14', 'ACCUMULATE', 10), trade('2023-05-01', 'REDUCE', 0)],
        '2024-01-01',
        '2026-10-30'
      )
    ).toEqual({
      x: [],
      y: []
    });
  });
});

describe('interpolatedClose', () => {
  it('averages the nearest closes around a day without a price and falls back to the one that exists', () => {
    expect(interpolatedClose([10, null, 14], 1)).toBe(12);
    expect(interpolatedClose([10, null, null], 2)).toBe(10);
    expect(interpolatedClose([null, null], 0)).toBeNull();
  });
});

describe('totalReturnFactorAt', () => {
  it('takes the factor of the first ex-date after the day', () => {
    const factors = [
      { exDate: '2024-04-26', factor: 0.95 },
      { exDate: '2025-04-25', factor: 0.98 }
    ];
    expect(totalReturnFactorAt(factors, '2024-01-10')).toBe(0.95);
    expect(totalReturnFactorAt(factors, '2024-04-26')).toBe(0.98);
    expect(totalReturnFactorAt(factors, '2025-06-01')).toBe(1);
  });
});
