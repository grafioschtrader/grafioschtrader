import type { Layout, ScatterData } from 'plotly.js';
import type { SecurityPeriodPerformance, SecurityPeriodPerformanceRow } from './security-period-performance';

/** Colors of a point with a gain and with a loss; a loss is red as in the tables. */
const GAIN_COLOR = '#2e9d4f';
const LOSS_COLOR = '#d9342b';
/** Diameter in pixels of the point with the fewest and with the most days held. */
const MIN_MARKER_SIZE = 7;
const MAX_MARKER_SIZE = 26;

/** Translated labels of the chart. */
export interface SecurityPerformanceChartTexts {
  title: string;
  gain: string;
  twr: string;
  holdingDays: string;
}

/** Formatting by GT's data types, supplied by the caller so the tooltip reads like the table. */
export interface SecurityPerformanceChartFormat {
  amount(value: number): string;
  percent(value: number): string;
  days(value: number): string;
}

/** Colors and number separators of the surface the chart is drawn on. */
export interface SecurityPerformanceChartSurface {
  textColor: string;
  gridColor: string;
  /** Decimal symbol followed by the thousands separator, as Plotly expects it. */
  separators: string;
}

/** The scatter trace and the security of each of its points, in the same order. */
export interface SecurityPerformanceChartPoints {
  trace: Partial<ScatterData>;
  idsSecuritycurrency: number[];
  /** Rows without return or gain, which have no place in the chart. */
  notShown: number;
}

/**
 * One point per security: the time-weighted return of the position on the x-axis, the profit of the period in the
 * main currency on the y-axis. The point grows with the days held, so a high return over a few days is recognisable
 * as a small point. Rows without return or gain, such as margin instruments and rows with a remark, are left out and
 * only counted.
 *
 * @param rows Rows of the response
 * @param texts Translated labels
 * @param format Formatting of the tooltip figures
 * @returns The trace, the security of every point and the number of rows left out
 */
export function securityPerformanceChartPoints(
  rows: SecurityPeriodPerformanceRow[],
  texts: SecurityPerformanceChartTexts,
  format: SecurityPerformanceChartFormat
): SecurityPerformanceChartPoints {
  const shown = rows.filter(
    (row): row is SecurityPeriodPerformanceRow & { twrPercent: number; gainPeriodMC: number } =>
      row.twrPercent != null && row.gainPeriodMC != null
  );
  const maxDays = Math.max(1, ...shown.map((row) => row.holdingDays));
  return {
    trace: {
      type: 'scatter',
      mode: 'markers',
      x: shown.map((row) => row.twrPercent),
      y: shown.map((row) => row.gainPeriodMC),
      marker: {
        size: shown.map((row) => markerSize(row.holdingDays, maxDays)),
        color: shown.map((row) => (row.gainPeriodMC < 0 ? LOSS_COLOR : GAIN_COLOR)),
        opacity: 0.75,
        line: { width: 1, color: 'rgba(0,0,0,0.35)' }
      },
      customdata: shown.map((row) => [
        row.name,
        format.amount(row.gainPeriodMC),
        format.percent(row.twrPercent),
        format.days(row.holdingDays)
      ]) as unknown as ScatterData['customdata'],
      hovertemplate:
        '<b>%{customdata[0]}</b><br>' +
        `${texts.gain}: %{customdata[1]}<br>` +
        `${texts.twr}: %{customdata[2]} %<br>` +
        `${texts.holdingDays}: %{customdata[3]}<extra></extra>`
    },
    idsSecuritycurrency: shown.map((row) => row.idSecuritycurrency),
    notShown: rows.length - shown.length
  };
}

/** Translated labels of the treemap. */
export interface SecurityPerformanceTreemapTexts {
  /** Label of the root, the calculated period. */
  root: string;
  gains: string;
  losses: string;
  holdingDays: string;
  /** Label of the share of a tile in its branch. */
  share: string;
}

