import moment from 'moment';
import { BaseSettings } from '../../lib/base.settings';
import type { TotalReturnFactor } from '../../entities/projection/historyquote.chart.response';

/**
 * The fields of a security transaction the price chart reads, so the desktop chart and the mobile application share
 * one calculation of the held units and of the marker prices.
 */
export interface ChartTransactionPosition {
  transaction: {
    transactionTime: string | number | Date | null;
    transactionType: string | null;
  };
  /** Units held after this transaction, adjusted for later splits so they match the adjusted prices. */
  holdingsSplitAdjusted?: number | null;
}

/** Units held over time as a step line: dates in the short native format and the units from that date on. */
export interface HoldingSteps {
  x: string[];
  y: number[];
}

/**
 * The total return factor of a date, i.e. that of the first ex-date after it. It places a real trade price on a line
 * adjusted for reinvested dividends.
 *
 * @param factors Factors of the chart response by ex-date, ascending; empty when the prices are not adjusted
 * @param dateStr Date in the short native format (yyyy-MM-dd)
 * @returns The factor, 1 when no ex-date follows
 */
export function totalReturnFactorAt(factors: TotalReturnFactor[], dateStr: string): number {
  const step = factors.find((trf) => trf.exDate > dateStr);
  return step ? step.factor : 1;
}

/**
 * The close at an index of a price line, for a marker on a day without a price the average of the nearest closes
 * before and after it, or the one that exists.
 *
 * @param closes Closes of the line
 * @param index Index found by a binary search; a negative index marks the insertion point
 * @returns The close, or null when the line has no price at all
 */
export function interpolatedClose(closes: (number | null)[], index: number): number | null {
  const absoluteIndex = Math.abs(index);
  const exact = closes[absoluteIndex];
  if (exact != null) {
    return exact;
  }
  let prevIndex = absoluteIndex - 1;
  while (prevIndex >= 0 && closes[prevIndex] == null) {
    prevIndex--;
  }
  let nextIndex = absoluteIndex + 1;
  while (nextIndex < closes.length && closes[nextIndex] == null) {
    nextIndex++;
  }
  const prev = prevIndex >= 0 ? closes[prevIndex] : null;
  const next = nextIndex < closes.length ? closes[nextIndex] : null;
  if (prev != null && next != null) {
    return (prev + next) / 2;
  }
  return prev ?? next ?? null;
}

/**
 * The units held over the shown period as steps: the holding before the first shown purchase or sale, a vertical
 * step at each of them, and the holding at the end of the period. Only purchases and sales change the units.
 *
 * @param positions Transactions of the security, oldest first
 * @param fromDateStr First day shown, short native format
 * @param endDateStr Last day shown, short native format
 * @returns The steps; empty when nothing was held in the period
 */
export function holdingSteps(
  positions: ChartTransactionPosition[],
  fromDateStr: string,
  endDateStr: string
): HoldingSteps {
  const steps: HoldingSteps = { x: [], y: [] };
  let before = 0;
  let seeded = false;
  for (const position of positions) {
    const type = position.transaction.transactionType;
    const units = position.holdingsSplitAdjusted;
    if ((type !== 'ACCUMULATE' && type !== 'REDUCE') || units == null) {
      continue;
    }
    const dateStr = moment(position.transaction.transactionTime).format(BaseSettings.FORMAT_DATE_SHORT_NATIVE);
    if (dateStr >= fromDateStr) {
      if (!seeded) {
        steps.x.push(fromDateStr);
        steps.y.push(before);
        seeded = true;
      }
      steps.x.push(dateStr, dateStr);
      steps.y.push(before, units);
    }
    before = units;
  }
  if (before !== 0) {
    if (!seeded) {
      steps.x.push(fromDateStr);
      steps.y.push(before);
    }
    steps.x.push(endDateStr);
    steps.y.push(before);
  }
  return steps;
}
