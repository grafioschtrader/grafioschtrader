import { describe, expect, it } from 'vitest';
import { parseTradingDays, type FirstAndMissingTradingDaysResponse } from './trading-days';

describe('trading-day date picker boundaries', () => {
  it.each([
    [2024, 2, 31, '2024-03-31'],
    [2024, 9, 27, '2024-10-27']
  ])('retains local midnight across daylight-saving boundaries (%s-%s-%s)', (year, month, day, iso) => {
    const response: FirstAndMissingTradingDaysResponse = {
      maxWeekLimit: 12,
      minIncludeMonthLimit: 3,
      firstEverTradingDay: iso,
      secondEverTradingDay: iso,
      lastTradingDayOfLastYear: iso,
      secondLatestTradingDay: iso,
      latestTradingDay: iso,
      holidayAndMissingQuoteDays: [iso]
    };
    const parsed = parseTradingDays(response);
    const expected = new Date(Number(year), Number(month), Number(day));
    expect(parsed.firstEverTradingDay).toEqual(expected);
    expect(parsed.secondEverTradingDay).toEqual(expected);
    expect(parsed.lastTradingDayOfLastYear).toEqual(expected);
    expect(parsed.secondLatestTradingDay).toEqual(expected);
    expect(parsed.latestTradingDay).toEqual(expected);
    expect(parsed.holidayAndMissingQuoteDays).toEqual([expected]);
    expect(response.firstEverTradingDay).toBe(iso);
  });

  it('retains missing dates and an absent holiday list without producing epoch dates', () => {
    const parsed = parseTradingDays({
      maxWeekLimit: 12,
      minIncludeMonthLimit: 3,
      firstEverTradingDay: null,
      secondEverTradingDay: null,
      lastTradingDayOfLastYear: null,
      secondLatestTradingDay: null,
      latestTradingDay: null
    });
    expect(parsed.firstEverTradingDay).toBeNull();
    expect(parsed.secondEverTradingDay).toBeNull();
    expect(parsed.lastTradingDayOfLastYear).toBeNull();
    expect(parsed.secondLatestTradingDay).toBeNull();
    expect(parsed.latestTradingDay).toBeNull();
    expect(parsed.holidayAndMissingQuoteDays).toEqual([]);
  });
});
