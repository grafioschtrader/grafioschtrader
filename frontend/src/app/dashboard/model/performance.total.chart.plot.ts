import type { Layout, ScatterData } from 'plotly.js';
import { PerformanceTotalChart, PerformanceTotalChartPoint } from './performance.total.chart';

/** Line colors in a fixed order, the same first two slots the other charts of GT use. */
const TOTAL_COLOR = '#2a78d6';
const INVESTED_COLOR = '#eb6834';
const SHADOW_COLOR = '#1baa7d';

/** Translated legend and tooltip labels of the chart. */
export interface PerformanceTotalChartTexts {
  totalBalance: string;
  investedCapital: string;
  difference: string;
  /** Label of the shadow line, with the name of the benchmark; without it no shadow line is drawn. */
  shadow?: string;
}

/** Formatting by GT's data types, supplied by the caller so the chart reads like the tables around it. */
export interface PerformanceTotalChartFormat {
  date(point: PerformanceTotalChartPoint): string;
  amount(value: number): string;
}

/** Colors and number separators of the surface the chart is drawn on. */
export interface PerformanceTotalChartSurface {
  textColor: string;
  gridColor: string;
  /** Decimal symbol followed by the thousands separator, as Plotly expects it. */
  separators: string;
}

/**
 * The lines of the total-value chart, shared by the dashboard card of the desktop and the mobile application: total
 * value, invested capital and, with a benchmark, the shadow portfolio. The total value carries the tooltip for all
 * lines, so a reader sees the day, the values and their difference in one place.
 *
 * @param points Points of the chart, oldest first
 * @param texts Translated labels
 * @param format Formatting of the tooltip figures
 * @returns Plotly traces
 */
export function performanceTotalChartTraces(
  points: PerformanceTotalChartPoint[],
  texts: PerformanceTotalChartTexts,
  format: PerformanceTotalChartFormat
): Partial<ScatterData>[] {
  const x = points.map((p) => p.date);
  const withShadow = !!texts.shadow && points.some((p) => p.shadowValueMC != null);
  const customdata = points.map((p) => [
    format.date(p),
    format.amount(p.totalBalanceMC),
    format.amount(p.investedCapitalMC),
    format.amount(p.totalBalanceMC - p.investedCapitalMC),
    p.shadowValueMC == null ? '–' : format.amount(p.shadowValueMC)
  ]);
  const traces: Partial<ScatterData>[] = [
    {
      type: 'scatter',
      mode: 'lines',
      name: texts.totalBalance,
      x,
      y: points.map((p) => p.totalBalanceMC),
      line: { color: TOTAL_COLOR, width: 2 },
      customdata,
      hovertemplate:
        '%{customdata[0]}<br>' +
        `${texts.totalBalance}: %{customdata[1]}<br>` +
        `${texts.investedCapital}: %{customdata[2]}<br>` +
        `${texts.difference}: %{customdata[3]}` +
        (withShadow ? `<br>${texts.shadow}: %{customdata[4]}` : '') +
        '<extra></extra>'
    },
    {
      type: 'scatter',
      mode: 'lines',
      name: texts.investedCapital,
      x,
      y: points.map((p) => p.investedCapitalMC),
      line: { color: INVESTED_COLOR, width: 2, shape: 'hv' },
      hoverinfo: 'skip'
    }
  ];
  if (withShadow) {
    // A null value stays a gap: no shadow before its first covered day and after a shortfall
    traces.push({
      type: 'scatter',
      mode: 'lines',
      name: texts.shadow,
      x,
      y: points.map((p) => p.shadowValueMC ?? null),
      connectgaps: false,
      line: { color: SHADOW_COLOR, width: 2, dash: 'dot' },
      hoverinfo: 'skip'
    });
  }
  return traces;
}

/**
 * Label of the shadow line: the translated name of the line followed by the benchmark name.
 *
 * @param label Translated name of the shadow line
 * @param benchmarkName Name of the benchmark, absent without one
 * @returns the label, undefined without a benchmark so that no shadow line is drawn
 */
export function shadowLabel(label: string, benchmarkName: string | null | undefined): string | undefined {
  return benchmarkName ? `${label} (${benchmarkName})` : undefined;
}

/**
 * Layout of the total-value chart with transparent surfaces, so it follows the light as well as the dark theme.
 * Plotly's own tick format shortens large values, 200'000 to 200k; the currency is the axis title, so the ticks stay
 * short.
 *
 * @param chart The chart shown, for its currency
 * @param surface Colors and separators of the surface
 * @returns Plotly layout
 */
export function performanceTotalChartLayout(
  chart: PerformanceTotalChart,
  surface: PerformanceTotalChartSurface
): Partial<Layout> {
  return {
    autosize: true,
    margin: { l: 8, r: 16, t: 30, b: 8 },
    paper_bgcolor: 'rgba(0,0,0,0)',
    plot_bgcolor: 'rgba(0,0,0,0)',
    font: { color: surface.textColor },
    separators: surface.separators,
    hovermode: 'x',
    showlegend: true,
    legend: { orientation: 'h', x: 0, y: 1.02, yanchor: 'bottom' },
    xaxis: { type: 'date', gridcolor: surface.gridColor, linecolor: surface.gridColor, automargin: true },
    yaxis: {
      title: { text: chart.currency, standoff: 8 },
      gridcolor: surface.gridColor,
      linecolor: surface.gridColor,
      automargin: true
    }
  };
}
