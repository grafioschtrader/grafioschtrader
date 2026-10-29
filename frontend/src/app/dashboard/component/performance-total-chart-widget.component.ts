import { requireFormControl } from '../../lib/dynamic-form/models/form-control-access';
import {
  AfterViewInit,
  Component,
  ElementRef,
  inject,
  NgZone,
  OnDestroy,
  OnInit,
  viewChild,
  input,
  linkedSignal,
  effect,
  untracked
} from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';
import { Subscription, merge } from 'rxjs';
import { DashboardConfig, DashboardResult } from '../../lib/dashboard/dashboard.types';
import { DashboardService } from '../../lib/dashboard/dashboard.service';
import { DYNAMIC_FORM_IMPORTS } from '../../lib/dynamic-form/dynamic-form.imports';
import { DynamicFieldHelper } from '../../lib/helper/dynamic.field.helper';
import { Helper } from '../../lib/helper/helper';
import { SelectOptionsHelper } from '../../lib/helper/select.options.helper';
import { TranslateHelper } from '../../lib/helper/translate.helper';
import { FieldConfig } from '../../lib/dynamic-form/models/field.config';
import { FormConfig } from '../../lib/dynamic-form/models/form.config';
import { ValueKeyHtmlSelectOptions } from '../../lib/dynamic-form/models/value.key.html.select.options';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { ShowRecordConfigBase } from '../../lib/datashowbase/show.record.config.base';
import { PortfolioService } from '../../portfolio/service/portfolio.service';
import { PlotlyLocales } from '../../shared/plotlylocale/plotly.locales';
import { PlotlyInstance, PlotlyLoaderService } from '../../shared/chart/plotly.loader';
import { PerformanceTotalChart, PerformanceTotalChartPoint } from '../model/performance.total.chart';

/** Line colors in a fixed order, the same first two slots the other charts of GT use. */
const TOTAL_COLOR = '#2a78d6';
const INVESTED_COLOR = '#eb6834';

/**
 * Dashboard card drawing the total value of the client, or of one of its portfolios, next to the invested capital.
 *
 * <p>
 * The period the card opens with is a layout decision and lives in the settings dialog. Which portfolio and which
 * period it shows can also be changed on the card: a reader comparing them does so repeatedly and none of those changes
 * is worth saving, so they travel as settings of one read and an ordinary Refresh returns the card to its saved state.
 * </p>
 *
 * <p>
 * The card can be maximized by the dashboard. It then receives {@link maximized} and the chart takes the height of the
 * card; a resize observer on the chart element re-lays out the plot for that as well as for the splitter and the
 * window.
 * </p>
 */
