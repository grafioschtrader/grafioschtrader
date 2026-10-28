import { ChangeDetectionStrategy, Component, ElementRef, inject, OnDestroy, OnInit, viewChild } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { ActivePanelService } from '../../../lib/mainmenubar/service/active.panel.service';
import { ActivatedRoute } from '@angular/router';
import { IGlobalMenuAttach } from '../../../lib/mainmenubar/component/iglobal.menu.attach';
import { ViewSizeChangedService } from '../../../lib/layout/service/view.size.changed.service';
import { Subscription } from 'rxjs';
import { ChartDataService } from '../service/chart.data.service';
import { ChartData, PlotlyHelper } from '../plotly.helper';
import { PlotlyLocales } from '../../plotlylocale/plotly.locales';
import { PlotlyInstance, PlotlyLoaderService } from '../plotly.loader';
import { GlobalparameterService } from '../../../lib/services/globalparameter.service';

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
  readonly chartElement = viewChild<ElementRef>('chart');

  private subscriptionViewSizeChanged: Subscription;
  private subscriptionChartDataChanged: Subscription;
  private routeSubscribe: Subscription;
  private chartData: ChartData;
  private readonly plotlyLoader = inject(PlotlyLoaderService);
  private destroyed = false;

  constructor(
    private gps: GlobalparameterService,
    private translateService: TranslateService,
    private chartDataService: ChartDataService,
    private viewSizeChangedService: ViewSizeChangedService,
    private activePanelService: ActivePanelService,
    private activatedRoute: ActivatedRoute
  ) {}

  ngOnInit(): void {
    this.activePanelService.registerPanel(this);
    const config = PlotlyLocales.setPlotyLocales(this.gps);
    config.displaylogo = false;

    this.subscriptionChartDataChanged = this.chartDataService.chartDataChanged$.subscribe((chartData: ChartData) => {
      chartData.options = Object.assign({}, chartData.options, config);
      this.chartData = chartData;
      this.plotOrRePlot();

      if (!this.subscriptionViewSizeChanged) {
        this.subscriptionViewSizeChanged = this.viewSizeChangedService.viewSizeChanged$.subscribe(
          (changedViewSizeType) => this.plotOrRePlot()
        );
      }
    });

    this.routeSubscribe = this.activatedRoute.params.subscribe((params) => {
      this.chartDataService.requestDataForChart(params['id']);
    });
  }

  isActivated(): boolean {
    return this.activePanelService.isActivated(this);
  }

  hideContextMenu(): void {}

  callMeDeactivate(): void {}

  onComponentClick(event): void {
    this.activePanelService.activatePanel(this);
  }

  public getHelpContextId(): string {
    // TODO return ID depending on the chart
    return null;
  }

  ngOnDestroy(): void {
    this.destroyed = true;
    this.routeSubscribe && this.routeSubscribe.unsubscribe();
    this.subscriptionChartDataChanged && this.subscriptionChartDataChanged.unsubscribe();
    this.subscriptionViewSizeChanged && this.subscriptionViewSizeChanged.unsubscribe();
    this.chartDataService.clearShownChart();
    this.activePanelService.destroyPanel(this);
  }

  /**
   * Draws the current chart data once Plotly is loaded. Data that was replaced while the library loaded is not drawn,
   * and nothing is drawn into a destroyed component.
   */
  private plotOrRePlot(): void {
    const chartData = this.chartData;
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
    const chartElement = this.chartElement();
    plotly.purge(chartElement.nativeElement);

    if (this.chartData.legendTooltipMap) {
      plotly
        .newPlot(chartElement.nativeElement, this.chartData.data, this.chartData.layout, this.chartData.options)
        .then(this.attachTooltip.bind(this));
      chartElement.nativeElement.on('plotly_afterplot', this.attachTooltip.bind(this));
    } else {
      plotly.newPlot(chartElement.nativeElement, this.chartData.data, this.chartData.layout, this.chartData.options);
    }
    if (this.chartData.callBackFN) {
      PlotlyHelper.registerPlotlyClick(chartElement.nativeElement, this.chartData.callBackFN);
    }
  }

  private attachTooltip(): void {
    PlotlyHelper.attachTooltip(this.chartData.legendTooltipMap, this.chartElement());
  }
}
