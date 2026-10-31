import moment from 'moment';
import type { Layout, ScatterData } from 'plotly.js';
import { BaseSettings } from '../../../app/lib/base.settings';
import {
  ChartTransactionPosition,
  holdingSteps,
  interpolatedClose,
  totalReturnFactorAt
} from '../../../app/historyquote/model/security.chart.series';

/** Periods of the chart, the buttons of the desktop chart; TRADE starts at the first transaction. */
export const MOBILE_CHART_RANGES = ['5D', '1M', '3M', 'YTD', '1Y', '3Y', '5Y', '10Y', 'MAX', 'TRADE'] as const;
export type MobileChartRange = (typeof MOBILE_CHART_RANGES)[number];

/** Whether a value, such as a stored or chosen one, is a period of the chart. */
export function isMobileChartRange(value: unknown): value is MobileChartRange {
  return MOBILE_CHART_RANGES.some((range) => range === value);
}

/** The fields of the chart response (backend HistoryquoteChartResponse) the mobile application reads. */
export interface MobileChartResponse {
  ohlcAvailable?: boolean;
  dateCloseList?: MobileChartClose[] | null;
  ohlcList?: MobileChartClose[] | null;
  /** The prices include the reinvested dividends. */
  totalReturn?: boolean;
  totalReturnFactors?: { exDate: string; factor: number }[] | null;
  /** Dividends were asked for, but the security distributes and none are recorded. */
  dividendDataMissing?: boolean;
}

export interface MobileChartClose {
  date: string;
  close: number | null;
}

/** A transaction as the chart reads it, with the price adjusted for later splits. */
export interface MobileChartTransactionPosition extends ChartTransactionPosition {
  transaction: ChartTransactionPosition['transaction'] & {
    quotation?: number | null;
    cashaccount?: { name?: string | null } | null;
  };
  quotationSplitCorrection?: number | null;
}

/** Translated names of the traces. */
export interface MobileChartTexts {
  price: string;
  holding: string;
  /** Names of the transaction types, by type. */
  types: Record<'ACCUMULATE' | 'REDUCE' | 'DIVIDEND', string>;
}

/** Colors and number separators of the surface the chart is drawn on. */
export interface MobileChartSurface {
  textColor: string;
  gridColor: string;
  gainColor: string;
  lossColor: string;
  separators: string;
}

/** Color of the price line, the first slot GT's charts use. */
const PRICE_COLOR = '#2a78d6';
/** Color of the held units, the one of the desktop chart. */
const HOLDING_COLOR = 'rgb(148, 103, 189)';
const DIVIDEND_COLOR = '#eb6834';

/**
 * First day of a period. A start on a weekend moves to the following Monday, as on the desktop.
 *
 * @param range Period chosen
 * @param today Today, short native format
 * @param firstTrade Date of the first transaction, short native format; null without transactions
 * @returns First day shown, or null for the whole history
 */
export function mobileChartFromDate(range: MobileChartRange, today: string, firstTrade: string | null): string | null {
  const day = moment(today, BaseSettings.FORMAT_DATE_SHORT_NATIVE);
  let from: moment.Moment;
  switch (range) {
    case 'MAX':
      return null;
    case 'TRADE':
      return firstTrade;
    case '5D':
      from = day.add(-7, 'days');
      break;
    case '1M':
      from = day.add(-1, 'month');
      break;
    case '3M':
      from = day.add(-3, 'month');
      break;
    case 'YTD':
      from = day.startOf('year').add(1, 'days');
      break;
    case '1Y':
      from = day.add(-1, 'year');
      break;
    case '3Y':
      from = day.add(-3, 'year');
      break;
    case '5Y':
      from = day.add(-5, 'year');
      break;
    case '10Y':
      from = day.add(-10, 'year');
      break;
  }
  if (from.day() % 6 === 0) {
    from = from.add(1, 'day').day(1);
  }
  return from.format(BaseSettings.FORMAT_DATE_SHORT_NATIVE);
}

/** The closes of a chart response, from the OHLC list when the instrument has one. */
export function mobileChartCloses(response: MobileChartResponse): MobileChartClose[] {
  return (response.ohlcAvailable ? response.ohlcList : response.dateCloseList) ?? [];
}

/** Date of the first transaction, short native format; null without transactions. */
export function mobileChartFirstTrade(positions: MobileChartTransactionPosition[]): string | null {
  const first = positions[0]?.transaction.transactionTime;
  return first == null ? null : moment(first).format(BaseSettings.FORMAT_DATE_SHORT_NATIVE);
}

/** A series on the price axis, kept to fit that axis to the window shown. */
export interface MobilePriceSeries {
  x: string[];
  y: (number | null)[];
}

/** The traces of the chart and what the page needs to move its window. */
export interface MobileChart {
  traces: Partial<ScatterData>[];
  /** The price line and the markers, which share the left axis. */
  priceSeries: MobilePriceSeries[];
  /** First day with a price, short native format; null without prices. */
  firstDate: string | null;
  /** The units held are drawn, so the layout needs their axis. */
  withHolding: boolean;
}