@Component({
  selector: 'performance-total-chart-widget',
  imports: [TranslateModule, ...DYNAMIC_FORM_IMPORTS],
  template: `
    @let shown = chart();
    <div class="total-chart" [class.total-chart-maximized]="maximized()">
      <dynamic-form
        [config]="config"
        [formConfig]="formConfig"
        [translateService]="translateService"
        #form="dynamicForm" />
      @if (shown?.reasonKey) {
        <small class="d-block">{{ shown.reasonKey | translate }}</small>
      }
      <div #plot class="total-chart-plot" [hidden]="!shown || !!shown.reasonKey"></div>
      @if (shown && !shown.reasonKey) {
        <small class="d-block">
          {{ 'DASHBOARD_TOTAL_CHART_NEWEST' | translate }}: {{ getValueByPath(shown, newestDateField) }}
          @if (shown.recalcPending) {
            · {{ 'DASHBOARD_TOTAL_CHART_RECALC' | translate }}
          }
          @if (shown.sampling !== 'DAY') {
            · {{ 'DASHBOARD_TOTAL_CHART_' + shown.sampling | translate }}
          }
        </small>
      }
    </div>
  `,
  styles: [
    `
      :host,
      .total-chart {
        display: flex;
        flex-direction: column;
        min-height: 0;
      }
      .total-chart-maximized {
        height: 100%;
      }
      .total-chart-plot {
        height: 320px;
        min-height: 240px;
      }
      .total-chart-maximized .total-chart-plot {
        flex: 1 1 auto;
        height: auto;
      }
    `
  ]
})
export class PerformanceTotalChartWidgetComponent
  extends ShowRecordConfigBase
  implements OnInit, AfterViewInit, OnDestroy
{
  private dashboardService: DashboardService = inject(DashboardService);
  private portfolioService: PortfolioService = inject(PortfolioService);
  private elementRef: ElementRef<HTMLElement> = inject(ElementRef);
  private zone: NgZone = inject(NgZone);

  readonly result = input<DashboardResult>(undefined);
  /** Set by the dashboard while this card takes over the whole dashboard area. */
  readonly maximized = input(false);

  readonly plotElement = viewChild<ElementRef<HTMLDivElement>>('plot');

  /** Server results replace the local chart; maximizing alone keeps its chosen portfolio and period. */
  readonly chart = linkedSignal(() => this.result()?.payload?.custom as PerformanceTotalChart);
  config: FieldConfig[];
  configObject: { [name: string]: FieldConfig };
  formConfig: FormConfig = { labelColumns: 3, nonModal: true };
  readonly newestDateField = ShowRecordConfigBase.createColumnConfig(DataType.DateString, 'newestDate', '');

  private readonly dateField = ShowRecordConfigBase.createColumnConfig(DataType.DateString, 'date', '');
  private readonly amountField = ShowRecordConfigBase.createColumnConfig(DataType.Numeric, 'value', '');
  private readonly plotlyConfig: any;
  private texts: { [key: string]: string } = {};
  private settingsSubscription: Subscription;
  private resizeObserver: ResizeObserver;
  private viewReady = false;
  private destroyed = false;
  private readonly plotlyLoader = inject(PlotlyLoaderService);
  /** Set once the chart has loaded the library; cleanup never triggers the import. */
  private plotly: PlotlyInstance;

  constructor() {
    super();
    this.config = [
      DynamicFieldHelper.createFieldSelectNumber('idPortfolio', 'DASHBOARD_TOTAL_CHART_PORTFOLIO', false, {
        usedLayoutColumns: 6
      }),
      DynamicFieldHelper.createFieldSelectString('range', 'DASHBOARD_TOTAL_CHART_RANGE', true, {
        usedLayoutColumns: 6
      })
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
    this.plotlyConfig = { ...PlotlyLocales.setPlotyLocales(this.gps), responsive: false, displaylogo: false };
    this.translateService
      .get(['TOTAL_BALANCE', 'INVESTED_CAPITAL', 'DIFFERENCE'])
      .subscribe((texts) => (this.texts = texts));

    effect(() => {
      this.chart();
      untracked(() => {
        this.applyChart();
      });
    });
  }

  /** The portfolios are data of the card rather than of its figures, so they are read once, whatever those show. */
  ngOnInit(): void {
    // The whole client is the default, so the empty entry is a real choice rather than a missing value.
    this.portfolioService.getPortfoliosForTenantOrderByName().subscribe((portfolios) => {
      this.configObject.idPortfolio.valueKeyHtmlOptions = SelectOptionsHelper.createValueKeyHtmlSelectOptionsFromArray(
        'idPortfolio',
        'name',
        portfolios,
        true
      );
      this.showChosenSettings();
    });
  }

  ngAfterViewInit(): void {
    this.viewReady = true;
    this.settingsSubscription = merge(
      requireFormControl(this.configObject.idPortfolio).valueChanges,
      requireFormControl(this.configObject.range).valueChanges
    ).subscribe(() => this.reload());
    // Outside Angular, because a re-layout of the plot changes nothing a template binds to.
    this.zone.runOutsideAngular(() => {
      this.resizeObserver = new ResizeObserver(() => this.resizePlot());
      this.resizeObserver.observe(this.plotElement().nativeElement);
    });
    this.drawPlot();
  }

  ngOnDestroy(): void {
    this.destroyed = true;
    this.settingsSubscription?.unsubscribe();
    this.resizeObserver?.disconnect();
    const plotElement = this.plotElement();
    if (plotElement) {
      this.plotly?.purge(plotElement.nativeElement);
    }
  }

  /**
   * Reloads this card alone for the chosen portfolio and period. A failure leaves the chart on screen untouched rather
   * than blanking it; the dashboard reports failures through its own refresh path.
   */
  private reload(): void {
    const resultValue = this.result();
    if (!resultValue?.instanceId) {
      return;
    }
    // A select of the browser answers with text; turning it into a portfolio id or null belongs to the field.
    const configOverride: DashboardConfig = {};
    Helper.copyFormSingleFormConfigToBusinessObject(
      this.formConfig,
      this.configObject.idPortfolio,
      configOverride,
      true
    );
    configOverride['range'] = requireFormControl(this.configObject.range).value;
    this.dashboardService.refresh(resultValue.instanceId, configOverride).subscribe((result) => {
      const chart = result?.payload?.custom as PerformanceTotalChart;
      if (chart) {
        this.show(chart);
      }
    });
  }

  private applyChart(): void {
    const chart = this.chart();
    if (chart) {
      this.amountField.fixedCurrency = chart.currency;
      this.configObject.range.valueKeyHtmlOptions = SelectOptionsHelper.translateExistingValueKeyHtmlSelectOptions(
        this.translateService,
        chart.ranges.map((range) => new ValueKeyHtmlSelectOptions(range, range)),
        false
      );
    }

    this.showChosenSettings();
    this.drawPlot();
  }
  private show(chart: PerformanceTotalChart): void {
    this.chart.set(chart);
  }

  /** Lets the selects agree with the chart on screen without asking for it again, also after a dashboard refresh. */
  private showChosenSettings(): void {
    this.configObject.idPortfolio.formControl?.setValue(this.chart()?.idPortfolio ?? '', { emitEvent: false });
    this.configObject.range.formControl?.setValue(this.chart()?.range ?? null, { emitEvent: false });
  }

  private drawPlot(): void {
    if (!this.viewReady) {
      return;
    }
    const element = this.plotElement().nativeElement;
    const chart = this.chart();
    if (!chart || chart.reasonKey || !chart.points.length) {
      this.plotly?.purge(element);
      return;
    }
    // Wait until the plot element is no longer hidden, otherwise Plotly measures a zero size.
    setTimeout(() =>
      this.plotlyLoader.load().then(
        (plotly) => {
          // A newer result replaces the pending one, so only the latest chart is drawn
          if (!this.destroyed && chart === this.chart()) {
            this.plotly = plotly;
            plotly.react(element, this.traces(chart.points), this.layout(), this.plotlyConfig);
          }
        },
        () => {}
      )
    );
  }

  private resizePlot(): void {
    const element = this.plotElement()?.nativeElement as any;
    if (element?.data && element.offsetParent) {
      this.plotly?.Plots.resize(element);
    }
  }

  /**
   * The total value carries the tooltip for both lines, so a reader sees the day, both values and their difference in
   * one place. Every figure of it is formatted by GT's data types rather than by Plotly, so it reads like the tables.
   */
  private traces(points: PerformanceTotalChartPoint[]): any[] {
    const x = points.map((p) => p.date);
    const customdata = points.map((p) => [
      this.getValueByPath(p, this.dateField),
      this.formatAmount(p.totalBalanceMC),
      this.formatAmount(p.investedCapitalMC),
      this.formatAmount(p.totalBalanceMC - p.investedCapitalMC)
    ]);
    return [
      {
        type: 'scatter',
        mode: 'lines',
        name: this.texts['TOTAL_BALANCE'],
        x,
        y: points.map((p) => p.totalBalanceMC),
        line: { color: TOTAL_COLOR, width: 2 },
        customdata,
        hovertemplate:
          '%{customdata[0]}<br>' +
          `${this.texts['TOTAL_BALANCE']}: %{customdata[1]}<br>` +
          `${this.texts['INVESTED_CAPITAL']}: %{customdata[2]}<br>` +
          `${this.texts['DIFFERENCE']}: %{customdata[3]}<extra></extra>`
      },
      {
        type: 'scatter',
        mode: 'lines',
        name: this.texts['INVESTED_CAPITAL'],
        x,
        y: points.map((p) => p.investedCapitalMC),
        line: { color: INVESTED_COLOR, width: 2, shape: 'hv' },
        hoverinfo: 'skip'
      }
    ];
  }

  private formatAmount(value: number): string {
    return this.getValueByPath({ value }, this.amountField) + ' ' + this.chart().currency;
  }

  /**
   * Transparent surfaces and the text and border colors of the active theme, read from its CSS variables, so the chart
   * follows the light as well as the dark theme.
   */
  private layout(): any {
    const style = getComputedStyle(this.elementRef.nativeElement);
    const text = style.getPropertyValue('--p-text-color').trim() || '#52514e';
    const grid = style.getPropertyValue('--p-content-border-color').trim() || '#e1e0d9';
    return {
      autosize: true,
      margin: { l: 8, r: 16, t: 30, b: 8 },
      paper_bgcolor: 'rgba(0,0,0,0)',
      plot_bgcolor: 'rgba(0,0,0,0)',
      font: { color: text },
      separators: this.gps.getDecimalSymbol() + this.gps.getThousandsSeparatorSymbol(),
      hovermode: 'x',
      showlegend: true,
      legend: { orientation: 'h', x: 0, y: 1.02, yanchor: 'bottom' },
      xaxis: { type: 'date', gridcolor: grid, linecolor: grid, automargin: true },
      // Plotly's own tick format, as in the period performance chart: large values are shortened, 200'000 to 200k.
      // The currency is the axis title, so the ticks stay short.
      yaxis: {
        title: { text: this.chart().currency, standoff: 8 },
        gridcolor: grid,
        linecolor: grid,
        automargin: true
      }
    };
  }
}
