import type { SecurityPositionCurrencySeries } from '../../performanceperiod/model/security-position-series';

/** A line or area trace of the position chart. */
export interface PositionLineTrace {
  x: string[];
  y: (number | null)[];
  name: string;
  type: 'scatter';
  mode: 'lines';
  line: { color?: string; width?: number; dash?: string; shape?: 'linear' | 'hv' };
  connectgaps: false;
  fill?: 'tozeroy' | 'tonexty';
  fillcolor?: string;
  hoverinfo?: 'skip';
  text?: string[];
  hovertemplate?: string;
  showlegend?: boolean;
  visible?: true | 'legendonly';
}

/** Translated names of the traces of the value chart and the drawdown chart. */
export interface PositionChartTexts {
  value: string;
  invested: string;
  valueWithIncome: string;
  purchases: string;
  drawdown: string;
  priceDrawdown: string;
}

/** A faint comparison line beside an area, for example the change of the price. */
export interface PositionComparison {
  values: (number | null)[];
  name: string;
}

/** A label at the right edge of the plot that names the last value of a line, as on a price board. */
export interface PositionEdgeLabel {
  y: number;
  text: string;
  color: string;
}

/** Colors and number separators of the active theme. */
export interface PositionChartSurface {
  textColor: string;
  gridColor: string;
  separators: string;
}

const POSITIVE_FILL = 'rgba(46, 160, 67, 0.35)';
const NEGATIVE_FILL = 'rgba(208, 52, 44, 0.35)';
const NEGATIVE_LINE = 'rgb(208, 52, 44)';
const BETWEEN_FILL = 'rgba(31, 119, 180, 0.12)';
const FAINT_LINE = 'rgba(128, 128, 128, 0.7)';
/** Color of the value line and of its edge label. */
export const VALUE_LINE = 'rgb(31, 119, 180)';
/** Color of the invested capital line and of its edge label. */
export const INVESTED_LINE = 'rgb(120, 120, 120)';

/** A trace that carries the hover of a point; the area traces beside it skip the hover. */
function hoverLine(dates: string[], values: (number | null)[], name: string, hover: string[], color: string) {
  const trace: PositionLineTrace = {
    x: dates,
    y: values,
    name,
    type: 'scatter',
    mode: 'lines',
    line: { color, width: 1.5 },
    connectgaps: false,
    text: hover,
    hovertemplate: '%{text}<extra></extra>'
  };
  return trace;
}

function faintLine(dates: string[], values: (number | null)[], name: string): PositionLineTrace {
  return {
    x: dates,
    y: values,
    name,
    type: 'scatter',
    mode: 'lines',
    line: { color: FAINT_LINE, width: 1, dash: 'dot' },
    connectgaps: false,
    hoverinfo: 'skip'
  };
}

/**
 * Splits a series at 0 into a positive and a negative part for two filled areas. At every sign change a point on 0 is
 * inserted at the interpolated time, so the color changes exactly where the line crosses 0. A null stays a gap.
 *
 * @param dates - Dates of the points, yyyy-MM-dd
 * @param values - Values of the points
 * @returns the dates with the inserted crossings and the two parts, each 0 where the other one is drawn
 */
export function splitAtZero(
  dates: string[],
  values: (number | null)[]
): { x: string[]; positive: (number | null)[]; negative: (number | null)[] } {
  const x: string[] = [];
  const positive: (number | null)[] = [];
  const negative: (number | null)[] = [];
  const push = (date: string, value: number | null) => {
    x.push(date);
    positive.push(value == null ? null : Math.max(value, 0));
    negative.push(value == null ? null : Math.min(value, 0));
  };
  for (let i = 0; i < values.length; i++) {
    const previous = i > 0 ? values[i - 1] : null;
    const current = values[i];
    if (previous != null && current != null && previous * current < 0) {
      const from = Date.parse(dates[i - 1]);
      const to = Date.parse(dates[i]);
      const crossing = new Date(from + ((to - from) * previous) / (previous - current));
      push(crossing.toISOString().replace('T', ' ').substring(0, 19), 0);
    }
    push(dates[i], current);
  }
  return { x, positive, negative };
}

/**
 * Value against invested capital: the area between them is the unrealized result. Value including income and
 * realized result and the cost of all purchases are offered in the legend, hidden until clicked.
 */
export function positionValueTraces(
  dates: string[],
  series: SecurityPositionCurrencySeries,
  texts: PositionChartTexts,
  hover: string[]
): PositionLineTrace[] {
  const withIncome = series.totalResult.map((result, i) => {
    const invested = series.invested[i];
    return result == null || invested == null ? null : invested + result;
  });
  return [
    {
      x: dates,
      y: series.invested,
      name: texts.invested,
      type: 'scatter',
      mode: 'lines',
      line: { color: INVESTED_LINE, width: 1.5, shape: 'hv' },
      connectgaps: false,
      hoverinfo: 'skip'
    },
    { ...hoverLine(dates, series.value, texts.value, hover, VALUE_LINE), fill: 'tonexty', fillcolor: BETWEEN_FILL },
    { ...faintLine(dates, withIncome, texts.valueWithIncome), visible: 'legendonly' },
    {
      ...faintLine(dates, series.purchases, texts.purchases),
      line: { color: FAINT_LINE, width: 1, dash: 'dash', shape: 'hv' },
      visible: 'legendonly'
    }
  ];
}

