import { ValueKeyHtmlSelectOptions } from '../../lib/dynamic-form/models/value.key.html.select.options';

/**
 * Value of the `meta` property of every trace that holds transaction markers. The PDF export finds the markers by it:
 * the names of the traces are translated texts, and the order of the traces changes with the chart type.
 */
export const TRANSACTION_MARKER_META = 'gt-transaction-markers';

/**
 * Moment format of the booking time in the `customdata` of a marker. The text sorts like the time itself and equals the
 * wall-clock ISO form of the backend, so the numbers follow the order in which the server lists the transactions.
 */
export const MARKER_TIME_FORMAT = 'YYYY-MM-DDTHH:mm:ss';

/** Size of the chart in the PDF in SVG pixels. At the width of an A4 page it prints an 11 px label as about 8 pt. */
export const PRINT_FIGURE_SIZE = { width: 700, height: 420 } as const;

/** Print colours: the PDF is white paper whatever theme the screen uses. */
const PRINT_BACKGROUND = '#ffffff';
const PRINT_TEXT = '#2a3f5f';

/** Marker of a transaction in the chart and the number printed at it and in the table of the PDF. */
export interface TransactionNumber {
  idTransaction: number;
  number: number;
}

/** What the server offers in the dialog of the PDF of a time-series chart. */
export interface SecurityChartPdfOptions {
  papers: ValueKeyHtmlSelectOptions[];
  languages: ValueKeyHtmlSelectOptions[];
  numberFormats: ValueKeyHtmlSelectOptions[];
}

/** Request of the PDF of a time-series chart, see `SecurityChartPdfRequest` in the backend. */
export interface SecurityChartPdfRequest {
  idSecuritycurrency: number;
  idPortfolio: number | null;
  idsSecurityaccount: number[] | null;
  svg: string;
  paper: string;
  language: string;
  numberFormat: string;
  includeTransactions: boolean;
  transactionNumbers: TransactionNumber[];
}

/** The scope the chart was opened for: the securities accounts, the portfolio or, with neither, the whole tenant. */
export interface ChartPdfScope {
  idSecuritycurrency: number;
  idPortfolio: number | null;
  idsSecurityaccount: number[] | null;
}

/** The chart as picture and the markers it shows, ready to be sent. */
export interface ChartPdfExport {
  svg: string;
  transactionNumbers: TransactionNumber[];
}

/** The part of a Plotly trace the export reads; the rest of the trace is carried along unchanged. */
export interface PlotlyTraceLike {
  meta?: unknown;
  customdata?: unknown[];
  x?: unknown[];
  y?: unknown[];
  [property: string]: unknown;
}

/** The numbers of the markers and, per trace, the text to print at each of its points. */
export interface MarkerNumbering {
  numbers: TransactionNumber[];
  /** Index of the trace in the data array to the label of each of its points; empty text for a point without number. */
  labels: Map<number, string[]>;
}

interface MarkerPoint {
  traceIndex: number;
  pointIndex: number;
  idTransaction: number;
  time: string;
}

/**
 * Numbers the transaction markers of a chart. The markers that are visible in the chart get 1, 2, 3, ... in the order
 * of their booking, which is the order in which the server lists them in the table. A marker outside the displayed
 * date range or without a price is not drawn and therefore gets no number.
 *
 * @param traces the traces of the displayed chart; transaction markers carry {@link TRANSACTION_MARKER_META} and, per
 *               point, `customdata` of `[idTransaction, transactionTime]`
 * @param xRange the displayed range of the date axis, normally `layout.xaxis.range`; absent or not made of two date
 *               texts shows every marker
 * @returns the numbers to send and the labels to draw
 */
export function numberTransactionMarkers(
  traces: readonly PlotlyTraceLike[],
  xRange?: readonly unknown[] | null
): MarkerNumbering {
  const days = visibleDays(xRange);
  const points: MarkerPoint[] = [];
  const labels = new Map<number, string[]>();
  traces.forEach((trace, traceIndex) => {
    if (trace.meta !== TRANSACTION_MARKER_META || !Array.isArray(trace.customdata)) {
      return;
    }
    labels.set(traceIndex, new Array<string>(trace.customdata.length).fill(''));
    trace.customdata.forEach((entry, pointIndex) => {
      const point = markerPoint(traceIndex, pointIndex, entry);
      if (point != null && isDrawn(trace, pointIndex) && isVisible(trace.x?.[pointIndex], days)) {
        points.push(point);
      }
    });
  });
  points.sort((a, b) => (a.time < b.time ? -1 : a.time > b.time ? 1 : a.idTransaction - b.idTransaction));
  const numbers = points.map((point, index) => {
    const traceLabels = labels.get(point.traceIndex);
    if (traceLabels != null) {
      traceLabels[point.pointIndex] = String(index + 1);
    }
    return { idTransaction: point.idTransaction, number: index + 1 };
  });
  return { numbers, labels };
}