/** The treemap trace, the security of every node (null for root and branches) and the amounts of its branches. */
export interface SecurityPerformanceTreemap {
  trace: Record<string, unknown>;
  idsSecuritycurrency: (number | null)[];
  gainsMC: number;
  lossesMC: number;
  /** Rows without profit or loss, which have no area. */
  notShown: number;
}

/**
 * The profits and the losses of the period as one treemap with two branches. The area of a tile is the absolute
 * profit or loss of the security in the main currency. Both branches hang below one root, so their areas stand in the
 * ratio of their sums: a small loss total next to a large profit total also looks small. A click on a branch zooms into
 * it, which keeps small tiles readable. Margin instruments take part, because they have a profit; rows without profit
 * or with a profit of zero have no area and are only counted.
 *
 * @param rows Rows of the response
 * @param texts Translated labels
 * @param format Formatting of amounts and days
 * @returns The trace, the security of every node, the sums of both branches and the number of rows left out
 */
export function securityPerformanceTreemap(
  rows: SecurityPeriodPerformanceRow[],
  texts: SecurityPerformanceTreemapTexts,
  format: SecurityPerformanceChartFormat
): SecurityPerformanceTreemap {
  const withGain = rows.filter((row): row is GainRow => row.gainPeriodMC != null);
  const gains = withGain.filter((row) => row.gainPeriodMC > 0);
  const losses = withGain.filter((row) => row.gainPeriodMC < 0);
  const gainsMC = gains.reduce((sum, row) => sum + row.gainPeriodMC, 0);
  const lossesMC = losses.reduce((sum, row) => sum - row.gainPeriodMC, 0);
  const nodes: TreemapNode[] = [
    { id: 'root', parent: '', label: texts.root, value: 0, color: 'rgba(0,0,0,0)', amount: '', days: '' }
  ];
  const branch = { holdingDays: texts.holdingDays, format };
  addBranch(nodes, { id: 'gains', label: texts.gains, sumMC: gainsMC, rgb: GAIN_RGB }, gains, branch);
  addBranch(nodes, { id: 'losses', label: texts.losses, sumMC: -lossesMC, rgb: LOSS_RGB }, losses, branch);
  return {
    trace: {
      type: 'treemap',
      ids: nodes.map((node) => node.id),
      parents: nodes.map((node) => node.parent),
      labels: nodes.map((node) => node.label),
      values: nodes.map((node) => node.value),
      branchvalues: 'remainder',
      sort: true,
      // Plotly draws the borders in the paper color, which is transparent here; white matches the holdings treemap
      marker: { colors: nodes.map((node) => node.color), line: { color: '#ffffff', width: 1 } },
      customdata: nodes.map((node) => [node.amount, node.days]),
      texttemplate: '%{label}<br>%{customdata[0]}',
      hovertemplate:
        `<b>%{label}</b><br>%{customdata[0]}<br>${texts.share}: %{percentParent:.1%}` +
        '%{customdata[1]}<extra></extra>',
      pathbar: { visible: true }
    },
    idsSecuritycurrency: nodes.map((node) => node.idSecuritycurrency ?? null),
    gainsMC,
    lossesMC,
    notShown: rows.length - gains.length - losses.length
  };
}

/**
 * Layout of the treemap: the title names the period, the sums of both branches with the balance and, when rows are
 * left out, their number.
 *
 * @param title Title of the chart
 * @param subtitle Sums of the branches and the balance, already formatted
 * @param notShown Translated note on the rows left out, null when every row is drawn
 * @param surface Colors and separators of the surface
 * @returns Plotly layout
 */
export function securityPerformanceTreemapLayout(
  title: string,
  subtitle: string,
  notShown: string | null,
  surface: SecurityPerformanceChartSurface
): Partial<Layout> {
  const lines = [title, `<sup>${subtitle}</sup>`, ...(notShown ? [`<sup>${notShown}</sup>`] : [])];
  return {
    title: { text: lines.join('<br>') },
    autosize: true,
    margin: { l: 8, r: 8, t: notShown ? 84 : 64, b: 8 },
    paper_bgcolor: 'rgba(0,0,0,0)',
    font: { color: surface.textColor },
    separators: surface.separators
  };
}

