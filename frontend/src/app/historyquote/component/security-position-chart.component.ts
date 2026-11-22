import {
  Component,
  ElementRef,
  Injector,
  OnDestroy,
  afterNextRender,
  computed,
  effect,
  inject,
  input,
  linkedSignal,
  signal,
  untracked,
  viewChild,
  WritableSignal
} from '@angular/core';
import { rxResource, takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { hidden, metadata, SchemaPathTree } from '@angular/forms/signals';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { MenuItem } from '@openng/optimus-ui/api';
import { ContextMenu } from '@openng/optimus-ui/contextmenu';
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
import { reportResourceErrors } from '../../lib/login/service/resource.error.report';
import type { IGlobalMenuAttach, PanelClickEvent } from '../../lib/mainmenubar/component/iglobal.menu.attach';
import { ActivePanelService } from '../../lib/mainmenubar/service/active.panel.service';
import { GlobalparameterService } from '../../lib/services/globalparameter.service';
import { UserSettingsService } from '../../lib/services/user.settings.service';
import type {
  SecurityPositionCurrencySeries,
  SecurityPositionEvent,
  SecurityPositionSeries
} from '../../performanceperiod/model/security-position-series';
import { HoldingService } from '../../performanceperiod/service/holding.service';
import { AppSettings } from '../../shared/app.settings';
import { PlotlyInstance, PlotlyLoaderService } from '../../shared/chart/plotly.loader';
import { PlotlyLocales } from '../../shared/plotlylocale/plotly.locales';
import {
  INVESTED_LINE,
  PositionChartTexts,
  PositionEdgeLabel,
  PositionLineTrace,
  VALUE_LINE,
  positionAreaTraces,
  positionChartLayout,
  positionDrawdownTraces,
  positionValueTraces,
  visibleValueRange
} from '../model/security-position.chart';
import { PositionEventKind, PositionEventTrace, positionEventTraces } from '../model/security.chart.series';
import { TimeSeriesParam } from '../model/time.series.param';
import { TimeSeriesQuotesService } from '../service/time.series.quotes.service';

/** The charts of one position the user can choose from, in the order of the select; the keys are their text keys. */
enum SecurityPositionChartType {
  POSITION_CHART_RESULT = 'POSITION_CHART_RESULT',
  POSITION_CHART_VALUE = 'POSITION_CHART_VALUE',
  POSITION_CHART_PERFORMANCE = 'POSITION_CHART_PERFORMANCE',
  POSITION_CHART_DRAWDOWN = 'POSITION_CHART_DRAWDOWN'
}

/** The chart types whose figure depends on the basis: open position or total since entry. */
const CHART_TYPES_WITH_BASIS: string[] = [
  SecurityPositionChartType.POSITION_CHART_RESULT,
  SecurityPositionChartType.POSITION_CHART_PERFORMANCE
];

/** The chart types drawn as an area filled to 0; their value axis keeps 0 when it follows the visible window. */
const AREA_CHART_TYPES: string[] = [...CHART_TYPES_WITH_BASIS, SecurityPositionChartType.POSITION_CHART_DRAWDOWN];

/** A drawn figure: all traces, the lines among them whose values the value axis follows, and the layout. */
interface PositionFigure {
  data: (PositionLineTrace | PositionEventTrace)[];
  lines: PositionLineTrace[];
  layout: object;
}

/** The last value that is not null, null when there is none. */
function lastPresent(values: (number | null)[]): number | null {
  for (let i = values.length - 1; i >= 0; i--) {
    const value = values[i];
    if (value != null) {
      return value;
    }
  }
  return null;
}

/** Base of the result and the percentage; the keys are also their translation keys. */
enum PerformanceBasis {
  OPEN_POSITION = 'OPEN_POSITION',
  TOTAL_SINCE_ENTRY = 'TOTAL_SINCE_ENTRY'
}

/** Currency of the series. */
enum CurrencyKind {
  MAIN = 'MAIN',
  SECURITY = 'SECURITY'
}

/** The selects above the chart. */
interface PositionChartFormModel {
  chartType: string | null;
  holdingPhase: string | null;
  performanceBasis: string | null;
  currencyKind: string | null;
}

const EVENT_KINDS: PositionEventKind[] = ['PURCHASE', 'SALE', 'TRANSFER', 'INCOME', 'FINANCE_COST'];

/**
 * Value, performance and drawdown of the user's own position in one security, in the scope of the view it was opened
 * from (tenant, portfolio or security account). One holding phase is shown at a time; the newest is preselected and
 * the others can be chosen. The chart type is remembered per tenant. The backend delivers every series ready to plot,
 * so this component only selects, formats and draws.
 */
@Component({
  selector: 'security-position-chart',
  template: `
    <div
      #container
      class="data-container security-position-chart"
      (click)="onComponentClick($event)"
      (contextmenu)="onComponentClick($event)"
      [class.active-border]="isActivated()"
      [class.passiv-border]="!isActivated()">
      <signal-dynamic-form
        [config]="config"
        [formConfig]="formConfig"
        [rules]="rules"
        [values]="startValues"
        #form="signalDynamicForm" />
      @if (headline(); as text) {
        <div class="position-headline">{{ text }}</div>
      }
      @if (emptyTextKey(); as key) {
        <h4>{{ key | translate }}</h4>
      }
      <div #chart class="security-position-chart-plot" [hidden]="!plotted()"></div>
      @for (line of summaryLines(); track $index) {
        <div class="position-summary">{{ line }}</div>
      }
      @for (note of notes(); track note) {
        <div class="position-note">{{ note | translate }}</div>
      }
      <p-contextMenu [model]="contextMenuItems()" [target]="container" appendTo="body" />
    </div>
  `,
  styles: [
    `
      .security-position-chart {
        display: flex;
        flex-direction: column;
        height: calc(100% - 8px);
      }
      .security-position-chart-plot {
        flex: 1 1 auto;
        min-height: 320px;
        width: 100%;
      }
      .position-headline {
        font-weight: bold;
        margin: 4px 0;
      }
      .position-summary,
      .position-note {
        font-size: 0.9em;
        margin-top: 2px;
      }
    `
  ],
  imports: [TranslateModule, SignalDynamicFormComponent, ContextMenu]
})
export class SecurityPositionChartComponent implements IGlobalMenuAttach, OnDestroy {
  /** JSON of a one-element TimeSeriesParam[]: the security and the scope of the calling view. */
  readonly allParam = input.required<string>();

  private readonly holdingService = inject(HoldingService);
  private readonly timeSeriesQuotesService = inject(TimeSeriesQuotesService);
  private readonly activePanelService = inject(ActivePanelService);
  private readonly usersettingsService = inject(UserSettingsService);
  private readonly gps = inject(GlobalparameterService);
  private readonly translateService = inject(TranslateService);
  private readonly plotlyLoader = inject(PlotlyLoaderService);
  private readonly elementRef: ElementRef<HTMLElement> = inject(ElementRef);
  private readonly renderInjector = inject(Injector);

  private readonly form = viewChild<SignalDynamicFormComponent<PositionChartFormModel>>('form');
  private readonly chartElement = viewChild.required<ElementRef<HTMLElement>>('chart');

  readonly formConfig: FormConfig = AppHelper.getDefaultFormConfig(this.gps, 2, null, true);
  /**
   * Two rows of two half-width fields: the label takes labelColumns of the field's own columns, so a narrower field
   * leaves its select no room. A hidden field keeps its place, so the others do not move when the chart type changes.
   */
  readonly config: FieldFormGroup[] = [
    DynamicFieldHelper.createFieldSelectStringHeqF('chartType', true, { usedLayoutColumns: 6 }),
    DynamicFieldHelper.createFieldSelectStringHeqF('performanceBasis', true, { usedLayoutColumns: 6 }),
    DynamicFieldHelper.createFieldSelectStringHeqF('holdingPhase', false, { usedLayoutColumns: 6 }),
    DynamicFieldHelper.createFieldSelectStringHeqF('currencyKind', true, { usedLayoutColumns: 6 })
  ];
  readonly startValues: Partial<PositionChartFormModel> = {
    chartType: this.storedChartType(),
    holdingPhase: null,
    performanceBasis: PerformanceBasis.OPEN_POSITION,
    currencyKind: CurrencyKind.MAIN
  };

  private readonly param = computed<TimeSeriesParam>(() => JSON.parse(this.allParam())[0]);
  /** First day of the phase the user picked; null for the newest. A different security starts over. */
  private readonly requestedPhase = linkedSignal<TimeSeriesParam, string | null>({
    source: this.param,
    computation: () => null
  });
  readonly seriesResource = rxResource({
    params: () => ({ param: this.param(), phaseFrom: this.requestedPhase() }),
    stream: ({ params }) =>
      this.holdingService.getSecurityPositionSeries(
        params.param.idSecuritycurrency,
        params.param.idPortfolio,
        params.param.idSecurityaccount,
        params.phaseFrom
      )
  });
  readonly result = computed<SecurityPositionSeries | null>(() =>
    this.seriesResource.hasValue() ? this.seriesResource.value() : null
  );

  private readonly chartTypeOptions = signal<ValueKeyHtmlSelectOptions[]>([]);
  /**
   * The value chart is left out for a CFD or Forex position: its entry value is the whole exposure, against which the
   * open result is too small to be read.
   */
  private readonly availableChartTypes = computed(() =>
    this.result()?.marginInstrument
      ? this.chartTypeOptions().filter((o) => o.key !== SecurityPositionChartType.POSITION_CHART_VALUE)
      : this.chartTypeOptions()
  );
  private readonly basisOptions = signal<ValueKeyHtmlSelectOptions[]>([]);
  private readonly phaseOptions = computed(() => this.buildPhaseOptions(this.result()));
  private readonly currencyOptions = computed(() => this.buildCurrencyOptions(this.result()));
  readonly rules = (p: SchemaPathTree<PositionChartFormModel>): void => {
    metadata(p.chartType, FIELD_DISPLAY, () => ({ valueKeyHtmlOptions: this.availableChartTypes() }));
    metadata(p.holdingPhase, FIELD_DISPLAY, () => ({ valueKeyHtmlOptions: this.phaseOptions() }));
    metadata(p.performanceBasis, FIELD_DISPLAY, () => ({ valueKeyHtmlOptions: this.basisOptions() }));
    metadata(p.currencyKind, FIELD_DISPLAY, () => ({ valueKeyHtmlOptions: this.currencyOptions() }));
    hidden(p.holdingPhase, { when: () => (this.result()?.phases.length ?? 0) < 2 });
    hidden(p.performanceBasis, {
      when: ({ valueOf }) => !CHART_TYPES_WITH_BASIS.includes(valueOf(p.chartType) ?? '')
    });
    hidden(p.currencyKind, { when: () => !this.result()?.main });
  };

  readonly contextMenuItems = signal<MenuItem[]>([]);
  readonly emptyTextKey = computed(() => this.emptyKey(this.result()));
  readonly plotted = computed(() => this.result() != null && this.emptyTextKey() == null);
  readonly headline = computed(() => this.buildHeadline(this.result()));
  readonly summaryLines = computed(() => this.buildSummary());
  readonly notes = computed(() => this.buildNotes());

  private readonly amountField = ShowRecordConfigBase.createColumnConfig(DataType.Numeric, 'value', '');
  private readonly percentField = ShowRecordConfigBase.createColumnConfig(DataType.Numeric, 'value', '', true, true, {
    maxFractionDigits: AppSettings.FID_PERCENTAGE_FRACTION
  });
  private readonly unitsField = ShowRecordConfigBase.createColumnConfig(DataType.Numeric, 'value', '');
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
    this.translateOptions(Object.values(SecurityPositionChartType), this.chartTypeOptions);
    this.translateOptions(Object.values(PerformanceBasis), this.basisOptions);
    reportResourceErrors(this.seriesResource, this.holdingService);
    effect(() => {
      const phase = this.form()?.value('holdingPhase') as string | null | undefined;
      untracked(() => this.phaseChosen(phase ?? null));
    });
    effect(() => {
      const form = this.form();
      const chartType = form?.value('chartType') as string | null | undefined;
      const basis = form?.value('performanceBasis') as string | null | undefined;
      const currencyKind = form?.value('currencyKind') as string | null | undefined;
      const result = this.result();
      untracked(() => this.redraw(chartType ?? null, basis ?? null, currencyKind ?? null, result));
    });
    inject(ViewSizeChangedService)
      .viewSizeChanged$.pipe(takeUntilDestroyed())
      .subscribe(() => this.resizeChart());
    this.onComponentClick(null);
  }

  isActivated(): boolean {
    return this.activePanelService.isActivated(this);
  }

  onComponentClick(_event: PanelClickEvent | MouseEvent | null): void {
    const items: MenuItem[] = [
      {
        label: 'LINE_CHART',
        command: () => this.timeSeriesQuotesService.showChartOfSecurity(AppSettings.TIME_SERIE_QUOTES, this.param())
      }
    ];
    TranslateHelper.translateMenuItems(items, this.translateService, false);
    this.contextMenuItems.set(items);
    this.activePanelService.activatePanel(this, { showMenu: items, editMenu: null });
  }

  hideContextMenu(): void {}

  callMeDeactivate(): void {}

  getHelpContextId(): string {
    return HelpIds.HELP_WATCHLIST_HISTORYQUOTES_CHART;
  }

  ngOnDestroy(): void {
    this.destroyed = true;
    this.plotly?.purge(this.chartElement().nativeElement);
    this.activePanelService.destroyPanel(this);
  }

  private translateOptions(keys: string[], target: WritableSignal<ValueKeyHtmlSelectOptions[]>): void {
    this.translateService
      .get(keys)
      .pipe(takeUntilDestroyed())
      .subscribe((texts: Record<string, string>) =>
        target.set(keys.map((key) => new ValueKeyHtmlSelectOptions(key, texts[key])))
      );
  }

  private storedChartType(): SecurityPositionChartType {
    const stored = this.usersettingsService.readSingleValue(this.getStoreKey());
    return Object.values(SecurityPositionChartType).includes(stored)
      ? stored
      : SecurityPositionChartType.POSITION_CHART_RESULT;
  }

  private getStoreKey(): string {
    return this.gps.getIdTenant() + '_' + AppSettings.SECURITY_POSITION_CHART_TYPE;
  }

  /** A phase chosen in the select loads that phase; the newest phase shown after a load needs no new request. */
  private phaseChosen(phase: string | null): void {
    const loaded = this.result();
    if (phase && loaded && phase !== loaded.phaseFrom) {
      this.requestedPhase.set(phase);
    }
  }

  private buildPhaseOptions(result: SecurityPositionSeries | null): ValueKeyHtmlSelectOptions[] {
    return (result?.phases ?? []).map(
      (phase) =>
        new ValueKeyHtmlSelectOptions(
          phase.from,
          `${this.formatDate(phase.from)} – ` +
            (phase.to ? this.formatDate(phase.to) : this.translateService.instant('PHASE_OPEN'))
        )
    );
  }

  private buildCurrencyOptions(result: SecurityPositionSeries | null): ValueKeyHtmlSelectOptions[] {
    if (!result?.main) {
      return [];
    }
    return [
      new ValueKeyHtmlSelectOptions(
        CurrencyKind.MAIN,
        `${result.mainCurrency} (${this.translateService.instant('POSITION_MAIN_CURRENCY')})`
      ),
      new ValueKeyHtmlSelectOptions(
        CurrencyKind.SECURITY,
        `${result.securityCurrency} (${this.translateService.instant('POSITION_SECURITY_CURRENCY')})`
      )
    ];
  }

  private emptyKey(result: SecurityPositionSeries | null): string | null {
    if (!result) {
      return null;
    }
    return result.security == null || result.dates.length === 0 ? 'NO_HOLDING' : null;
  }

  /** Security · scope · phase · valued until, so the same security from two views reads as two scopes. */
  private buildHeadline(result: SecurityPositionSeries | null): string | null {
    if (!result) {
      return null;
    }
    const parts = [result.securityName, result.scopeName];
    if (result.phaseFrom) {
      parts.push(
        `${this.translateService.instant('HOLDING_PHASE')} ${this.formatDate(result.phaseFrom)} – ` +
          (result.phaseTo ? this.formatDate(result.phaseTo) : this.translateService.instant('PHASE_OPEN'))
      );
    }
    if (result.lastValuedDate) {
      parts.push(`${this.translateService.instant('VALUED_UNTIL')} ${this.formatDate(result.lastValuedDate)}`);
    }
    return parts.join(' · ');
  }

  /** The chosen currency's series: the main currency when chosen and present, otherwise the security currency. */
  private chosenSeries(
    result: SecurityPositionSeries,
    security: SecurityPositionCurrencySeries,
    currencyKind: string | null
  ): SecurityPositionCurrencySeries {
    return currencyKind !== CurrencyKind.SECURITY && result.main ? result.main : security;
  }

  private buildSummary(): string[] {
    const result = this.result();
    const chartType = this.form()?.value('chartType');
    if (!result || !result.security || chartType !== SecurityPositionChartType.POSITION_CHART_DRAWDOWN) {
      return [];
    }
    const currencyKind = (this.form()?.value('currencyKind') as string | null | undefined) ?? null;
    const s = this.chosenSeries(result, result.security, currencyKind);
    const t = (key: string) => this.translateService.instant(key);
    const e = s.maxDrawdown;
    const lines = [
      `${t('MAX_DRAWDOWN')}: ${this.formatPercent(e.depth)} · ${t('DRAWDOWN_PEAK_DATE')}: ${this.formatDate(e.peakDate)}` +
        ` · ${t('DRAWDOWN_TROUGH_DATE')}: ${this.formatDate(e.troughDate)}` +
        ` · ${t('DRAWDOWN_RECOVERY_DATE')}: ${this.formatDate(e.recoveryDate)}` +
        ` · ${t('DRAWDOWN_EPISODE_DAYS')}: ${this.format(e.durationDays, this.daysField)}`
    ];
    if (s.longestUnderwater) {
      const u = s.longestUnderwater;
      lines.push(
        `${t('MAX_DRAWDOWN_DURATION_DAYS')}: ${this.format(u.durationDays, this.daysField)}` +
          ` (${this.formatDate(u.from)} – ${this.formatDate(u.to)})`
      );
    }
    const currentKey = result.phaseTo ? 'DRAWDOWN_AT_SALE' : 'CURRENT_DRAWDOWN';
    lines.push(`${t(currentKey)}: ${this.formatPercent(s.currentDrawdown)}`);
    return lines;
  }

  /** Remarks of the response plus the explanation of the chosen chart; both are text keys. */
  private buildNotes(): string[] {
    const result = this.result();
    if (!result || this.emptyTextKey()) {
      return [];
    }
    const notes: string[] = result.remarks.map((remark) =>
      remark === 'SPIN_OFF' ? 'POSITION_SERIES_SPIN_OFF' : remark
    );
    const chartType = this.form()?.value('chartType') as string | null | undefined;
    if (chartType != null && CHART_TYPES_WITH_BASIS.includes(chartType)) {
      const basis =
        (this.form()?.value('performanceBasis') as string | null | undefined) ?? PerformanceBasis.OPEN_POSITION;
      notes.push(basis + (chartType === SecurityPositionChartType.POSITION_CHART_RESULT ? '_RESULT_NOTE' : '_NOTE'));
      if (result.marginInstrument) {
        notes.push('POSITION_MARGIN_BASIS_NOTE');
      }
    } else if (chartType === SecurityPositionChartType.POSITION_CHART_DRAWDOWN) {
      notes.push('POSITION_CHART_DRAWDOWN_TOOLTIP');
      if (result.marginInstrument) {
        notes.push('POSITION_MARGIN_DRAWDOWN_NOTE');
      }
    }
    if (result.main && this.form()?.value('currencyKind') !== CurrencyKind.SECURITY) {
      notes.push('POSITION_MAIN_CURRENCY_NOTE');
    }
    return notes;
  }

  private redraw(
    chartType: string | null,
    basis: string | null,
    currencyKind: string | null,
    result: SecurityPositionSeries | null
  ): void {
    if (result?.marginInstrument && chartType === SecurityPositionChartType.POSITION_CHART_VALUE) {
      // Not offered for a margin instrument; the change of the select draws the result chart instead.
      this.form()?.setValue('chartType', SecurityPositionChartType.POSITION_CHART_RESULT);
      return;
    }
    if (chartType) {
      this.usersettingsService.saveSingleValue(this.getStoreKey(), chartType);
    }
    if (result?.phaseFrom && this.form()?.value('holdingPhase') !== result.phaseFrom) {
      this.form()?.setValue('holdingPhase', result.phaseFrom);
    }
    const version = ++this.drawVersion;
    if (this.plotly) {
      this.plotly.purge(this.chartElement().nativeElement);
    }
    if (!chartType || !result || !result.security || this.emptyKey(result)) {
      return;
    }
    const figure = this.figure(chartType, basis, this.chosenSeries(result, result.security, currencyKind), result);
    // Wait until the chart element is no longer hidden, otherwise Plotly measures a zero size.
    afterNextRender(
      () =>
        this.plotlyLoader.load().then(
          (plotly) => {
            if (!this.destroyed && version === this.drawVersion) {
              this.plotly = plotly;
              this.plot(plotly, figure, AREA_CHART_TYPES.includes(chartType));
            }
          },
          () => {}
        ),
      { injector: this.renderInjector }
    );
  }

  /**
   * Draws the figure and lets the value axis follow the visible date window: Plotly keeps the axis of the whole phase
   * when the range slider or a zoom narrows the dates, which would leave a narrowed part flat at the top.
   */
  private plot(plotly: PlotlyInstance, figure: PositionFigure, includeZero: boolean): void {
    const element = this.chartElement().nativeElement;
    // The trace and layout builders describe only the attributes they set, so they are passed untyped
    plotly.newPlot(element, figure.data as Data[], figure.layout as Partial<Layout>, this.plotlyConfig).then((gd) =>
      gd.on('plotly_relayout', (event) =>
        // The event carries keys such as 'xaxis.range[0]', which its typing does not describe
        this.followVisibleWindow(plotly, gd, figure.lines, event as unknown as Record<string, unknown>, includeZero)
      )
    );
  }

  private followVisibleWindow(
    plotly: PlotlyInstance,
    gd: HTMLElement,
    lines: PositionLineTrace[],
    event: Record<string, unknown>,
    includeZero: boolean
  ): void {
    const window = Array.isArray(event['xaxis.range'])
      ? (event['xaxis.range'] as string[])
      : event['xaxis.range[0]'] != null
        ? [String(event['xaxis.range[0]']), String(event['xaxis.range[1]'])]
        : null;
    if (event['xaxis.autorange']) {
      plotly.relayout(gd, { 'yaxis.autorange': true });
    } else if (window) {
      const visible = lines.filter((trace) => trace.visible !== 'legendonly');
      const range = visibleValueRange(visible, String(window[0]), String(window[1]), includeZero);
      if (range) {
        plotly.relayout(gd, { 'yaxis.range': range });
      }
    }
  }

  private figure(
    chartType: string,
    basis: string | null,
    s: SecurityPositionCurrencySeries,
    result: SecurityPositionSeries
  ): PositionFigure {
    const texts = this.traceTexts();
    const total = basis === PerformanceBasis.TOTAL_SINCE_ENTRY;
    const basisName = this.translateService.instant(total ? 'TOTAL_SINCE_ENTRY' : 'OPEN_POSITION');
    const hover = this.hoverTexts(result, s, total);
    let lines: PositionLineTrace[];
    let yTitle = s.currency;
    let edgeLabels: PositionEdgeLabel[] = [];
    if (chartType === SecurityPositionChartType.POSITION_CHART_RESULT) {
      lines = positionAreaTraces(result.dates, total ? s.totalResult : s.openResult, basisName, null, hover);
    } else if (chartType === SecurityPositionChartType.POSITION_CHART_PERFORMANCE) {
      const comparison = total ? null : { values: s.priceChange, name: this.translateService.instant('PRICE_CHANGE') };
      lines = positionAreaTraces(
        result.dates,
        total ? s.totalPerformance : s.openPerformance,
        basisName,
        comparison,
        hover
      );
      yTitle = this.translateService.instant('SINCE_PHASE_START');
    } else if (chartType === SecurityPositionChartType.POSITION_CHART_DRAWDOWN) {
      lines = positionDrawdownTraces(result.dates, s, texts, hover);
      yTitle = this.translateService.instant('POSITION_CHART_DRAWDOWN');
    } else {
      lines = positionValueTraces(result.dates, s, texts, hover);
      edgeLabels = this.valueEdgeLabels(s);
    }
    const events = positionEventTraces(result.events, this.eventLabels(result));
    const percent =
      chartType === SecurityPositionChartType.POSITION_CHART_PERFORMANCE ||
      chartType === SecurityPositionChartType.POSITION_CHART_DRAWDOWN;
    return {
      data: [...lines, ...events],
      lines,
      layout: positionChartLayout(yTitle, percent, edgeLabels, this.surface())
    };
  }

  /** Labels of the last value and the last invested capital at the right edge, as a price board shows them. */
  private valueEdgeLabels(s: SecurityPositionCurrencySeries): PositionEdgeLabel[] {
    const labels: PositionEdgeLabel[] = [];
    const lastValue = lastPresent(s.value);
    const lastInvested = lastPresent(s.invested);
    if (lastValue != null) {
      labels.push({ y: lastValue, text: this.format(lastValue, this.amountField), color: VALUE_LINE });
    }
    if (lastInvested != null && lastInvested > 0) {
      labels.push({ y: lastInvested, text: this.format(lastInvested, this.amountField), color: INVESTED_LINE });
    }
    return labels;
  }

  private traceTexts(): PositionChartTexts {
    const t = (key: string) => this.translateService.instant(key);
    return {
      value: t('POSITION_VALUE'),
      invested: t('INVESTED_CAPITAL'),
      valueWithIncome: t('POSITION_VALUE_WITH_INCOME'),
      purchases: t('POSITION_PURCHASES'),
      drawdown: t('POSITION_CHART_DRAWDOWN'),
      priceDrawdown: t('PRICE_DRAWDOWN')
    };
  }

  /** The figures of one date for the hover, in the chosen currency and formatted by GT's data types. */
  private hoverTexts(result: SecurityPositionSeries, s: SecurityPositionCurrencySeries, total: boolean): string[] {
    const t = (key: string) => this.translateService.instant(key);
    const percentValues = total ? s.totalPerformance : s.openPerformance;
    const percentName = t(total ? 'TOTAL_SINCE_ENTRY' : 'OPEN_POSITION');
    return result.dates.map((date, i) => {
      const lines = [
        `<b>${this.formatDate(date)}</b>`,
        `${t('POSITION_VALUE')}: ${this.formatAmount(s.value[i], s.currency)}`,
        `${t(result.marginInstrument ? 'ENTRY_VALUE' : 'INVESTED_CAPITAL')}: ${this.formatAmount(s.invested[i], s.currency)}`,
        `${t('OPEN_RESULT')}: ${this.formatAmount(s.openResult[i], s.currency)}`,
        `${t('REALIZED_RESULT')}: ${this.formatAmount(s.realized[i], s.currency)}`,
        `${t('POSITION_INCOME')}: ${this.formatAmount(s.income[i], s.currency)}`,
        `${t('TOTAL_RESULT')}: ${this.formatAmount(s.totalResult[i], s.currency)}`,
        `${percentName}: ${this.formatPercent(percentValues[i])}`,
        `${t('POSITION_CHART_DRAWDOWN')}: ${this.formatPercent(s.drawdown[i])}`
      ];
      return lines.join('<br>');
    });
  }

  private eventLabels(result: SecurityPositionSeries) {
    const names = {} as Record<PositionEventKind, string>;
    const letters = {} as Record<PositionEventKind, string>;
    for (const kind of EVENT_KINDS) {
      names[kind] = this.translateService.instant('POSITION_EVENT_' + kind);
      letters[kind] = this.translateService.instant('POSITION_EVENT_LETTER_' + kind);
    }
    return {
      names,
      letters,
      describe: (event: SecurityPositionEvent) =>
        (event.units == null ? '' : this.format(event.units, this.unitsField) + ' · ') +
        this.formatAmount(event.amountSC, result.securityCurrency)
    };
  }

  /** The text and border colors of the active theme, read from its CSS variables. */
  private surface() {
    const style = getComputedStyle(this.elementRef.nativeElement);
    return {
      textColor: style.getPropertyValue('--p-text-color').trim() || '#52514e',
      gridColor: style.getPropertyValue('--p-content-border-color').trim() || '#e1e0d9',
      separators: this.gps.getDecimalSymbol() + this.gps.getThousandsSeparatorSymbol()
    };
  }

  private formatAmount(value: number | null, currency: string): string {
    return value == null ? '–' : `${this.format(value, this.amountField)} ${currency}`;
  }

  private formatPercent(value: number | null): string {
    return value == null ? '–' : `${this.format(value, this.percentField)} %`;
  }

  private formatDate(value: string | null): string {
    return value == null ? '–' : this.format(value, this.dateField);
  }

  private format(value: number | string | null, field: ColumnConfig): string {
    if (value == null) {
      return '–';
    }
    return AppHelper.getValueByPathWithField(this.gps, this.translateService, { value }, field, 'value') ?? '';
  }

  private resizeChart(): void {
    const element = this.chartElement().nativeElement as HTMLElement & { data?: unknown };
    if (this.plotted() && element.data) {
      this.plotly?.Plots.resize(element);
    }
  }
}
