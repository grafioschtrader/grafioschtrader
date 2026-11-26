import {
  afterNextRender,
  Component,
  effect,
  ElementRef,
  inject,
  Injector,
  OnDestroy,
  signal,
  untracked,
  viewChild
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { metadata, SchemaPathTree } from '@angular/forms/signals';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import type { FormConfig } from '../../lib/dynamic-form/models/form.config';
import type { FieldFormGroup } from '../../lib/dynamic-form/models/form.group.definition';
import { ValueKeyHtmlSelectOptions } from '../../lib/dynamic-form/models/value.key.html.select.options';
import { SignalDynamicFormComponent } from '../../lib/dynamic-form/signal/signal-dynamic-form.component';
import { FIELD_DISPLAY } from '../../lib/dynamic-form/signal/signal-form.model';
import { HelpIds } from '../../lib/help/help.ids';
import { AppHelper } from '../../lib/helper/app.helper';
import { DynamicFieldHelper } from '../../lib/helper/dynamic.field.helper';
import { TranslateHelper } from '../../lib/helper/translate.helper';
import { ViewSizeChangedService } from '../../lib/layout/service/view.size.changed.service';
import type { IGlobalMenuAttach, PanelClickEvent } from '../../lib/mainmenubar/component/iglobal.menu.attach';
import { ActivePanelService } from '../../lib/mainmenubar/service/active.panel.service';
import { GlobalparameterService } from '../../lib/services/globalparameter.service';
import { UserSettingsService } from '../../lib/services/user.settings.service';
import { AppSettings } from '../../shared/app.settings';
import { PlotlyInstance, PlotlyLoaderService } from '../../shared/chart/plotly.loader';
import { PlotlyLocales } from '../../shared/plotlylocale/plotly.locales';
import {
  AMOUNT_FIELDS,
  amountTraces,
  PerformanceChartNames,
  performanceChartLayout,
  performanceChartModes,
  PerformancePeriodChartMode,
  RETURN_FIELDS,
  returnTraces,
  WEALTH_FIELDS,
  wealthTraces
} from '../model/performance-period.chart';
import type { PerformancePeriod } from '../model/performance.period';
import { PerformancePeriodChartService } from '../service/performance-period-chart.service';
import { PlotlyHelper } from '../../shared/chart/plotly.helper';
import { ThemeService } from '../../lib/theme/theme.service';

/** The select of the chart mode. */
interface ChartModeFormModel {
  chartMode: string | null;
}

/**
 * Chart of the view "Period performance" in the lower display area. It shows the changes of the balance since the
 * first day and, when the report carries a benchmark, the cumulative returns of the portfolio and of the benchmark
 * over their common intervals. A change of the mode redraws the loaded period at once; the choice is remembered.
 */
@Component({
  // Routed into the lower display area; the selector names the host element for the e2e spec
  selector: 'performance-period-chart',
  template: `
    <div
      class="data-container performance-period-chart"
      (click)="onComponentClick($event)"
      [class.active-border]="isActivated()"
      [class.passiv-border]="!isActivated()">
      <signal-dynamic-form
        [config]="config"
        [formConfig]="formConfig"
        [rules]="rules"
        [values]="startValues"
        #form="signalDynamicForm" />
      @if (!chartService.period()) {
        <h4>{{ 'NO_DATA_AVAILABLE' | translate }}</h4>
      }
      <div #chart class="performance-period-chart-plot" [hidden]="!chartService.period()"></div>
    </div>
  `,
  styles: [
    `
      .performance-period-chart {
        display: flex;
        flex-direction: column;
        height: calc(100% - 8px);
      }
      .performance-period-chart-plot {
        flex: 1 1 auto;
        min-height: 320px;
        width: 100%;
      }
    `
  ],
  imports: [TranslateModule, SignalDynamicFormComponent]
})
export class PerformancePeriodChartComponent implements IGlobalMenuAttach, OnDestroy {
  readonly chartService = inject(PerformancePeriodChartService);
  private readonly activePanelService = inject(ActivePanelService);
  private readonly usersettingsService = inject(UserSettingsService);
  private readonly gps = inject(GlobalparameterService);
  private readonly translateService = inject(TranslateService);
  private readonly plotlyLoader = inject(PlotlyLoaderService);
  private readonly renderInjector = inject(Injector);

  private readonly form = viewChild<SignalDynamicFormComponent<ChartModeFormModel>>('form');
  private readonly chartElement = viewChild.required<ElementRef<HTMLElement>>('chart');

  readonly formConfig: FormConfig = AppHelper.getDefaultFormConfig(this.gps, 2, null, true);
  readonly config: FieldFormGroup[] = [
    DynamicFieldHelper.createFieldSelectStringHeqF('chartMode', true, { usedLayoutColumns: 6 })
  ];
  /** The mode chosen last, or the amounts. */
  readonly startValues: Partial<ChartModeFormModel> = { chartMode: this.storedChartMode() };

  private readonly modeLabels = signal<Record<string, string>>({});
  private readonly traceNames = signal<PerformanceChartNames>({});
  readonly rules = (path: SchemaPathTree<ChartModeFormModel>): void => {
    metadata(path.chartMode, FIELD_DISPLAY, () => ({
      valueKeyHtmlOptions: performanceChartModes(this.chartService.period()).map(
        (mode) => new ValueKeyHtmlSelectOptions(mode, this.modeLabels()[mode] ?? mode)
      )
    }));
  };

  private readonly plotlyConfig = { ...PlotlyLocales.setPlotyLocales(this.gps), displaylogo: false, responsive: true };

  /** Set once the first chart has loaded the library; cleanup never triggers the import. */
  private plotly?: PlotlyInstance;
  private destroyed = false;
  /** Raised by every draw, so a draw still waiting for the library is dropped when a newer one follows. */
  private drawVersion = 0;
  private readonly themeService = inject(ThemeService);

  constructor() {
    TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config, false);
    PlotlyHelper.effectOnThemeChange(this.themeService.isDark, () => {
      if (this.plotly && !this.destroyed) {
        PlotlyHelper.restyleForTheme(this.plotly, this.chartElement().nativeElement, this.plotlyConfig);
      }
    });
    this.loadTexts();
    effect(() => {
      const chartMode = this.form()?.value('chartMode') as string | null | undefined;
      const period = this.chartService.period();
      const names = this.traceNames();
      untracked(() => this.modeOrPeriodChanged(chartMode ?? null, period, names));
    });
    inject(ViewSizeChangedService)
      .viewSizeChanged$.pipe(takeUntilDestroyed())
      .subscribe(() => this.resizeChart());
    this.onComponentClick(null);
  }

  isActivated(): boolean {
    return this.activePanelService.isActivated(this);
  }

  onComponentClick(_event: PanelClickEvent | null): void {
    this.activePanelService.activatePanel(this);
  }

  hideContextMenu(): void {}

  callMeDeactivate(): void {}

  getHelpContextId(): string {
    return HelpIds.HELP_PORTFOLIOS_PERIODPERFORMANCE;
  }

  ngOnDestroy(): void {
    this.destroyed = true;
    this.plotly?.purge(this.chartElement().nativeElement);
    this.activePanelService.destroyPanel(this);
  }

  private loadTexts(): void {
    const modes = Object.values(PerformancePeriodChartMode);
    const amountKeys = AMOUNT_FIELDS.map((field) => AppHelper.removeSomeStringAndToUpperCaseWithUnderscore(field));
    const returnKeys = RETURN_FIELDS.map((field) => AppHelper.removeSomeStringAndToUpperCaseWithUnderscore(field));
    const wealthKeys = WEALTH_FIELDS.map((field) => AppHelper.removeSomeStringAndToUpperCaseWithUnderscore(field));
    this.translateService
      .get([...modes, ...amountKeys, ...returnKeys, ...wealthKeys])
      .pipe(takeUntilDestroyed())
      .subscribe((texts: Record<string, string>) => {
        this.modeLabels.set(Object.fromEntries(modes.map((mode) => [mode, texts[mode]])));
        const names: PerformanceChartNames = {};
        AMOUNT_FIELDS.forEach((field, i) => (names[field] = texts[amountKeys[i]]));
        RETURN_FIELDS.forEach((field, i) => (names[field] = texts[returnKeys[i]]));
        WEALTH_FIELDS.forEach((field, i) => (names[field] = texts[wealthKeys[i]]));
        this.traceNames.set(names);
      });
  }

  private storedChartMode(): PerformancePeriodChartMode {
    const stored = this.usersettingsService.readSingleValue(this.getStoreKey());
    return Object.values(PerformancePeriodChartMode).includes(stored) ? stored : PerformancePeriodChartMode.AMOUNTS;
  }

  private getStoreKey(): string {
    return this.gps.getIdTenant() + '_' + AppSettings.PERFORMANCE_PERIOD_CHART_MODE;
  }

  private modeOrPeriodChanged(
    chartMode: string | null,
    period: PerformancePeriod | null,
    names: PerformanceChartNames
  ): void {
    if (chartMode) {
      this.usersettingsService.saveSingleValue(this.getStoreKey(), chartMode);
    }
    const version = ++this.drawVersion;
    if (this.plotly) {
      this.plotly.purge(this.chartElement().nativeElement);
    }
    if (!period) {
      return;
    }
    // A period without benchmark falls back to the amounts, whatever was remembered
    const mode =
      chartMode && performanceChartModes(period).includes(chartMode as PerformancePeriodChartMode)
        ? chartMode
        : PerformancePeriodChartMode.AMOUNTS;
    const percent = mode === PerformancePeriodChartMode.RETURNS_PERCENT;
    const data = percent
      ? returnTraces(period, names)
      : mode === PerformancePeriodChartMode.WEALTH
        ? wealthTraces(period, names)
        : amountTraces(period, names);
    const layout = performanceChartLayout(period, percent);
    // Wait until the chart element is no longer hidden, otherwise Plotly measures a zero size.
    afterNextRender(
      () =>
        this.plotlyLoader.load().then(
          (plotly) => {
            if (!this.destroyed && version === this.drawVersion) {
              this.plotly = plotly;
              layout.uirevision = mode;
              PlotlyHelper.applyThemeToLayout(layout, PlotlyHelper.getThemeSurface(this.chartElement().nativeElement));
              plotly.newPlot(this.chartElement().nativeElement, data, layout, this.plotlyConfig);
            }
          },
          () => {}
        ),
      { injector: this.renderInjector }
    );
  }

  private resizeChart(): void {
    const element = this.chartElement().nativeElement as HTMLElement & { data?: unknown };
    if (this.chartService.period() && element.data) {
      this.plotly?.Plots.resize(element);
    }
  }
}
