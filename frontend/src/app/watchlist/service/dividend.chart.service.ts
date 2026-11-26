import { inject, Service } from '@angular/core';
import { Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { MenuItem } from '@openng/optimus-ui/api';
import { Subscription } from 'rxjs';
import { BaseSettings } from '../../lib/base.settings';
import { AppSettings } from '../../shared/app.settings';
import { ChartDataService } from '../../shared/chart/service/chart.data.service';
import { ChartData, PlotlyHelper } from '../../shared/chart/plotly.helper';
import { DividendService } from './dividend.service';
import type {
  DividendChartSeries,
  SecurityDividendChart
} from '../../entities/view/securitydividends/security.dividend.metrics';

/**
 * Shows the dividend chart of one or several securities in the general purpose chart of the bottom area. It follows
 * the pattern of the line chart: "Dividend chart" starts the chart with one security, "Add to dividend chart" adds a
 * further one while the chart is shown.
 *
 * With one security the chart shows its annual dividends as bars, its trailing yield as a line on a second axis and
 * its splits as a trace of vertical lines, which the legend can hide. With several securities it compares them by
 * measures free of a unit — the trailing yield, and the annual dividend indexed to a common base year — so no
 * currency is converted. The series are computed by the backend.
 *
 * The service is the data provider of the chart rather than the box that offers the menu, so the chart keeps working
 * when the expanded row of the box is collapsed.
 */
@Service()
export class DividendChartService {
  /** Most securities of one chart, matching the backend limit */
  static readonly MAX_SECURITIES = 10;

  /** Default Plotly colours, one per compared security */
  private static readonly COLORS = [
    '#1f77b4',
    '#ff7f0e',
    '#2ca02c',
    '#d62728',
    '#9467bd',
    '#8c564b',
    '#e377c2',
    '#7f7f7f',
    '#bcbd22',
    '#17becf'
  ];

  /** Bar width of one annual sum on the date axis, 70 % of a year in milliseconds */
  private static readonly BAR_WIDTH_MS = 0.7 * 365.25 * 24 * 3600 * 1000;

  private readonly router = inject(Router);
  private readonly chartDataService = inject(ChartDataService);
  private readonly dividendService = inject(DividendService);
  private readonly translateService = inject(TranslateService);

  /** Securities of the chart in the order they were added */
  private ids: number[] = [];
  /** Load of the chart series in progress, replaced by a newer request */
  private loadSubscription?: Subscription;

  constructor() {
    this.chartDataService.requestFromChart$.subscribe((id) => this.answerRequest(id));
  }

  /**
   * Menu items of a security: show the dividend chart with this security alone, or add it to the shown chart.
   *
   * @param idSecuritycurrency - The security of the box that offers the menu
   */
  getMenuItems(idSecuritycurrency: number): MenuItem[] {
    const shown = this.isChartShown();
    return [
      { label: 'DIVIDEND_METRICS_CHART', command: () => this.showChart(idSecuritycurrency) },
      {
        label: 'ADD_TO_DIVIDEND_CHART',
        command: () => this.addToChart(idSecuritycurrency),
        disabled:
          !shown || this.ids.includes(idSecuritycurrency) || this.ids.length >= DividendChartService.MAX_SECURITIES
      }
    ];
  }

  /** Shows the dividend chart with this security alone. */
  showChart(idSecuritycurrency: number): void {
    this.navigate([idSecuritycurrency]);
  }

  /** Adds the security to the shown dividend chart; without a shown chart it starts one. */
  addToChart(idSecuritycurrency: number): void {
    if (!this.isChartShown()) {
      this.showChart(idSecuritycurrency);
    } else if (!this.ids.includes(idSecuritycurrency) && this.ids.length < DividendChartService.MAX_SECURITIES) {
      this.navigate([...this.ids, idSecuritycurrency]);
    }
  }

  private isChartShown(): boolean {
    return this.ids.length > 0 && this.chartDataService.isChartOfIdShown(this.chartId(this.ids));
  }

  /** The chart id carries the securities, so every change of the selection changes the route of the chart. */
  private chartId(ids: number[]): string {
    return AppSettings.DIVIDEND_METRICS_CHART + ids.join('-');
  }

  private navigate(ids: number[]): void {
    const id = this.chartId(ids);
    this.ids = ids;
    if (this.chartDataService.isChartOfIdShown(id)) {
      this.load(id, ids);
    } else {
      this.router.navigate([
        BaseSettings.MAINVIEW_KEY + '/',
        { outlets: { mainbottom: [AppSettings.CHART_GENERAL_PURPOSE, id] } }
      ]);
    }
  }

  /** Answers a chart request of a dividend chart id, also one restored by the browser history. */
  private answerRequest(id: string): void {
    if (!id.startsWith(AppSettings.DIVIDEND_METRICS_CHART)) return;
    const ids = id
      .substring(AppSettings.DIVIDEND_METRICS_CHART.length)
      .split('-')
      .map(Number)
      .filter((n) => Number.isInteger(n) && n > 0);
    if (ids.length === 0) return;
    this.ids = ids;
    this.load(id, ids);
  }

  private load(id: string, ids: number[]): void {
    this.loadSubscription?.unsubscribe();
    this.loadSubscription = this.dividendService.getDividendChart(ids).subscribe((chart) => {
      if (this.chartDataService.isChartOfIdShown(id)) {
        this.chartDataService.sentToChart(
          chart.series.length > 1 ? this.createComparison(chart) : this.createSingle(chart.series[0])
        );
      }
    });
  }

  /**
   * One security: annual dividends as bars (cut years red, the current year light), the trailing yield on a second
   * axis and the splits as one trace of vertical lines labelled with their factor.
   */
  private createSingle(s: DividendChartSeries | undefined): Partial<ChartData> {
    if (!s) return { data: [], layout: {}, options: {} };
    const t = this.translateService.instant(['ANNUAL_DIVIDEND', 'YIELD_TTM', 'SPLIT', 'DIVIDEND_METRICS_CHART']);
    const bars = {
      ...PlotlyHelper.initializeChartTrace(`${t.ANNUAL_DIVIDEND} (${s.currency})`, 'bar'),
      x: s.years.map((y) => `${y.year}-07-01`),
      y: s.years.map((y) => y.amount),
      width: s.years.map(() => DividendChartService.BAR_WIDTH_MS),
      marker: { color: s.years.map((y) => (y.cut ? '#d62728' : y.partial ? '#aec7e8' : '#1f77b4')) },
      hovertemplate: '%{x|%Y}: %{y}<extra></extra>'
    };
    const yieldLine = {
      ...PlotlyHelper.initializeChartTrace(`${t.YIELD_TTM} %`, 'scatter', 'lines'),
      x: s.yieldHistory.map((p) => p.date),
      y: s.yieldHistory.map((p) => p.value),
      yaxis: 'y2',
      line: { color: '#ff7f0e' },
      hovertemplate: `%{x|%Y-%m-%d}: %{y:${DividendChartService.percentFormat()}} %<extra></extra>`
    };
    const data: object[] = [bars, yieldLine];
    const splitTrace = s.splits.length > 0 ? this.createSplitTrace(s, t.SPLIT) : null;
    if (splitTrace) {
      data.push(splitTrace);
    }
    return {
      data,
      // The split labels and lines use the muted text color of the active scheme.
      applyTheme: (_data, surface) => {
        if (splitTrace) {
          splitTrace.textfont.color = surface.mutedColor;
          splitTrace.line.color = surface.mutedColor;
        }
      },
      layout: {
        title: { text: `${s.name} – ${t.DIVIDEND_METRICS_CHART}` },
        xaxis: { type: 'date' },
        yaxis: { title: { text: s.currency }, rangemode: 'tozero' },
        yaxis2: { title: { text: '%' }, overlaying: 'y', side: 'right', rangemode: 'tozero', showgrid: false },
        // Invisible axis from 0 to 1 that holds the vertical split lines over the whole height of the plot
        yaxis3: { overlaying: 'y', range: [0, 1], visible: false, fixedrange: true },
        legend: PlotlyHelper.getLegendUnderChart(11)
      },
      options: {}
    };
  }

  /**
   * All splits as one trace, so that a click on its legend entry hides them. Every split is a vertical line labelled
   * with its factor only; the labels alternate between two heights, so neighbouring splits do not overlap. The hover
   * text names the date as well.
   */
  private createSplitTrace(
    s: DividendChartSeries,
    splitText: string
  ): {
    textfont: { size: number; color: string };
    line: { dash: string; width: number; color: string };
    [key: string]: unknown;
  } {
    // Muted text of the active scheme; the chart recolors it through ChartData.applyTheme when the scheme changes.
    const muted = PlotlyHelper.getThemeSurface().mutedColor;
    const x: (string | null)[] = [];
    const y: (number | null)[] = [];
    const text: string[] = [];
    const hovertext: string[] = [];
    s.splits.forEach((split, i) => {
      const factor = `${split.fromFactor}:${split.toFactor}`;
      const hover = `${splitText} ${factor} ${split.date}`;
      x.push(split.date, split.date, null);
      y.push(0, i % 2 === 0 ? 0.92 : 0.84, null);
      text.push('', factor, '');
      hovertext.push(hover, hover, '');
    });
    return {
      name: splitText,
      type: 'scatter',
      mode: 'lines+text',
      x,
      y,
      text,
      hovertext,
      hoverinfo: 'text',
      textposition: 'top center',
      textfont: { size: 10, color: muted },
      cliponaxis: false,
      yaxis: 'y3',
      line: { dash: 'dot', width: 1, color: muted }
    };
  }

  /**
   * Several securities: the trailing yield in the upper part and the annual dividend indexed to the common base year
   * in the lower part. Both traces of a security share colour and legend entry, so one click hides the security.
   */
  private createComparison(chart: SecurityDividendChart): Partial<ChartData> {
    const t = this.translateService.instant(['YIELD_TTM', 'DIVIDEND_INDEX', 'DIVIDEND_COMPARISON']);
    const percentFormat = DividendChartService.percentFormat();
    const withIndex = chart.baseYear != null;
    const data: object[] = [];
    chart.series.forEach((s, i) => {
      const color = DividendChartService.COLORS[i % DividendChartService.COLORS.length];
      const group = String(s.idSecuritycurrency);
      data.push({
        ...PlotlyHelper.initializeChartTrace(s.name, 'scatter', 'lines'),
        x: s.yieldHistory.map((p) => p.date),
        y: s.yieldHistory.map((p) => p.value),
        legendgroup: group,
        line: { color },
        hovertemplate: `${s.name}: %{y:${percentFormat}} %<extra></extra>`
      });
      if (withIndex) {
        data.push({
          ...PlotlyHelper.initializeChartTrace(s.name, 'scatter', 'lines+markers'),
          x: s.index.map((v) => `${v.year}-07-01`),
          y: s.index.map((v) => v.value),
          yaxis: 'y2',
          legendgroup: group,
          showlegend: false,
          line: { color },
          hovertemplate: `${s.name} %{x|%Y}: %{y:${percentFormat}}<extra></extra>`
        });
      }
    });
    // Each part gets its title above it instead of a long vertical axis title, which would not fit half the height
    const yieldDomain = withIndex ? [0.58, 0.93] : [0, 0.93];
    const annotations = [this.createPartTitle(`${t.YIELD_TTM} %`, yieldDomain[1])];
    if (withIndex) {
      annotations.push(this.createPartTitle(`${t.DIVIDEND_INDEX} (${chart.baseYear} = 100)`, 0.4));
    }
    return {
      data,
      layout: {
        title: { text: t.DIVIDEND_COMPARISON },
        xaxis: { type: 'date', anchor: withIndex ? 'y2' : 'y' },
        yaxis: { title: { text: '%' }, rangemode: 'tozero', domain: yieldDomain },
        ...(withIndex && { yaxis2: { title: { text: 'Index' }, domain: [0, 0.4] } }),
        annotations,
        legend: PlotlyHelper.getLegendUnderChart(11)
      },
      options: {}
    };
  }

  /**
   * Plotly number format of a percentage — trailing yield and dividend index — with the fraction digits GT prescribes
   * for percentages.
   */
  private static percentFormat(): string {
    return `.${AppSettings.FID_PERCENTAGE_FRACTION}f`;
  }

  /** Title of one part of the comparison, placed left-aligned just above the top of its vertical domain. */
  private createPartTitle(text: string, domainTop: number): object {
    return {
      text: `<b>${text}</b>`,
      xref: 'paper',
      yref: 'paper',
      x: 0,
      y: domainTop,
      xanchor: 'left',
      yanchor: 'bottom',
      showarrow: false,
      font: { size: 12 }
    };
  }
}
