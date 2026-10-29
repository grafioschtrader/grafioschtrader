import {
  afterNextRender,
  AfterRenderRef,
  AfterViewInit,
  computed,
  DestroyRef,
  Injector,
  signal,
  Component,
  ElementRef,
  OnDestroy,
  OnInit,
  inject,
  viewChild,
  effect,
  untracked
} from '@angular/core';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
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
import { PlotlyLocales } from '../../shared/plotlylocale/plotly.locales';
import { PlotlyInstance, PlotlyLoaderService } from '../../shared/chart/plotly.loader';
import { PlotlyHelper } from '../../shared/chart/plotly.helper';
import { CorrelationChartService } from '../service/correlation-chart.service';
import { buildCorrelationFigure, CorrelationChartType } from './correlation-chart';

/** Selectable rolling correlations with daily percentage price developments in a shared Plotly figure. */
@Component({
  template: `
    <div
      class="data-container correlation-chart"
      (click)="onComponentClick($event)"
      [class]="{ 'active-border': isActivated(), 'passiv-border': !isActivated() }">
      <dynamic-form
        [config]="config"
        [formConfig]="formConfig"
        [translateService]="translateService"
        #form="dynamicForm" />
      @if (chartInput()?.includeDividends || chartInput()?.adjustCurrency) {
        <div class="chart-adjustments">
          @if (chartInput()?.includeDividends) {
            <span>{{ 'INCLUDE_DIVIDENDS' | translate }}</span>
          }
          @if (chartInput()?.adjustCurrency) {
            <span>{{ 'ADJUST_CURRENCY' | translate }}</span>
          }
        </div>
      }
      @if (noData()) {
        <p>{{ 'NO_DATA_AVAILABLE' | translate }}</p>
      }
      @if (missingCloses()) {
        <p>{{ 'CORRELATION_CHART_MISSING_PRICES' | translate }}</p>
      }
      <div #chart class="correlation-chart-plot" [hidden]="noData()"></div>
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
  imports: [TranslateModule, ...DYNAMIC_FORM_IMPORTS]
})
export class CorrelationChartComponent extends FormBase implements OnInit, AfterViewInit, OnDestroy, IGlobalMenuAttach {
  private chartService: CorrelationChartService = inject(CorrelationChartService);
  private activePanelService: ActivePanelService = inject(ActivePanelService);
  private viewSizeChangedService: ViewSizeChangedService = inject(ViewSizeChangedService);
  private usersettingsService: UserSettingsService = inject(UserSettingsService);
  private gps: GlobalparameterService = inject(GlobalparameterService);
  public translateService: TranslateService = inject(TranslateService);

  readonly form = viewChild(DynamicFormComponent);
  readonly chartElement = viewChild<ElementRef>('chart');
  readonly chartInput = this.chartService.input;
  private readonly chartType = signal(CorrelationChartType.Combined);
  private readonly figure = computed(() =>
    this.chartInput() ? buildCorrelationFigure(this.chartInput(), this.chartType()) : null
  );
  readonly noData = computed(() => !this.figure()?.data.length);
  readonly missingCloses = computed(() => !!this.figure()?.missingCloses);
  private readonly destroyRef = inject(DestroyRef);
  private readonly injector = inject(Injector);
  private pendingRender?: AfterRenderRef;
  private ready = false;
  private destroyed = false;
  private readonly plotlyLoader = inject(PlotlyLoaderService);
  /** Set once the first chart has loaded the library; cleanup never triggers the import. */
  private plotly: PlotlyInstance;
  private renderVersion = 0;
  private plotlyConfig: any;

  constructor() {
    super();
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps, 2, null, true);

    effect(() => {
      this.chartInput();
      untracked(() => this.drawChart());
    });
  }

  ngOnInit(): void {
    this.activePanelService.registerPanel(this);
    this.chartService.shown = true;
    this.plotlyConfig = { ...PlotlyLocales.setPlotyLocales(this.gps), displaylogo: false, responsive: true };
    this.config = [DynamicFieldHelper.createFieldSelectStringHeqF('chartType', true, { usedLayoutColumns: 6 })];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
    const types = Object.values(CorrelationChartType);
    this.translateService
      .stream(types)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((texts) => {
        this.configObject.chartType.valueKeyHtmlOptions = types.map(
          (type) => new ValueKeyHtmlSelectOptions(type, texts[type])
        );
        this.drawChart();
      });
    this.viewSizeChangedService.viewSizeChanged$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      const chartElement = this.chartElement();
      if (!this.noData() && chartElement.nativeElement.data) {
        this.plotly?.Plots.resize(chartElement.nativeElement);
      }
    });
  }

  ngAfterViewInit(): void {
    afterNextRender(
      () => {
        if (this.destroyed) {
          return;
        }
        const stored = this.usersettingsService.readSingleValue(this.storeKey());
        const type = Object.values(CorrelationChartType).includes(stored) ? stored : CorrelationChartType.Combined;
        this.chartType.set(type);
        this.configObject.chartType.formControl.setValue(type);
        this.configObject.chartType.formControl.valueChanges
          .pipe(takeUntilDestroyed(this.destroyRef))
          .subscribe((type) => {
            this.chartType.set(type);
            this.usersettingsService.saveSingleValue(this.storeKey(), type);
            this.drawChart();
          });
        this.ready = true;
        this.drawChart();
      },
      { injector: this.injector }
    );
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
    this.pendingRender?.destroy();
    this.plotly?.purge(this.chartElement().nativeElement);
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
    this.pendingRender?.destroy();
    const figure = this.figure();
    if (this.noData()) {
      this.plotly?.purge(this.chartElement().nativeElement);
      return;
    }
    const layout = structuredClone(figure.layout);
    const data = structuredClone(figure.data);
    PlotlyHelper.translateLayout(this.translateService, layout);
    // Allow Angular to unhide the plot before Plotly measures its container.
    this.pendingRender = afterNextRender(
      () =>
        this.plotlyLoader.load().then(
          (plotly) => {
            if (this.destroyed || version !== this.renderVersion) {
              return;
            }
            this.plotly = plotly;
            plotly.react(this.chartElement().nativeElement, data, layout, this.plotlyConfig).then(() => {
              const chartElement = this.chartElement();
              if (
                !this.destroyed &&
                version === this.renderVersion &&
                chartElement.nativeElement.querySelector('g.legend')
              ) {
                PlotlyHelper.attachTooltip(figure.legendTooltipMap, chartElement);
              }
            });
          },
          () => {}
        ),
      { injector: this.injector }
    );
  }
}