/**
 * The price chart of one instrument over its whole history: the price line, a marker for each purchase and sale at
 * its price adjusted for later splits, a marker for each dividend on the line, and the units held on a second axis.
 * The whole history is drawn so the window can be moved into the past; the period chosen only sets that window. In
 * total return mode a trade price is scaled like the line, so the marker stays on it.
 *
 * @param response Chart response of the instrument
 * @param positions Transactions of the tenant, oldest first; empty for a currency pair or an instrument never traded
 * @param today Last day of the history, short native format
 * @param withHolding Whether the units held are drawn
 * @param texts Translated trace names
 * @param surface Colors of the surface
 * @returns The traces, the held units last
 */
export function mobileChart(
  response: MobileChartResponse,
  positions: MobileChartTransactionPosition[],
  today: string,
  withHolding: boolean,
  texts: MobileChartTexts,
  surface: MobileChartSurface
): MobileChart {
  const closes = mobileChartCloses(response);
  let start = 0;
  // The line begins at the first price; a missing price stays a gap later on.
  while (start < closes.length && closes[start].close == null) {
    start++;
  }
  const shown = closes.slice(start);
  const x = shown.map((c) => c.date);
  const y = shown.map((c) => c.close);
  const firstDay = x[0] ?? today;
  const factors = response.totalReturn ? (response.totalReturnFactors ?? []) : [];
  const priceSeries: MobilePriceSeries[] = [{ x, y }];
  const traces: Partial<ScatterData>[] = [
    {
      type: 'scatter',
      mode: 'lines',
      name: texts.price,
      x,
      y,
      line: { color: PRICE_COLOR, width: 1.5 },
      hovertemplate: '%{y}<extra></extra>'
    }
  ];
  const colors = { ACCUMULATE: surface.gainColor, REDUCE: surface.lossColor, DIVIDEND: DIVIDEND_COLOR };
  for (const type of ['ACCUMULATE', 'REDUCE', 'DIVIDEND'] as const) {
    const marker = markerTrace(positions, type, firstDay, x, y, factors);
    if (marker.x.length) {
      priceSeries.push({ x: marker.x, y: marker.y });
      traces.push({
        type: 'scatter',
        mode: 'markers',
        name: texts.types[type],
        x: marker.x,
        y: marker.y,
        text: marker.text,
        marker: {
          color: colors[type],
          size: type === 'DIVIDEND' ? 7 : 10,
          line: { width: 1, color: surface.textColor }
        },
        // The tooltip of a day also lists the nearest marker, so the marker names its own date.
        hovertemplate: '%{x|%x}: %{y} %{text}<extra>' + texts.types[type] + '</extra>'
      });
    }
  }
  const steps = withHolding ? holdingSteps(positions, firstDay, today) : { x: [], y: [] };
  if (steps.x.length) {
    traces.push({
      type: 'scatter',
      mode: 'lines',
      name: texts.holding,
      x: steps.x,
      y: steps.y,
      yaxis: 'y2',
      line: { color: HOLDING_COLOR, width: 1, dash: 'dashdot' },
      hovertemplate: '%{y}<extra>' + texts.holding + '</extra>'
    });
  }
  return { traces, priceSeries, firstDate: x[0] ?? null, withHolding: steps.x.length > 0 };
}

/**
 * The window shown for a period: from its first day, or from the first price for the whole history, to today.
 *
 * @param fromDate First day of the period, null for the whole history
 * @param firstDate First day with a price
 * @param today Today, short native format
 * @returns First and last day of the window
 */
export function mobileChartWindow(fromDate: string | null, firstDate: string | null, today: string): [string, string] {
  return [fromDate ?? firstDate ?? today, today];
}

/**
 * The window after Plotly moved it: by a finger, which reports both ends, by the range slider, which reports the
 * range, or by a double tap, which returns to the whole history. A change of an axis height carries no window.
 *
 * @param event The relayout event of Plotly
 * @param whole The window of the whole history
 * @returns The window, or null when the event did not move it
 */
export function mobileChartRelayoutWindow(event: object, whole: [string, string]): [string, string] | null {
  const values = new Map<string, unknown>(Object.entries(event));
  const start = values.get('xaxis.range[0]');
  const end = values.get('xaxis.range[1]');
  if (typeof start === 'string' && typeof end === 'string') {
    return [start, end];
  }
  const range = values.get('xaxis.range');
  if (Array.isArray(range) && typeof range[0] === 'string' && typeof range[1] === 'string') {
    return [range[0], range[1]];
  }
  return values.get('xaxis.autorange') === true ? whole : null;
}

/**
 * The range of the price axis that fits the prices and markers within a window, with a little room above and below.
 * Plotly would otherwise scale the axis to the whole history, and a short window would show an almost flat line.
 *
 * @param series The series on the price axis
 * @param from First day of the window; a date with time from Plotly is compared by its date
 * @param to Last day of the window
 * @returns The range, or null when the window holds no price
 */