/**
 * A result or a percentage as an area, green above 0 and red below, with an optional faint comparison line. Serves the
 * result chart in money and the performance chart in percent.
 *
 * @param dates - Dates of the points
 * @param values - The result or the percentage per point
 * @param name - Name of the series
 * @param comparison - Faint line beside the area, null for none
 * @param hover - Hover text per point
 */
export function positionAreaTraces(
  dates: string[],
  values: (number | null)[],
  name: string,
  comparison: PositionComparison | null,
  hover: string[]
): PositionLineTrace[] {
  const split = splitAtZero(dates, values);
  const area = (y: (number | null)[], color: string): PositionLineTrace => ({
    x: split.x,
    y,
    name,
    type: 'scatter',
    mode: 'lines',
    line: { width: 0 },
    connectgaps: false,
    fill: 'tozeroy',
    fillcolor: color,
    hoverinfo: 'skip',
    showlegend: false
  });
  const traces = [
    area(split.positive, POSITIVE_FILL),
    area(split.negative, NEGATIVE_FILL),
    hoverLine(dates, values, name, hover, FAINT_LINE)
  ];
  if (comparison) {
    traces.push(faintLine(dates, comparison.values, comparison.name));
  }
  return traces;
}

/** The drawdown as a red area from 0 downwards with the decline of the price as a faint line. */
export function positionDrawdownTraces(
  dates: string[],
  series: SecurityPositionCurrencySeries,
  texts: PositionChartTexts,
  hover: string[]
): PositionLineTrace[] {
  return [
    {
      ...hoverLine(dates, series.drawdown, texts.drawdown, hover, NEGATIVE_LINE),
      fill: 'tozeroy',
      fillcolor: NEGATIVE_FILL
    },
    faintLine(dates, series.priceDrawdown, texts.priceDrawdown)
  ];
}

/**
 * The value range of the lines within a date window, padded by 5 % on both sides. Plotly keeps the value axis of the
 * whole series when the date window is moved; this range lets the visible part fill the height of the plot.
 *
 * @param traces - The traces of the plot; hidden ones and those on another axis are skipped by the caller
 * @param from - First date of the window, as Plotly reports it
 * @param to - Last date of the window
 * @param includeZero - Keep 0 in the range, for the areas that are filled to 0
 * @returns the range, or null when no point lies in the window
 */
export function visibleValueRange(
  traces: { x: string[]; y: (number | null)[] }[],
  from: string,
  to: string,
  includeZero: boolean
): [number, number] | null {
  let min = includeZero ? 0 : Number.POSITIVE_INFINITY;
  let max = includeZero ? 0 : Number.NEGATIVE_INFINITY;
  let found = false;
  for (const trace of traces) {
    trace.x.forEach((date, i) => {
      const value = trace.y[i];
      if (value != null && date >= from.substring(0, 10) && date <= to) {
        min = Math.min(min, value);
        max = Math.max(max, value);
        found = true;
      }
    });
  }
  if (!found) {
    return null;
  }
  const padding = (max - min) * 0.05 || Math.abs(max) * 0.05 || 1;
  return [min - padding, max + padding];
}

/**
 * Layout of the position chart: dates with a range slider below, the markers on a hidden second axis at the bottom,
 * the legend above the plot, and labels of the last values at the right edge.
 *
 * @param yTitle - Title of the value axis, for example the currency or "% since start of phase"
 * @param percent - True for a percentage axis
 * @param edgeLabels - Labels of the last values, placed right of the plot
 * @param surface - Colors and separators of the theme
 */
export function positionChartLayout(
  yTitle: string,
  percent: boolean,
  edgeLabels: PositionEdgeLabel[],
  surface: PositionChartSurface
) {
  return {
    margin: { t: 30, r: edgeLabels.length ? 95 : 20, b: 30, l: 70 },
    paper_bgcolor: 'rgba(0,0,0,0)',
    plot_bgcolor: 'rgba(0,0,0,0)',
    font: { color: surface.textColor },
    separators: surface.separators,
    hovermode: 'closest',
    showlegend: true,
    legend: { orientation: 'h', x: 0, y: 1.08 },
    xaxis: { type: 'date', gridcolor: surface.gridColor, rangeslider: { visible: true, thickness: 0.06 } },
    yaxis: { title: { text: yTitle }, gridcolor: surface.gridColor, ticksuffix: percent ? ' %' : '' },
    yaxis2: { overlaying: 'y', range: [0, 1], visible: false, fixedrange: true },
    annotations: edgeLabels.map((label) => ({
      xref: 'paper',
      x: 1,
      xanchor: 'left',
      yref: 'y',
      y: label.y,
      text: label.text,
      showarrow: false,
      font: { color: '#ffffff', size: 11 },
      bgcolor: label.color,
      borderpad: 3
    }))
  };
}
