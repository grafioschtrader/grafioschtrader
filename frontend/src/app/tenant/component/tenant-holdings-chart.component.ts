import {
  afterNextRender,
  DestroyRef,
  Injector,
  signal,
  AfterViewInit,
  Component,
  ElementRef,
  inject,
  OnDestroy,
  OnInit,
  viewChild,
  effect,
  untracked
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Subscription } from 'rxjs';
import { FormBase } from '../../lib/edit/form.base';
import { DynamicFormComponent } from '../../lib/dynamic-form/containers/dynamic-form/dynamic-form.component';
import { DYNAMIC_FORM_IMPORTS } from '../../lib/dynamic-form/dynamic-form.imports';
import { DynamicFieldHelper } from '../../lib/helper/dynamic.field.helper';
import { TranslateHelper } from '../../lib/helper/translate.helper';
import { AppHelper } from '../../lib/helper/app.helper';
import { ValueKeyHtmlSelectOptions } from '../../lib/dynamic-form/models/value.key.html.select.options';
import { GlobalparameterService } from '../../lib/services/globalparameter.service';
import { UserSettingsService } from '../../lib/services/user.settings.service';
import { IGlobalMenuAttach } from '../../lib/mainmenubar/component/iglobal.menu.attach';
import { ActivePanelService } from '../../lib/mainmenubar/service/active.panel.service';
import { ViewSizeChangedService } from '../../lib/layout/service/view.size.changed.service';
import { HelpIds } from '../../lib/help/help.ids';
import { AppSettings } from '../../shared/app.settings';
import { PlotlyLocales } from '../../shared/plotlylocale/plotly.locales';
import { PlotlyInstance, PlotlyLoaderService } from '../../shared/chart/plotly.loader';
import { TenantHoldingsChartService } from '../service/tenant.holdings.chart.service';
import { HoldingsTreemapChart } from './holdings.treemap.chart';

/**
 * The charts the user can choose from. The keys are also their translation keys.
 */
enum HoldingsChartType {
  HOLDINGS_CHART_ASSETCLASS = 'HOLDINGS_CHART_ASSETCLASS',
  HOLDINGS_CHART_TREEMAP = 'HOLDINGS_CHART_TREEMAP'
}

/**
 * Charts of the report "security asset classes with cash" in the lower display area. The user chooses between the net
 * risk and share per asset class and the holdings treemap, which shows every holding valued on a hypothetical sale at
 * the report date. The report table publishes every load through {@link TenantHoldingsChartService}, so the chart
 * follows the report date, the closed positions and the strategy selection of the table.
 */
