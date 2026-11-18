import {
  Component,
  ElementRef,
  Injector,
  OnDestroy,
  afterNextRender,
  effect,
  inject,
  signal,
  untracked,
  viewChild
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { metadata, SchemaPathTree } from '@angular/forms/signals';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import type { Data, Layout } from 'plotly.js';

import type { ColumnConfig } from '../../lib/datashowbase/column.config';
import { ShowRecordConfigBase } from '../../lib/datashowbase/show.record.config.base';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { FormConfig } from '../../lib/dynamic-form/models/form.config';
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
import { PlotlyHelper } from '../../shared/chart/plotly.helper';
import { PlotlyInstance, PlotlyLoaderService } from '../../shared/chart/plotly.loader';
import { PlotlyLocales } from '../../shared/plotlylocale/plotly.locales';
import type { SecurityPeriodPerformance } from '../model/security-period-performance';
import {
  SecurityPerformanceChartFormat,
  SecurityPerformanceChartSurface,
  securityPerformanceChartLayout,
  securityPerformanceChartPoints,
  securityPerformanceTreemap,
  securityPerformanceTreemapLayout
} from '../model/security-period-performance.chart';
import { SecurityPeriodPerformanceChartService } from '../service/security-period-performance-chart.service';

/** The charts the user can choose from. The keys are also their translation keys. */
enum SecurityPerformanceChartType {
  SECURITY_PERIOD_CHART_SCATTER = 'SECURITY_PERIOD_CHART_SCATTER',
  SECURITY_PERIOD_CHART_TREEMAP = 'SECURITY_PERIOD_CHART_TREEMAP'
}

/** The select of the chart type. */
interface ChartTypeFormModel {
  chartType: string | null;
}

/** A Plotly figure together with the security of every point or tile, null for a tile that is no security. */
interface SecurityPerformanceFigure {
  data: Data[];
  layout: Partial<Layout>;
  idsSecuritycurrency: (number | null)[];
}

/**
 * Charts of the view "Performance by security" in the lower display area. The user chooses between the scatter chart
 * of return against profit and the treemap of the profits and losses; the choice is remembered. The view publishes
 * every calculation through {@link SecurityPeriodPerformanceChartService}, so the chart follows it, and a click on a
 * security travels back to expand its row in the table.
 */
@Component({
  // Routed into the lower display area; the selector names the host element for the e2e spec
  selector: 'security-period-performance-chart',
  template: `
    <div
      class="data-container security-performance-chart"
      (click)="onComponentClick($event)"
      [class.active-border]="isActivated()"
      [class.passiv-border]="!isActivated()">
      <signal-dynamic-form
        [config]="config"
        [formConfig]="formConfig"
        [rules]="rules"
        [values]="startValues"
        #form="signalDynamicForm" />
      @if (!chartService.result()) {
        <h4>{{ 'NO_DATA_AVAILABLE' | translate }}</h4>
      }
      <div #chart class="security-performance-chart-plot" [hidden]="!chartService.result()"></div>
    </div>
  `,
  styles: [
    `
      .security-performance-chart {
        display: flex;
        flex-direction: column;
        height: calc(100% - 8px);
      }
      .security-performance-chart-plot {
        flex: 1 1 auto;
        min-height: 320px;
        width: 100%;
      }
    `
  ],
  imports: [TranslateModule, SignalDynamicFormComponent]
})
export class SecurityPeriodPerformanceChartComponent implements IGlobalMenuAttach, OnDestroy {
  readonly chartService = inject(SecurityPeriodPerformanceChartService);
  private readonly activePanelService = inject(ActivePanelService);
  private readonly usersettingsService = inject(UserSettingsService);
  private readonly gps = inject(GlobalparameterService);
  private readonly translateService = inject(TranslateService);
  private readonly plotlyLoader = inject(PlotlyLoaderService);
  private readonly elementRef: ElementRef<HTMLElement> = inject(ElementRef);
  private readonly renderInjector = inject(Injector);

  private readonly form = viewChild<SignalDynamicFormComponent<ChartTypeFormModel>>('form');
  private readonly chartElement = viewChild.required<ElementRef<HTMLElement>>('chart');

  readonly formConfig: FormConfig = AppHelper.getDefaultFormConfig(this.gps, 2, null, true);
  readonly config: FieldFormGroup[] = [
    DynamicFieldHelper.createFieldSelectStringHeqF('chartType', true, { usedLayoutColumns: 6 })
  ];
  /** The chart type chosen last, or the scatter chart. */
  readonly startValues: Partial<ChartTypeFormModel> = { chartType: this.storedChartType() };

  private readonly chartTypeOptions = signal<ValueKeyHtmlSelectOptions[]>([]);
  readonly rules = (path: SchemaPathTree<ChartTypeFormModel>): void => {
    metadata(path.chartType, FIELD_DISPLAY, () => ({ valueKeyHtmlOptions: this.chartTypeOptions() }));
  };

  private readonly amountField = ShowRecordConfigBase.createColumnConfig(DataType.Numeric, 'value', '');
  private readonly percentField = ShowRecordConfigBase.createColumnConfig(DataType.Numeric, 'value', '', true, true, {
    maxFractionDigits: AppSettings.FID_PERCENTAGE_FRACTION
  });
  private readonly daysField = ShowRecordConfigBase.createColumnConfig(DataType.NumericInteger, 'value', '');
  private readonly dateField = ShowRecordConfigBase.createColumnConfig(DataType.DateString, 'value', '');
  private readonly plotlyConfig = { ...PlotlyLocales.setPlotyLocales(this.gps), displaylogo: false, responsive: true };

  /** Set once the first chart has loaded the library; cleanup never triggers the import. */
  private plotly?: PlotlyInstance;
  private destroyed = false;
  /** Raised by every draw, so a draw still waiting for the library is dropped when a newer one follows. */
  private drawVersion = 0;

  constructor() {
    TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config, false);
    const chartTypes = Object.values(SecurityPerformanceChartType);
    this.translateService
      .get(chartTypes)
      .pipe(takeUntilDestroyed())
      .subscribe((texts: Record<string, string>) =>
        this.chartTypeOptions.set(chartTypes.map((type) => new ValueKeyHtmlSelectOptions(type, texts[type])))
      );
    effect(() => {
      const chartType = this.form()?.value('chartType') as string | null | undefined;
      const result = this.chartService.result();
      untracked(() => this.chartTypeOrResultChanged(chartType ?? null, result));
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
    return HelpIds.HELP_PORTFOLIOS_SECURITY_PERIODPERFORMANCE;
  }

  ngOnDestroy(): void {
    this.destroyed = true;
    this.plotly?.purge(this.chartElement().nativeElement);
    this.activePanelService.destroyPanel(this);
  }

  private storedChartType(): SecurityPerformanceChartType {
    const stored = this.usersettingsService.readSingleValue(this.getStoreKey());
    return Object.values(SecurityPerformanceChartType).includes(stored)
      ? stored
      : SecurityPerformanceChartType.SECURITY_PERIOD_CHART_SCATTER;
  }

  private getStoreKey(): string {
    return this.gps.getIdTenant() + '_' + AppSettings.SECURITY_PERFORMANCE_CHART_TYPE;
  }

  private chartTypeOrResultChanged(chartType: string | null, result: SecurityPeriodPerformance | null): void {
    if (chartType) {
      this.usersettingsService.saveSingleValue(this.getStoreKey(), chartType);
    }
    const version = ++this.drawVersion;
    // A plot exists only after the view was rendered, so the element is read only then
    if (this.plotly) {
      this.plotly.purge(this.chartElement().nativeElement);
    }
    if (!chartType || !result) {
      return;
    }
    const figure =
      chartType === SecurityPerformanceChartType.SECURITY_PERIOD_CHART_TREEMAP
        ? this.treemapFigure(result)
        : this.scatterFigure(result);
    // Wait until the chart element is no longer hidden, otherwise Plotly measures a zero size.
    afterNextRender(
      () =>
        this.plotlyLoader.load().then(
          (plotly) => {
            if (!this.destroyed && version === this.drawVersion) {
              this.plotly = plotly;
              this.plot(plotly, result, figure);
            }
          },
          () => {}
        ),
      { injector: this.renderInjector }
    );
  }

  /** Draws the figure; a new plot drops the listeners of the previous one, so the click is registered again. */
  private plot(plotly: PlotlyInstance, result: SecurityPeriodPerformance, figure: SecurityPerformanceFigure): void {
    const element = this.chartElement().nativeElement;
    plotly
      .newPlot(element, figure.data, figure.layout, this.plotlyConfig)
      .then(() =>
        PlotlyHelper.registerPlotlyClick(element, (_traceIndex, index) => this.clicked(result, figure, index))
      );
  }

  private clicked(result: SecurityPeriodPerformance, figure: SecurityPerformanceFigure, index: number): void {
    const idSecuritycurrency = figure.idsSecuritycurrency[index];
    if (idSecuritycurrency != null) {
      this.chartService.clickSecurity(result, idSecuritycurrency);
    }
  }

  private scatterFigure(result: SecurityPeriodPerformance): SecurityPerformanceFigure {
    const texts = {
      title: `${this.translateService.instant('PERFORMANCE_SECURITIES')}: ${this.periodText(result)}`,
      gain: this.translateService.instant('GAIN_PERIOD'),
      twr: this.translateService.instant('TWR_POSITION_PERCENT'),
      holdingDays: this.translateService.instant('HOLDING_DAYS')
    };
    const points = securityPerformanceChartPoints(result.rows, texts, this.chartFormat(result));
    const notShown = points.notShown
      ? this.translateService.instant('SECURITY_PERIOD_CHART_NOT_SHOWN', { count: points.notShown })
      : null;
    return {
      data: [points.trace],
      layout: securityPerformanceChartLayout(result, texts, notShown, this.surface()),
      idsSecuritycurrency: points.idsSecuritycurrency
    };
  }

  private treemapFigure(result: SecurityPeriodPerformance): SecurityPerformanceFigure {
    const format = this.chartFormat(result);
    const texts = {
      root: this.periodText(result),
      gains: this.translateService.instant('SECURITY_PERIOD_GAINS'),
      losses: this.translateService.instant('SECURITY_PERIOD_LOSSES'),
      holdingDays: this.translateService.instant('HOLDING_DAYS'),
      share: this.translateService.instant('SECURITY_PERIOD_SHARE')
    };
    const treemap = securityPerformanceTreemap(result.rows, texts, format);
    const subtitle =
      `${texts.gains}: ${format.amount(treemap.gainsMC)} · ${texts.losses}: ${format.amount(-treemap.lossesMC)} · ` +
      `${this.translateService.instant('BALANCE')}: ${format.amount(result.totals.gainPeriodMC)}`;
    const notShown = treemap.notShown
      ? this.translateService.instant('SECURITY_PERIOD_TREEMAP_NOT_SHOWN', { count: treemap.notShown })
      : null;
    const title = `${this.translateService.instant('PERFORMANCE_SECURITIES')}: ${texts.root}`;
    return {
      // The typings of plotly.js do not describe every treemap attribute, so the trace is built untyped
      data: [treemap.trace as Data],
      layout: securityPerformanceTreemapLayout(title, subtitle, notShown, this.surface()),
      idsSecuritycurrency: treemap.idsSecuritycurrency
    };
  }

  /** Every figure of a tooltip is formatted by GT's data types, so it reads like the table. */
  private chartFormat(result: SecurityPeriodPerformance): SecurityPerformanceChartFormat {
    return {
      amount: (value) => this.format(value, this.amountField) + ' ' + result.mainCurrency,
      percent: (value) => this.format(value, this.percentField),
      days: (value) => this.format(value, this.daysField)
    };
  }

  private periodText(result: SecurityPeriodPerformance): string {
    return `${this.format(result.dateFrom, this.dateField)} - ${this.format(result.dateTo, this.dateField)}`;
  }

  /** The text and border colors of the active theme, read from its CSS variables. */
  private surface(): SecurityPerformanceChartSurface {
    const style = getComputedStyle(this.elementRef.nativeElement);
    return {
      textColor: style.getPropertyValue('--p-text-color').trim() || '#52514e',
      gridColor: style.getPropertyValue('--p-content-border-color').trim() || '#e1e0d9',
      separators: this.gps.getDecimalSymbol() + this.gps.getThousandsSeparatorSymbol()
    };
  }

  private format(value: number | string, field: ColumnConfig): string {
    return AppHelper.getValueByPathWithField(this.gps, this.translateService, { value }, field, 'value') ?? '';
  }

  private resizeChart(): void {
    const element = this.chartElement().nativeElement as HTMLElement & { data?: unknown };
    if (this.chartService.result() && element.data) {
      this.plotly?.Plots.resize(element);
    }
  }
}