export function mobileChartPriceRange(series: MobilePriceSeries[], from: string, to: string): [number, number] | null {
  const first = from.slice(0, 10);
  const last = to.slice(0, 10);
  let min = Number.POSITIVE_INFINITY;
  let max = Number.NEGATIVE_INFINITY;
  for (const s of series) {
    s.x.forEach((date, i) => {
      const value = s.y[i];
      if (value != null && date >= first && date <= last) {
        min = Math.min(min, value);
        max = Math.max(max, value);
      }
    });
  }
  if (min > max) {
    return null;
  }
  const room = (max - min) * 0.05 || Math.abs(max) * 0.01 || 1;
  return [min - room, max + room];
}

/** Markers of one transaction type from the first price on. */
function markerTrace(
  positions: MobileChartTransactionPosition[],
  type: 'ACCUMULATE' | 'REDUCE' | 'DIVIDEND',
  firstDay: string,
  x: string[],
  y: (number | null)[],
  factors: { exDate: string; factor: number }[]
): { x: string[]; y: (number | null)[]; text: string[] } {
  const marker = { x: [] as string[], y: [] as (number | null)[], text: [] as string[] };
  for (const position of positions) {
    const transaction = position.transaction;
    if (transaction.transactionType !== type) {
      continue;
    }
    const dateStr = moment(transaction.transactionTime).format(BaseSettings.FORMAT_DATE_SHORT_NATIVE);
    if (dateStr < firstDay) {
      continue;
    }
    let price: number | null;
    if (type === 'DIVIDEND') {
      // A dividend has no price of its own; it sits on the line at its date.
      price = interpolatedClose(y, insertionIndex(x, dateStr));
    } else {
      const quotation = position.quotationSplitCorrection ?? transaction.quotation;
      if (quotation == null) {
        continue;
      }
      price = quotation * totalReturnFactorAt(factors, dateStr);
    }
    marker.x.push(dateStr);
    marker.y.push(price);
    marker.text.push(transaction.cashaccount?.name ?? '');
  }
  return marker;
}

/** Index of a date in the ascending dates of the line, or of the first later date. */
function insertionIndex(dates: string[], dateStr: string): number {
  const index = dates.findIndex((d) => d >= dateStr);
  return index < 0 ? dates.length - 1 : index;
}

/**
 * Layout of the price chart with transparent surfaces, so it follows the light and the dark theme. One finger moves
 * the window sideways, and the range slider below shows the whole history and widens or narrows the window at its
 * handles. The axes keep their height; the page fits the price axis to the window after each move. The units held
 * have their own axis on the right. One tap shows every value of that day together.
 *
 * @param currency Currency of the prices, the title of the left axis
 * @param priceFractionDigits Fraction digits of the prices the backend decided on; null for two
 * @param withHolding Whether the units held are shown
 * @param span First and last day shown
 * @param priceRange Range of the price axis for that window; null to let Plotly scale it
 * @param surface Colors and separators of the surface
 * @returns Plotly layout
 */
export function mobileChartLayout(
  currency: string,
  priceFractionDigits: number | null,
  withHolding: boolean,
  span: [string, string],
  priceRange: [number, number] | null,
  surface: MobileChartSurface
): Partial<Layout> {
  const axis = { gridcolor: surface.gridColor, linecolor: surface.gridColor, automargin: true };
  return {
    autosize: true,
    margin: { l: 8, r: withHolding ? 8 : 16, t: 30, b: 8 },
    paper_bgcolor: 'rgba(0,0,0,0)',
    plot_bgcolor: 'rgba(0,0,0,0)',
    font: { color: surface.textColor },
    separators: surface.separators,
    hovermode: 'x unified',
    dragmode: 'pan',
    showlegend: true,
    legend: { orientation: 'h', x: 0, y: 1.02, yanchor: 'bottom' },
    // The tooltip names the day in the date format of the locale and the price with its own fraction digits.
    xaxis: {
      ...axis,
      type: 'date',
      hoverformat: '%x',
      autorange: false,
      range: span,
      rangeslider: { visible: true, thickness: 0.12, bordercolor: surface.gridColor, borderwidth: 1 }
    },
    yaxis: {
      ...axis,
      title: { text: currency, standoff: 8 },
      hoverformat: `,.${priceFractionDigits ?? 2}f`,
      fixedrange: true,
      ...(priceRange ? { autorange: false, range: priceRange } : { autorange: true })
    },
    ...(withHolding
      ? {
          yaxis2: {
            overlaying: 'y',
            side: 'right',
            showgrid: false,
            rangemode: 'tozero',
            fixedrange: true,
            // Its own ticks; aligned to the price grid the units would read as fractions.
            tickmode: 'auto',
            automargin: true,
            tickfont: { color: HOLDING_COLOR },
            hoverformat: ',~f'
          }
        }
      : {})
  };
}
