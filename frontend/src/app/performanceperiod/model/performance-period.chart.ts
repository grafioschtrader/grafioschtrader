import type { Data, Layout } from 'plotly.js';
import { PlotlyHelper } from '../../shared/chart/plotly.helper';
import type { PerformanceBenchmarkResult, PerformanceChartDayDiff, PerformancePeriod } from './performance.period';

/** The views of the period performance chart. The keys are also their translation keys. */
export enum PerformancePeriodChartMode {
  /** Changes of the balance since the first day, the chart the view always had. */
  AMOUNTS = 'AMOUNTS',
  /** Cumulative time-weighted returns of the portfolio and of its benchmark, only with a benchmark. */
  RETURNS_PERCENT = 'RETURNS_PERCENT',
  /** Value of the portfolio next to that of its shadow portfolio, only with a benchmark. */
  WEALTH = 'WEALTH'
}

/** Translated names of the traces, keyed by the field they show. */
export type PerformanceChartNames = Record<string, string>;

/** The amount fields of a chart day, in the order of the traces. */
export const AMOUNT_FIELDS: readonly (keyof Omit<PerformanceChartDayDiff, 'date'>)[] = [
  'externalCashTransferDiffMC',
  'gainDiffMC',
  'cashBalanceDiffMC',
  'securitiesDiffMC',
  'totalBalanceMC'
];

/** The return fields of a benchmark chart point, in the order of the traces. */
export const RETURN_FIELDS = ['portfolioTwrPercent', 'benchmarkTwrPercent'] as const;

/** The value fields of a benchmark chart point, in the order of the traces. */
export const WEALTH_FIELDS = ['portfolioValueMC', 'shadowValueMC'] as const;

/**
 * The modes the chart offers for a period: the cumulative returns and the values beside the shadow portfolio only when
 * the report carries a benchmark with a comparison span.
 *
 * @param period - The calculated period
 * @returns the selectable modes
 */
export function performanceChartModes(period: PerformancePeriod | null): PerformancePeriodChartMode[] {
  return period?.benchmark?.chart?.length && period.benchmark.metrics
    ? [
        PerformancePeriodChartMode.AMOUNTS,
        PerformancePeriodChartMode.RETURNS_PERCENT,
        PerformancePeriodChartMode.WEALTH
      ]
    : [PerformancePeriodChartMode.AMOUNTS];
}

/**
 * Traces of the changes of the balance since the first day of the period.
 *
 * @param period - The calculated period
 * @param names - Translated trace names by field
 * @returns one line per amount field
 */
export function amountTraces(period: PerformancePeriod, names: PerformanceChartNames): Data[] {
  const days = period.performanceChartDayDiff;
  return AMOUNT_FIELDS.map((field) => ({
    type: 'scatter',
    mode: 'lines',
    name: names[field] ?? field,
    x: days.map((day) => day.date),
    y: days.map((day) => day[field])
  }));
}

/**
 * Traces of the cumulative returns of the portfolio and its benchmark. A null value stays a gap, so a day outside the
 * comparison span or without portfolio capital is never drawn as a measured zero.
 *
 * @param period - The calculated period with a benchmark
 * @param names - Translated trace names by field
 * @returns the portfolio line and the benchmark line
 */
export function returnTraces(period: PerformancePeriod, names: PerformanceChartNames): Data[] {
  return benchmarkTraces(period.benchmark ?? null, names, RETURN_FIELDS);
}

/**
 * Traces of the value of the portfolio and of its shadow portfolio. Both start with the same value on the first day of
 * the comparison; the shadow line ends at a withdrawal it could not fund.
 *
 * @param period - The calculated period with a benchmark
 * @param names - Translated trace names by field
 * @returns the portfolio line and the shadow line
 */
export function wealthTraces(period: PerformancePeriod, names: PerformanceChartNames): Data[] {
  return benchmarkTraces(period.benchmark ?? null, names, WEALTH_FIELDS);
}

/**
 * Traces of selected fields of the chart points of a comparison, shared by the period performance and the comparison
 * of a historical replay. A null value stays a gap.
 *
 * @param benchmark - The comparison, null without one
 * @param names - Translated trace names by field
 * @param fields - The return fields or the value fields of a chart point
 * @returns one line per field
 */
export function benchmarkTraces(
  benchmark: PerformanceBenchmarkResult | null,
  names: PerformanceChartNames,
  fields: readonly (typeof RETURN_FIELDS)[number][] | readonly (typeof WEALTH_FIELDS)[number][]
): Data[] {
  const points = benchmark?.chart ?? [];
  return fields.map((field) => ({
    type: 'scatter',
    mode: 'lines',
    connectgaps: false,
    name: names[field] ?? field,
    x: points.map((point) => point.date),
    y: points.map((point) => point[field])
  }));
}

/**
 * Layout shared by both modes: legend below the chart and a range slider over the whole period.
 *
 * @param period - The calculated period
 * @param percent - Whether the y axis shows percentages
 * @returns the Plotly layout
 */
export function performanceChartLayout(period: PerformancePeriod, percent: boolean): Partial<Layout> {
  const days = period.performanceChartDayDiff;
  const first = days.length ? days[0].date : undefined;
  const last = days.length ? days[days.length - 1].date : undefined;
  return {
    legend: PlotlyHelper.getLegendUnderChart(12),
    hovermode: 'closest',
    xaxis: {
      autorange: true,
      type: 'date',
      rangeslider: first && last ? { range: [first, last] } : {}
    },
    yaxis: percent ? { ticksuffix: ' %' } : {}
  };
}