/** Red, green and blue of the two branches; the tiles take them with an opacity growing with their amount. */
const GAIN_RGB = '46,157,79';
const LOSS_RGB = '217,52,43';

/** One node of the treemap before it is split into the parallel arrays Plotly expects. */
interface TreemapNode {
  id: string;
  parent: string;
  label: string;
  value: number;
  color: string;
  amount: string;
  days: string;
  idSecuritycurrency?: number;
}

/** A row that carries a profit or loss in the main currency. */
type GainRow = SecurityPeriodPerformanceRow & { gainPeriodMC: number };

/** Adds a branch and its securities; an empty branch is left out, so it takes no place in the chart. */
function addBranch(
  nodes: TreemapNode[],
  branch: { id: string; label: string; sumMC: number; rgb: string },
  rows: GainRow[],
  tiles: { holdingDays: string; format: SecurityPerformanceChartFormat }
): void {
  if (rows.length === 0) {
    return;
  }
  nodes.push({
    id: branch.id,
    parent: 'root',
    label: branch.label,
    value: 0,
    color: `rgb(${branch.rgb})`,
    amount: tiles.format.amount(branch.sumMC),
    days: ''
  });
  const largest = Math.max(...rows.map((row) => Math.abs(row.gainPeriodMC)));
  rows.forEach((row) => {
    const value = Math.abs(row.gainPeriodMC);
    nodes.push({
      id: `${branch.id}-${row.idSecuritycurrency}`,
      parent: branch.id,
      label: row.name,
      value,
      color: `rgba(${branch.rgb},${(0.45 + (0.5 * value) / largest).toFixed(2)})`,
      amount: tiles.format.amount(row.gainPeriodMC),
      days: `<br>${tiles.holdingDays}: ${tiles.format.days(row.holdingDays)}`,
      idSecuritycurrency: row.idSecuritycurrency
    });
  });
}

/** Area proportional to the days held, between the smallest and the largest point. */
function markerSize(holdingDays: number, maxDays: number): number {
  return MIN_MARKER_SIZE + (MAX_MARKER_SIZE - MIN_MARKER_SIZE) * Math.sqrt(Math.max(0, holdingDays) / maxDays);
}

/**
 * Layout with both zero lines drawn, so the four fields of the chart are visible, and transparent surfaces, so it
 * follows the light as well as the dark theme. The note on the rows left out is a smaller second line of the title,
 * where it cannot collide with the axis labels.
 *
 * @param result The calculated period, for its main currency
 * @param texts Translated labels
 * @param notShown Translated note on the rows left out, null when every row is drawn
 * @param surface Colors and separators of the surface
 * @returns Plotly layout
 */
export function securityPerformanceChartLayout(
  result: SecurityPeriodPerformance,
  texts: SecurityPerformanceChartTexts,
  notShown: string | null,
  surface: SecurityPerformanceChartSurface
): Partial<Layout> {
  const axis = {
    gridcolor: surface.gridColor,
    linecolor: surface.gridColor,
    zeroline: true,
    zerolinecolor: surface.textColor,
    zerolinewidth: 1,
    automargin: true
  };
  return {
    title: { text: notShown ? `${texts.title}<br><sup>${notShown}</sup>` : texts.title },
    autosize: true,
    margin: { l: 8, r: 16, t: notShown ? 64 : 40, b: 8 },
    paper_bgcolor: 'rgba(0,0,0,0)',
    plot_bgcolor: 'rgba(0,0,0,0)',
    font: { color: surface.textColor },
    separators: surface.separators,
    hovermode: 'closest',
    showlegend: false,
    xaxis: { ...axis, title: { text: `${texts.twr} %` }, ticksuffix: ' %' },
    yaxis: { ...axis, title: { text: `${texts.gain} ${result.mainCurrency}`, standoff: 8 } }
  };
}
