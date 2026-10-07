import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  OnDestroy,
  OnInit,
  ViewChild
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Subscription } from 'rxjs';
import { filter } from 'rxjs/operators';
import { FormBase } from '../../lib/edit/form.base';
import { DynamicFormComponent } from '../../lib/dynamic-form/containers/dynamic-form/dynamic-form.component';
import { DynamicFormModule } from '../../lib/dynamic-form/dynamic-form.module';
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
import { HoldingsChartInput, TenantHoldingsChartService } from '../service/tenant.holdings.chart.service';
import { HoldingsTreemapChart } from './holdings.treemap.chart';

declare let Plotly: any;

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
      [ngClass]="{ 'active-border': isActivated(), 'passiv-border': !isActivated() }">
      <dynamic-form
        [config]="config"
        [formConfig]="formConfig"
        [translateService]="translateService"
        #form="dynamicForm">
      </dynamic-form>
      @if (noDataTextKey) {
        <h4>{{ noDataTextKey | translate }}</h4>
      }
      <div #chart class="holdings-chart-plot" [hidden]="!!noDataTextKey"></div>
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
  standalone: true,
  changeDetection: ChangeDetectionStrategy.Eager,
  imports: [CommonModule, TranslateModule, DynamicFormModule]
})
export class TenantHoldingsChartComponent
  extends FormBase
  implements OnInit, AfterViewInit, OnDestroy, IGlobalMenuAttach
{
  @ViewChild(DynamicFormComponent, { static: true }) form: DynamicFormComponent;
  @ViewChild('chart', { static: true }) chartElement: ElementRef;

  /** Translation key of the hint shown instead of a chart, null when a chart is shown. */
  noDataTextKey: string = null;

  private chartInput: HoldingsChartInput;
  private formReady = false;
  private plotlyConfig: any;
  private subscriptions: Subscription[] = [];

  constructor(
    private tenantHoldingsChartService: TenantHoldingsChartService,
    private activePanelService: ActivePanelService,
    private viewSizeChangedService: ViewSizeChangedService,
    private usersettingsService: UserSettingsService,
    private gps: GlobalparameterService,
    public translateService: TranslateService
  ) {
    super();
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps, 2, null, true);
  }

  ngOnInit(): void {
    this.plotlyConfig = PlotlyLocales.setPlotyLocales(Plotly, this.gps);
    this.plotlyConfig.displaylogo = false;
    this.plotlyConfig.responsive = true;
    this.createInputFormDefinition();
    this.subscriptions.push(
      this.tenantHoldingsChartService.chartInput$.pipe(filter((ci) => ci != null)).subscribe((ci) => {
        this.chartInput = ci;
        this.drawChart();
      }),
      this.viewSizeChangedService.viewSizeChanged$.subscribe(() => this.resizeChart())
    );
  }

  ngAfterViewInit(): void {
    // The dynamic-form builds its FormGroup in its own ngOnInit, so the controls only exist now.
    Promise.resolve().then(() => {
      const storedType = this.usersettingsService.readSingleValue(this.getStoreKey());
      this.configObject.chartType.formControl.setValue(
        Object.values(HoldingsChartType).includes(storedType) ? storedType : HoldingsChartType.HOLDINGS_CHART_ASSETCLASS
      );
      this.subscriptions.push(
        this.configObject.chartType.formControl.valueChanges.subscribe((chartType) => {
          this.usersettingsService.saveSingleValue(this.getStoreKey(), chartType);
          this.drawChart();
        })
      );
      this.formReady = true;
      this.drawChart();
    });
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
    this.subscriptions.forEach((s) => s.unsubscribe());
    Plotly.purge(this.chartElement.nativeElement);
    this.activePanelService.destroyPanel(this);
  }

  private createInputFormDefinition(): void {
    this.config = [DynamicFieldHelper.createFieldSelectStringHeqF('chartType', true, { usedLayoutColumns: 6 })];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
    const chartTypes = Object.values(HoldingsChartType);
    this.translateService.get(chartTypes).subscribe((t) => {
      this.configObject.chartType.valueKeyHtmlOptions = chartTypes.map(
        (ct) => new ValueKeyHtmlSelectOptions(ct, t[ct])
      );
    });
  }

  private getStoreKey(): string {
    return this.gps.getIdTenant() + '_' + AppSettings.DEPOT_CASH_CHART_TYPE;
  }

  private drawChart(): void {
    if (!this.formReady || !this.chartInput) {
      return;
    }
    const figure = this.buildFigure(this.configObject.chartType.formControl.value);
    Plotly.purge(this.chartElement.nativeElement);
    if (figure) {
      // Wait until the chart element is no longer hidden, otherwise Plotly measures a zero size.
      setTimeout(() => Plotly.newPlot(this.chartElement.nativeElement, figure.data, figure.layout, this.plotlyConfig));
    }
  }

  /**
   * Returns the figure of the selected chart, or null with the hint to show instead. The treemap is only built by the
   * plain asset class report; with a rebalancing strategy selected the table loads a different report.
   */
  private buildFigure(chartType: HoldingsChartType): { data: any[]; layout: any } {
    this.noDataTextKey = null;
    if (chartType === HoldingsChartType.HOLDINGS_CHART_TREEMAP) {
      const treemap = this.chartInput.summary?.holdingsTreemap;
      if (this.chartInput.rebalancing || !treemap) {
        this.noDataTextKey = 'HOLDINGS_TREEMAP_NO_STRATEGY';
        return null;
      }
      if (treemap.nodes.length <= 1) {
        this.noDataTextKey = 'NO_DATA_AVAILABLE';
        return null;
      }
      return new HoldingsTreemapChart(
        this.translateService,
        this.gps,
        this.chartInput.summary.currency
      ).getChartDefinition(this.chartInput.title, treemap);
    }
    return this.chartInput.assetclassChart;
  }

  private resizeChart(): void {
    if (!this.noDataTextKey && this.chartElement.nativeElement.data) {
      Plotly.Plots.resize(this.chartElement.nativeElement);
    }
  }
}
