import { describe, expect, it } from 'vitest';
import { WatchlistIndicatorColumn } from './watchlist-indicator';
import { indicatorMarkClass } from './watchlist-indicator.display';

function column(markColors: string, lowerMark: number | null, upperMark: number | null): WatchlistIndicatorColumn {
  const c = new WatchlistIndicatorColumn();
  c.markColors = markColors;
  c.lowerMark = lowerMark;
  c.upperMark = upperMark;
  return c;
}

describe('indicatorMarkClass', () => {
  it('marks a high value as gain and a low value as loss by default', () => {
    const c = column('LOW_NEGATIVE_HIGH_POSITIVE', 30, 70);
    expect(indicatorMarkClass(c, 75)).toBe('gt-gain');
    expect(indicatorMarkClass(c, 70)).toBe('gt-gain');
    expect(indicatorMarkClass(c, 25)).toBe('gt-loss');
    expect(indicatorMarkClass(c, 50)).toBeNull();
  });

  it('inverts the meaning for a low-positive column', () => {
    const c = column('LOW_POSITIVE_HIGH_NEGATIVE', 30, 70);
    expect(indicatorMarkClass(c, 25)).toBe('gt-gain');
    expect(indicatorMarkClass(c, 75)).toBe('gt-loss');
  });

  it('highlights the background on both sides of a neutral column', () => {
    const c = column('NEUTRAL', 30, 70);
    expect(indicatorMarkClass(c, 10)).toBe('gt-mark-neutral');
    expect(indicatorMarkClass(c, 90)).toBe('gt-mark-neutral');
    expect(indicatorMarkClass(c, 50)).toBeNull();
  });

  it('ignores missing marks and values that are no number', () => {
    expect(indicatorMarkClass(column('LOW_NEGATIVE_HIGH_POSITIVE', null, 70), -1000)).toBeNull();
    expect(indicatorMarkClass(column('LOW_NEGATIVE_HIGH_POSITIVE', 30, 70), true)).toBeNull();
    expect(indicatorMarkClass(column('LOW_NEGATIVE_HIGH_POSITIVE', 30, 70), null)).toBeNull();
  });
});
