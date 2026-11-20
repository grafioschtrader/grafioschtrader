import { describe, expect, it } from 'vitest';
import { splitAtZero, visibleValueRange } from './security-position.chart';

describe('splitAtZero', () => {
  it('inserts a point on 0 at the interpolated time of a sign change', () => {
    const split = splitAtZero(['2025-01-01', '2025-01-03'], [10, -10]);
    expect(split.x).toEqual(['2025-01-01', '2025-01-02 00:00:00', '2025-01-03']);
    expect(split.positive).toEqual([10, 0, 0]);
    expect(split.negative).toEqual([0, 0, -10]);
  });

  it('keeps a missing value as a gap in both parts', () => {
    const split = splitAtZero(['2025-01-01', '2025-01-02'], [5, null]);
    expect(split.positive).toEqual([5, null]);
    expect(split.negative).toEqual([0, null]);
  });
});

describe('visibleValueRange', () => {
  const traces = [
    { x: ['2025-01-01', '2025-01-10', '2025-01-20'], y: [2000, 18000, 20000] },
    { x: ['2025-01-01', '2025-01-10', '2025-01-20'], y: [2100, 17900, null] }
  ];

  it('fits the values inside the window and pads them by 5 %', () => {
    expect(visibleValueRange(traces, '2025-01-05 12:00:00', '2025-01-31', false)).toEqual([17795, 20105]);
  });

  it('keeps 0 for an area filled to 0', () => {
    expect(visibleValueRange(traces, '2025-01-05', '2025-01-31', true)).toEqual([-1000, 21000]);
  });

  it('returns null when no point lies in the window', () => {
    expect(visibleValueRange(traces, '2026-01-01', '2026-02-01', false)).toBeNull();
  });
});