@Component({
  template: `
    <div
      class="data-container holdings-chart"
      (click)="onComponentClick($event)"
      [class]="{ 'active-border': isActivated(), 'passiv-border': !isActivated() }">
      <dynamic-form
        [config]="config"
        [formConfig]="formConfig"
        [translateService]="translateService"
        #form="dynamicForm" />
      @if (noDataTextKey()) {
        <h4>{{ noDataTextKey() | translate }}</h4>
      }
      <div #chart class="holdings-chart-plot" [hidden]="!!noDataTextKey()"></div>
    </div>
  `,
  styles: [
    `
      .holdings-chart {
        display: flex;
        flex-direction: column;
        height: calc(100% - 8px);
      }
      .holdings-chart-plot {
        flex: 1 1 auto;
        min-height: 320px;
        width: 100%;
      }
    `
  ],
  imports: [TranslateModule, ...DYNAMIC_FORM_IMPORTS]
})
export class TenantHoldingsChartComponent
  extends FormBase
  implements OnInit, AfterViewInit, OnDestroy, IGlobalMenuAttach
{
  private tenantHoldingsChartService: TenantHoldingsChartService = inject(TenantHoldingsChartService);
  private activePanelService: ActivePanelService = inject(ActivePanelService);
  private viewSizeChangedService: ViewSizeChangedService = inject(ViewSizeChangedService);
  private usersettingsService: UserSettingsService = inject(UserSettingsService);
  private gps: GlobalparameterService = inject(GlobalparameterService);
  public translateService: TranslateService = inject(TranslateService);

  readonly form = viewChild(DynamicFormComponent);
  readonly chartElement = viewChild<ElementRef>('chart');

  /** Translation key of the hint shown instead of a chart, null when a chart is shown. */
  readonly noDataTextKey = signal<string>(null);
  private readonly destroyRef = inject(DestroyRef);
  private readonly renderInjector = inject(Injector);

  private readonly chartInput = this.tenantHoldingsChartService.chartInput;
  private formReady = false;
  private plotlyConfig: any;
  private readonly plotlyLoader = inject(PlotlyLoaderService);
  /** Set once the first chart has loaded the library; cleanup never triggers the import. */
  private plotly: PlotlyInstance;
  private destroyed = false;
  /** Raised by every draw, so a draw still waiting for the library is dropped when a newer one follows. */
  private drawVersion = 0;
  private subscriptions: Subscription[] = [];

  constructor() {
    super();
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps, 2, null, true);

    effect(() => {
      const input = this.chartInput();
      if (input) untracked(() => this.drawChart());
    });
  }

  ngOnInit(): void {
    this.plotlyConfig = PlotlyLocales.setPlotyLocales(this.gps);
    this.plotlyConfig.displaylogo = false;
    this.plotlyConfig.responsive = true;
    this.createInputFormDefinition();
    this.subscriptions.push(
      this.viewSizeChangedService.viewSizeChanged$
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe(() => this.resizeChart())
    );
  }

  ngAfterViewInit(): void {
    // The dynamic-form builds its FormGroup in its own ngOnInit, so the controls only exist now.
    afterNextRender(
      () => {
        const storedType = this.usersettingsService.readSingleValue(this.getStoreKey());
        this.configObject.chartType.formControl.setValue(
          Object.values(HoldingsChartType).includes(storedType)
            ? storedType
            : HoldingsChartType.HOLDINGS_CHART_ASSETCLASS
        );
        this.subscriptions.push(
          this.configObject.chartType.formControl.valueChanges
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe((chartType) => {
              this.usersettingsService.saveSingleValue(this.getStoreKey(), chartType);
              this.drawChart();
            })
        );
        this.formReady = true;
        this.drawChart();
      },
      { injector: this.renderInjector }
    );
  }

  isActivated(): boolean {
    return this.activePanelService.isActivated(this);
  }

  onComponentClick(event): void {
    this.activePanelService.activatePanel(this);
  }

  hideContextMenu(): void {}

  callMeDeactivate(): void {}

  getHelpContextId(): string {
    return HelpIds.HELP_PORTFOLIOS_SECURITY_CASH_ACCOUNT_REPORT;
  }

  ngOnDestroy(): void {
    this.destroyed = true;
    this.subscriptions.forEach((s) => s.unsubscribe());
    this.plotly?.purge(this.chartElement().nativeElement);
    this.activePanelService.destroyPanel(this);
  }

  private createInputFormDefinition(): void {
    this.config = [DynamicFieldHelper.createFieldSelectStringHeqF('chartType', true, { usedLayoutColumns: 6 })];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
    const chartTypes = Object.values(HoldingsChartType);
    this.translateService
      .get(chartTypes)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((t) => {
        this.configObject.chartType.valueKeyHtmlOptions = chartTypes.map(
          (ct) => new ValueKeyHtmlSelectOptions(ct, t[ct])
        );
      });
  }

  private getStoreKey(): string {
    return this.gps.getIdTenant() + '_' + AppSettings.DEPOT_CASH_CHART_TYPE;
  }

  private drawChart(): void {
    if (!this.formReady || !this.chartInput()) {
      return;
    }
    const figure = this.buildFigure(this.configObject.chartType.formControl.value);
    const version = ++this.drawVersion;
    this.plotly?.purge(this.chartElement().nativeElement);
    if (figure) {
      // Wait until the chart element is no longer hidden, otherwise Plotly measures a zero size.
      afterNextRender(
        () =>
          this.plotlyLoader.load().then(
            (plotly) => {
              if (!this.destroyed && version === this.drawVersion) {
                this.plotly = plotly;
                plotly.newPlot(this.chartElement().nativeElement, figure.data, figure.layout, this.plotlyConfig);
              }
            },
            () => {}
          ),
        { injector: this.renderInjector }
      );
    }
  }

  /**
   * Returns the figure of the selected chart, or null with the hint to show instead. The treemap is only built by the
   * plain asset class report; with a rebalancing strategy selected the table loads a different report.
   */
  private buildFigure(chartType: HoldingsChartType): { data: any[]; layout: any } {
    this.noDataTextKey.set(null);
    if (chartType === HoldingsChartType.HOLDINGS_CHART_TREEMAP) {
      const treemap = this.chartInput().summary?.holdingsTreemap;
      if (this.chartInput().rebalancing || !treemap) {
        this.noDataTextKey.set('HOLDINGS_TREEMAP_NO_STRATEGY');
        return null;
      }
      if (treemap.nodes.length <= 1) {
        this.noDataTextKey.set('NO_DATA_AVAILABLE');
        return null;
      }
      return new HoldingsTreemapChart(
        this.translateService,
        this.gps,
        this.chartInput().summary.currency
      ).getChartDefinition(this.chartInput().title, treemap);
    }
    return this.chartInput().assetclassChart;
  }

  private resizeChart(): void {
    const chartElement = this.chartElement();
    if (!this.noDataTextKey() && chartElement.nativeElement.data) {
      this.plotly?.Plots.resize(chartElement.nativeElement);
    }
  }
}