/**
 * Builds the figure that is rendered for the PDF from the chart on screen: its traces and layout as they are, so that
 * the zoom, the holdings line, the indicators and the drawn lines come along, changed only where print needs it. The
 * chart on screen is not touched; the figure is a copy.
 *
 * @param data the traces of the displayed chart
 * @param layout the layout of the displayed chart
 * @param numbering the numbers to print at the transaction markers, or null to print none
 * @param size the size of the figure in SVG pixels
 * @returns a figure for `Plotly.toImage`
 */
export function buildPrintFigure(
  data: readonly PlotlyTraceLike[],
  layout: Record<string, any>,
  numbering: MarkerNumbering | null,
  size: { readonly width: number; readonly height: number } = PRINT_FIGURE_SIZE
): { data: PlotlyTraceLike[]; layout: Record<string, any> } {
  const printData = clone([...data]);
  printData.forEach((trace, index) => {
    // FOP draws a group with an opacity below 1 as a bitmap and loses the text inside it, which would take away the
    // numbers of the markers. The markers of the screen are translucent; in print they are opaque.
    if (typeof trace['opacity'] === 'number' && trace['opacity'] < 1) {
      trace['opacity'] = 1;
    }
    const labels = numbering?.labels.get(index);
    if (labels != null) {
      trace['text'] = labels;
      trace['mode'] = 'markers+text';
      trace['textposition'] = 'top center';
      trace['textfont'] = { size: 9, color: PRINT_TEXT };
      delete trace['hovertext'];
    }
  });
  const printLayout: Record<string, any> = clone(layout);
  printLayout['width'] = size.width;
  printLayout['height'] = size.height;
  printLayout['autosize'] = false;
  printLayout['paper_bgcolor'] = PRINT_BACKGROUND;
  printLayout['plot_bgcolor'] = PRINT_BACKGROUND;
  printLayout['font'] = { ...printLayout['font'], color: PRINT_TEXT };
  // The range slider is an operating element of the screen and would take a third of the small figure.
  printLayout['xaxis'] = { ...printLayout['xaxis'], rangeslider: { visible: false } };
  return { data: printData, layout: printLayout };
}

/**
 * Reads the SVG text out of the data URL that `Plotly.toImage` answers with. Plotly URL-encodes an SVG; a base64
 * encoded one is accepted as well.
 *
 * @param dataUrl `data:image/svg+xml,...` as returned by `Plotly.toImage(..., {format: 'svg'})`
 * @returns the SVG document as text
 * @throws Error when the text is not a data URL
 */
export function svgFromDataUrl(dataUrl: string): string {
  const comma = dataUrl.indexOf(',');
  if (!dataUrl.startsWith('data:') || comma < 0) {
    throw new Error('Plotly did not return a data URL');
  }
  const payload = dataUrl.substring(comma + 1);
  if (dataUrl.substring(0, comma).endsWith(';base64')) {
    return new TextDecoder().decode(Uint8Array.from(atob(payload), (character) => character.charCodeAt(0)));
  }
  return decodeURIComponent(payload);
}

/** The filter of {@link numberTransactionMarkers}: two date texts, or no filter. */
function visibleDays(xRange?: readonly unknown[] | null): [string, string] | null {
  if (xRange != null && xRange.length === 2 && typeof xRange[0] === 'string' && typeof xRange[1] === 'string') {
    return [xRange[0].substring(0, 10), xRange[1].substring(0, 10)];
  }
  return null;
}

function isVisible(x: unknown, days: [string, string] | null): boolean {
  if (days == null) {
    return true;
  }
  if (typeof x !== 'string') {
    return false;
  }
  const day = x.substring(0, 10);
  return day >= days[0] && day <= days[1];
}

/** A marker without a price is a gap in the trace and is not drawn. */
function isDrawn(trace: PlotlyTraceLike, pointIndex: number): boolean {
  return trace.y == null || trace.y[pointIndex] != null;
}

function markerPoint(traceIndex: number, pointIndex: number, entry: unknown): MarkerPoint | null {
  if (!Array.isArray(entry) || typeof entry[0] !== 'number' || typeof entry[1] !== 'string') {
    return null;
  }
  return { traceIndex, pointIndex, idTransaction: entry[0], time: entry[1] };
}

function clone<T>(value: T): T {
  return JSON.parse(JSON.stringify(value)) as T;
}
