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

/** The kinds of transaction the position chart marks, each with its own trace, symbol and letter. */
export type PositionEventKind = 'PURCHASE' | 'SALE' | 'TRANSFER' | 'INCOME' | 'FINANCE_COST';

/** The fields of a position event the markers read. */
export interface PositionEventInput {
  date: string;
  transactionType: string;
  units: number | null;
  amountSC: number;
  transfer: boolean;
}

/** Translated texts of the markers and the formatting of one event for the hover. */
export interface PositionEventLabels {
  names: Record<PositionEventKind, string>;
  letters: Record<PositionEventKind, string>;
  /** One hover line of an event, for example units and amount formatted by GT's data types. */
  describe: (event: PositionEventInput) => string;
}

/** A marker trace of the position chart; it lies on its own hidden axis at the bottom of the plot. */
export interface PositionEventTrace {
  x: string[];
  y: number[];
  text: string[];
  hovertext: string[];
  name: string;
  type: 'scatter';
  mode: 'markers+text';
  textposition: 'top center';
  hoverinfo: 'text';
  yaxis: 'y2';
  marker: { symbol: string; size: number };
}

const EVENT_KIND_ORDER: PositionEventKind[] = ['PURCHASE', 'SALE', 'TRANSFER', 'INCOME', 'FINANCE_COST'];

const EVENT_KIND_SYMBOL: Record<PositionEventKind, string> = {
  PURCHASE: 'triangle-up',
  SALE: 'triangle-down',
  TRANSFER: 'diamond',
  INCOME: 'circle',
  FINANCE_COST: 'square'
};

/** Height of the markers on their hidden axis, whose range is 0 to 1. */
export const POSITION_EVENT_Y = 0.03;

/**
 * The kind of marker of a transaction: a leg of a security transfer is a transfer whatever its type.
 *
 * @param event - The event of the position
 * @returns the marker kind
 */
export function positionEventKind(event: PositionEventInput): PositionEventKind {
  if (event.transfer && (event.transactionType === 'ACCUMULATE' || event.transactionType === 'REDUCE')) {
    return 'TRANSFER';
  }
  switch (event.transactionType) {
    case 'ACCUMULATE':
      return 'PURCHASE';
    case 'REDUCE':
      return 'SALE';
    case 'FINANCE_COST':
      return 'FINANCE_COST';
    default:
      return 'INCOME';
  }
}

/**
 * One marker trace per kind of transaction present, with one marker per day: several events of a kind on one day
 * share the marker, and the hover lists each of them. Each trace can be hidden in the legend.
 *
 * @param events - The events of the phase in any order
 * @param labels - Names, letters and the hover line of an event
 * @returns the traces in the order purchase, sale, transfer, income, finance cost
 */
export function positionEventTraces(events: PositionEventInput[], labels: PositionEventLabels): PositionEventTrace[] {
  const byKind = new Map<PositionEventKind, Map<string, string[]>>();
  for (const event of events) {
    const kind = positionEventKind(event);
    const days = byKind.get(kind) ?? new Map<string, string[]>();
    byKind.set(kind, days);
    days.set(event.date, [...(days.get(event.date) ?? []), labels.describe(event)]);
  }
  const traces: PositionEventTrace[] = [];
  for (const kind of EVENT_KIND_ORDER) {
    const days = byKind.get(kind);
    if (!days) {
      continue;
    }
    const dates = [...days.keys()].sort();
    traces.push({
      x: dates,
      y: dates.map(() => POSITION_EVENT_Y),
      text: dates.map(() => labels.letters[kind]),
      hovertext: dates.map((date) => `${date} ${labels.names[kind]}<br>${(days.get(date) ?? []).join('<br>')}`),
      name: labels.names[kind],
      type: 'scatter',
      mode: 'markers+text',
      textposition: 'top center',
      hoverinfo: 'text',
      yaxis: 'y2',
      marker: { symbol: EVENT_KIND_SYMBOL[kind], size: 8 }
    });
  }
  return traces;
}
