import { describe, expect, it } from 'vitest';
import { requestAgeCellStyle } from './request-age.color';

const created = '2026-11-13 20:00:00';
const createdMillis = Date.UTC(2026, 10, 13, 20, 0, 0);
const DAY = 24 * 60 * 60 * 1000;

const alpha = (style: { [key: string]: string } | null): number => {
  const match = /rgba\(\d+, \d+, \d+, ([\d.]+)\)/.exec(style?.['background-color'] ?? '');
  return match ? Number(match[1]) : NaN;
};

describe('Red mark of an open administrator request', () => {
  it('is strongest on the day of creation and fades until it vanishes after five days', () => {
    expect(alpha(requestAgeCellStyle(created, createdMillis))).toBeCloseTo(0.6, 3);
    expect(alpha(requestAgeCellStyle(created, createdMillis + 2.5 * DAY))).toBeCloseTo(0.3, 3);
    expect(alpha(requestAgeCellStyle(created, createdMillis + 4.9 * DAY))).toBeCloseTo(0.012, 3);
  });

  it('has no background any more from the age of five days', () => {
    expect(requestAgeCellStyle(created, createdMillis + 5 * DAY)).toBeNull();
    expect(requestAgeCellStyle(created, createdMillis + 40 * DAY)).toBeNull();
  });

  it('treats the server time as UTC and tolerates a client clock slightly behind the server', () => {
    expect(alpha(requestAgeCellStyle(created, createdMillis - 60_000))).toBeCloseTo(0.6, 3);
  });

  it('marks nothing without a parsable creation time', () => {
    expect(requestAgeCellStyle(null, createdMillis)).toBeNull();
    expect(requestAgeCellStyle('not a date', createdMillis)).toBeNull();
  });
});
