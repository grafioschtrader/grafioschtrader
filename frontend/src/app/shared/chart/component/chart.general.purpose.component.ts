import type { PanelClickEvent } from '../../../lib/mainmenubar/component/iglobal.menu.attach';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  inject,
  OnDestroy,
  OnInit,
  viewChild,
  effect,
  input,
  untracked
} from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { ActivePanelService } from '../../../lib/mainmenubar/service/active.panel.service';
import { IGlobalMenuAttach } from '../../../lib/mainmenubar/component/iglobal.menu.attach';
import { ViewSizeChangedService } from '../../../lib/layout/service/view.size.changed.service';
import { Subscription } from 'rxjs';
import { ChartDataService } from '../service/chart.data.service';
import { ChartData, PlotlyHelper } from '../plotly.helper';
import { PlotlyLocales } from '../../plotlylocale/plotly.locales';
import { PlotlyInstance, PlotlyLoaderService } from '../plotly.loader';
import { GlobalparameterService } from '../../../lib/services/globalparameter.service';
import { ThemeService } from '../../../lib/theme/theme.service';

/**
 * This general purpose chart does not have a certain layout, it can be a pie or other type of chart. After the
 * creation it makes a call back to requested the data for the registered id. Advantage: once open it can
 * receive its data over subscription.
 */
@Component({
  template: `
    <div
      class="fullChart"
      [class]="{ 'active-border': isActivated(), 'passiv-border': !isActivated() }"
      (click)="onComponentClick($event)">
      <div #chart class="plot-container"></div>
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ChartGeneralPurposeComponent implements OnInit, OnDestroy, IGlobalMenuAttach {
  readonly id = input.required<string>();

  private gps = inject(GlobalparameterService);
  private translateService = inject(TranslateService);
  private chartDataService = inject(ChartDataService);
  private viewSizeChangedService = inject(ViewSizeChangedService);
  private activePanelService = inject(ActivePanelService);

  readonly chartElement = viewChild.required<ElementRef>('chart');

  private subscriptionViewSizeChanged?: Subscription;
  private subscriptionChartDataChanged?: Subscription;
  private chartData?: Partial<ChartData>;
  private readonly plotlyLoader = inject(PlotlyLoaderService);
  private readonly themeService = inject(ThemeService);
  private plotly?: PlotlyInstance;
  private destroyed = false;
  constructor() {
    effect(() => {
      const id = this.id();
      untracked(() => this.chartDataService.requestDataForChart(id));
    });
    PlotlyHelper.effectOnThemeChange(this.themeService.isDark, () => this.restyleForTheme());
  }

  ngOnInit(): void {
    this.activePanelService.registerPanel(this);
    const config = PlotlyLocales.setPlotyLocales(this.gps);
    config.displaylogo = false;

    this.subscriptionChartDataChanged = this.chartDataService.chartDataChanged$.subscribe((chartData) => {
      chartData.options = Object.assign({}, chartData.options, config);
      this.chartData = chartData;
      this.plotOrRePlot();

      if (!this.subscriptionViewSizeChanged) {
        this.subscriptionViewSizeChanged = this.viewSizeChangedService.viewSizeChanged$.subscribe(
          (changedViewSizeType) => this.plotOrRePlot()
        );
      }
    });
  }

  isActivated(): boolean {
    return this.activePanelService.isActivated(this);
  }

  hideContextMenu(): void {}

  callMeDeactivate(): void {}

  onComponentClick(event: PanelClickEvent): void {
    this.activePanelService.activatePanel(this);
  }

  public getHelpContextId(): string | null {
    // TODO return ID depending on the chart
    return null;
  }

  ngOnDestroy(): void {
    this.destroyed = true;
    this.subscriptionChartDataChanged?.unsubscribe();
    this.subscriptionViewSizeChanged?.unsubscribe();
    this.chartDataService.clearShownChart();
    this.activePanelService.destroyPanel(this);
  }

  /**
   * Draws the current chart data once Plotly is loaded. Data that was replaced while the library loaded is not drawn,
   * and nothing is drawn into a destroyed component.
   */
  private plotOrRePlot(): void {
    const chartData = this.chartData;
    if (!chartData) return;
    this.plotlyLoader.load().then(
      (plotly) => {
        if (!this.destroyed && chartData === this.chartData) {
          this.plot(plotly);
        }
      },
      () => {}
    );
  }

  private plot(plotly: PlotlyInstance): void {
    const chartData = this.chartData;
    if (!chartData) return;
    this.plotly = plotly;
    const chartElement = this.chartElement();
    plotly.purge(chartElement.nativeElement);

    this.applyTheme(chartData);
    if (chartData.legendTooltipMap) {
      plotly
        .newPlot(chartElement.nativeElement, chartData.data, chartData.layout, chartData.options)
        .then(this.attachTooltip.bind(this));
      chartElement.nativeElement.on('plotly_afterplot', this.attachTooltip.bind(this));
    } else {
      plotly.newPlot(chartElement.nativeElement, chartData.data, chartData.layout, chartData.options);
    }
    if (chartData.callBackFN) {
      PlotlyHelper.registerPlotlyClick(chartElement.nativeElement, chartData.callBackFN);
    }
  }

  /**
   * Applies the active scheme to the producer's accents and the layout chrome. The layout keeps a stable `uirevision`
   * (the chart id), so a later `Plotly.react` with the same data set retains zoom and legend state.
   */
  private applyTheme(chartData: Partial<ChartData>): void {
    const surface = PlotlyHelper.getThemeSurface(this.chartElement().nativeElement);
    chartData.applyTheme?.(chartData.data, surface);
    chartData.layout ??= {};
    chartData.layout.uirevision ??= this.id();
    PlotlyHelper.applyThemeToLayout(chartData.layout, surface);
  }

  /** Redraws the mounted chart in the colors of the new scheme, keeping zoom and hidden legend entries. */
  private restyleForTheme(): void {
    const chartData = this.chartData;
    const plotly = this.plotly;
    if (!chartData || !plotly || this.destroyed) return;
    this.applyTheme(chartData);
    plotly.react(this.chartElement().nativeElement, chartData.data, chartData.layout, chartData.options);
  }

  private attachTooltip(): void {
    if (this.chartData) PlotlyHelper.attachTooltip(this.chartData.legendTooltipMap, this.chartElement());
  }
}
