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
import { PlotlyLocales } from '../../shared/plotlylocale/plotly.locales';
import { PlotlyHelper } from '../../shared/chart/plotly.helper';
import { CorrelationChartInput, CorrelationChartService } from '../service/correlation-chart.service';
import { buildCorrelationFigure, CorrelationChartType } from './correlation-chart';

declare let Plotly: any;

/** Selectable rolling correlations with daily percentage price developments in a shared Plotly figure. */
@Component({
  template: `
    <div
      class="data-container correlation-chart"
      (click)="onComponentClick($event)"
      [ngClass]="{ 'active-border': isActivated(), 'passiv-border': !isActivated() }">
      <dynamic-form
        [config]="config"
        [formConfig]="formConfig"
        [translateService]="translateService"
        #form="dynamicForm"></dynamic-form>
      @if (chartInput?.includeDividends || chartInput?.adjustCurrency) {
        <div class="chart-adjustments">
          @if (chartInput?.includeDividends) {
            <span>{{ 'INCLUDE_DIVIDENDS' | translate }}</span>
          }
          @if (chartInput?.adjustCurrency) {
            <span>{{ 'ADJUST_CURRENCY' | translate }}</span>
          }
        </div>
      }
      @if (noData) {
        <p>{{ 'NO_DATA_AVAILABLE' | translate }}</p>
      }
      @if (missingCloses) {
        <p>{{ 'CORRELATION_CHART_MISSING_PRICES' | translate }}</p>
      }
      <div #chart class="correlation-chart-plot" [hidden]="noData"></div>
    </div>
  `,
  styles: [
    `
      .correlation-chart {
        display: flex;
        flex-direction: column;
        height: calc(100% - 8px);
        overflow: auto;
      }
      .correlation-chart-plot {
        flex: 1 1 auto;
        min-height: 380px;
        width: 100%;
      }
      .chart-adjustments {
        display: flex;
        gap: 1.5rem;
      }
    `
  ],
  standalone: true,
  changeDetection: ChangeDetectionStrategy.Eager,
  imports: [CommonModule, TranslateModule, DynamicFormModule]
})
export class CorrelationChartComponent extends FormBase implements OnInit, AfterViewInit, OnDestroy, IGlobalMenuAttach {
  @ViewChild(DynamicFormComponent, { static: true }) form: DynamicFormComponent;
  @ViewChild('chart', { static: true }) chartElement: ElementRef;
  chartInput: CorrelationChartInput;
  noData = true;
  missingCloses = false;
  private ready = false;
  private destroyed = false;
  private renderVersion = 0;
  private plotlyConfig: any;
  private subscriptions: Subscription[] = [];

  constructor(
    private chartService: CorrelationChartService,
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
    this.activePanelService.registerPanel(this);
    this.chartService.shown = true;
    this.plotlyConfig = { ...PlotlyLocales.setPlotyLocales(Plotly, this.gps), displaylogo: false, responsive: true };
    this.config = [DynamicFieldHelper.createFieldSelectStringHeqF('chartType', true, { usedLayoutColumns: 6 })];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
    const types = Object.values(CorrelationChartType);
    this.subscriptions.push(
      this.translateService.stream(types).subscribe((texts) => {
        this.configObject.chartType.valueKeyHtmlOptions = types.map(
          (type) => new ValueKeyHtmlSelectOptions(type, texts[type])
        );
        this.drawChart();
      }),
      this.chartService.input$.subscribe((input) => {
        this.chartInput = input;
        this.drawChart();
      }),
      this.viewSizeChangedService.viewSizeChanged$.subscribe(() => {
        if (!this.noData && this.chartElement.nativeElement.data) {
          Plotly.Plots.resize(this.chartElement.nativeElement);
        }
      })
    );
  }

  ngAfterViewInit(): void {
    Promise.resolve().then(() => {
      if (this.destroyed) {
        return;
      }
      const stored = this.usersettingsService.readSingleValue(this.storeKey());
      this.configObject.chartType.formControl.setValue(
        Object.values(CorrelationChartType).includes(stored) ? stored : CorrelationChartType.Combined
      );
      this.subscriptions.push(
        this.configObject.chartType.formControl.valueChanges.subscribe((type) => {
          this.usersettingsService.saveSingleValue(this.storeKey(), type);
          this.drawChart();
        })
      );
      this.ready = true;
      this.drawChart();
    });
  }

  isActivated(): boolean {
    return this.activePanelService.isActivated(this);
  }
  onComponentClick(event: Event): void {
    this.activePanelService.activatePanel(this);
  }
  hideContextMenu(): void {}
  callMeDeactivate(): void {}
  getHelpContextId(): string {
    return null;
  }

  ngOnDestroy(): void {
    this.destroyed = true;
    this.renderVersion++;
    this.subscriptions.forEach((subscription) => subscription.unsubscribe());
    Plotly.purge(this.chartElement.nativeElement);
    this.chartService.shown = false;
    this.activePanelService.destroyPanel(this);
  }

  private storeKey(): string {
    return `${this.gps.getIdUser()}_${this.gps.getIdTenant()}_correlationChartType`;
  }

  private drawChart(): void {
    if (!this.ready) {
      return;
    }
    const version = ++this.renderVersion;
    const figure = this.chartInput
      ? buildCorrelationFigure(this.chartInput, this.configObject.chartType.formControl.value)
      : null;
    this.noData = !figure?.data.length;
    this.missingCloses = !!figure?.missingCloses;
    if (this.noData) {
      Plotly.purge(this.chartElement.nativeElement);
      return;
    }
    PlotlyHelper.translateLayout(this.translateService, figure.layout);
    // Allow Angular to unhide the plot before Plotly measures its container.
    setTimeout(() => {
      if (this.destroyed || version !== this.renderVersion) {
        return;
      }
      Plotly.react(this.chartElement.nativeElement, figure.data, figure.layout, this.plotlyConfig).then(() => {
        if (
          !this.destroyed &&
          version === this.renderVersion &&
          this.chartElement.nativeElement.querySelector('g.legend')
        ) {
          PlotlyHelper.attachTooltip(Plotly, figure.legendTooltipMap, this.chartElement);
        }
      });
    });
  }
}
