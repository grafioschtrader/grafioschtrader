import { describe, expect, it } from 'vitest';
import type { FirstAndMissingTradingDays } from './trading-days';
import {
  firstSelectableDateTo,
  initialPerformanceRange,
  performanceRangeBounds,
  storedDateTo
} from './performance-range';

/** Local calendar day, month 1-based. */
const d = (year: number, month: number, day: number): Date => new Date(year, month - 1, day);

/** A scope from Monday 2024-01-08 to Friday 2025-03-14, 2025-01-02 is a holiday. */
const days = (latest: Date = d(2025, 3, 14)): FirstAndMissingTradingDays => ({
  maxWeekLimit: 52,
  minIncludeMonthLimit: 12,
  firstEverTradingDay: d(2024, 1, 8),
  secondEverTradingDay: d(2024, 1, 9),
  lastTradingDayOfLastYear: d(2024, 12, 31),
  secondLatestTradingDay: d(2025, 3, 13),
  latestTradingDay: latest,
  holidayAndMissingQuoteDays: [d(2025, 1, 2)]
});

describe('shared date range of the performance views', () => {
  it('defaults to the last trading day of the previous year and the newest valuation day', () => {
    expect(initialPerformanceRange(days(), null, null)).toEqual({ dateFrom: d(2024, 12, 31), dateTo: d(2025, 3, 14) });
  });

  it('defaults to the first trading day when the history starts after the previous year', () => {
    const young = { ...days(), firstEverTradingDay: d(2025, 2, 3), lastTradingDayOfLastYear: d(2024, 12, 31) };
    expect(initialPerformanceRange(young, null, null)?.dateFrom).toEqual(d(2025, 2, 3));
  });

  it('uses stored dates the calendar of the scope can select', () => {
    expect(initialPerformanceRange(days(), '2024-06-28', '2025-02-28')).toEqual({
      dateFrom: d(2024, 6, 28),
      dateTo: d(2025, 2, 28)
    });
  });

  it('ignores stored dates the scope cannot select', () => {
    // A Saturday, a holiday, a day before the history and a day after the newest valuation day
    expect(initialPerformanceRange(days(), '2024-06-29', null)?.dateFrom).toEqual(d(2024, 12, 31));
    expect(initialPerformanceRange(days(), null, '2025-01-02')?.dateTo).toEqual(d(2025, 3, 14));
    expect(initialPerformanceRange(days(), '2023-12-29', null)?.dateFrom).toEqual(d(2024, 12, 31));
    expect(initialPerformanceRange(days(), null, '2025-03-17')?.dateTo).toEqual(d(2025, 3, 14));
  });

  it('ignores a stored end date that does not lie after the start date', () => {
    expect(initialPerformanceRange(days(), '2025-02-28', '2025-02-28')?.dateTo).toEqual(d(2025, 3, 14));
    expect(initialPerformanceRange(days(), '2025-02-28', '2025-01-31')?.dateTo).toEqual(d(2025, 3, 14));
  });

  it('treats an unparsable entry as absent', () => {
    expect(initialPerformanceRange(days(), 'garbage', '31.12.2024')).toEqual({
      dateFrom: d(2024, 12, 31),
      dateTo: d(2025, 3, 14)
    });
  });

  it('does not store the newest valuation day, so a reopened view follows a newer one', () => {
    expect(storedDateTo(d(2025, 3, 14), days())).toBeNull();
    expect(storedDateTo(d(2025, 3, 13), days())).toBe('2025-03-13');
    expect(storedDateTo(null, days())).toBeNull();
    const stored = storedDateTo(d(2025, 3, 14), days());
    expect(initialPerformanceRange(days(d(2025, 3, 17)), null, stored)?.dateTo).toEqual(d(2025, 3, 17));
  });

  it('gives the start date a different upper bound than the end date', () => {
    const bounds = performanceRangeBounds(days());
    expect(bounds?.fromMin).toEqual(d(2024, 1, 8));
    expect(bounds?.fromMax).toEqual(d(2025, 3, 13));
    expect(bounds?.toMax).toEqual(d(2025, 3, 14));
  });

  it('starts the end date on the first selectable day after the start date', () => {
    const bounds = performanceRangeBounds(days());
    if (!bounds) throw new Error('bounds expected');
    // 2025-01-02 is a holiday of the scope, 2025-03-07 a Friday
    expect(firstSelectableDateTo(d(2025, 1, 1), bounds)).toEqual(d(2025, 1, 3));
    expect(firstSelectableDateTo(d(2025, 3, 7), bounds)).toEqual(d(2025, 3, 10));
  });

  it('offers no range when the scope has no newest valuation day', () => {
    expect(initialPerformanceRange({ ...days(), latestTradingDay: null }, null, null)).toBeNull();
  });
});
