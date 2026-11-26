import { requireFormControl } from '../../lib/dynamic-form/models/form-control-access';
import type { PanelClickEvent } from '../../lib/mainmenubar/component/iglobal.menu.attach';
import {
  afterNextRender,
  computed,
  effect,
  input,
  Injector,
  signal,
  untracked,
  Component,
  DestroyRef,
  OnDestroy,
  OnInit,
  inject,
  viewChild
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ShowRecordConfigBase } from '../../lib/datashowbase/show.record.config.base';
import { ValueKeyHtmlSelectOptions } from '../../lib/dynamic-form/models/value.key.html.select.options';
import { DynamicFieldHelper } from '../../lib/helper/dynamic.field.helper';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { FormBase } from '../../lib/edit/form.base';
import { TranslateHelper } from '../../lib/helper/translate.helper';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { FirstAndMissingTradingDays, HoldingService, PerformanceWindowDef, WeekYear } from '../service/holding.service';
import { performanceWindowFromDraft } from '../model/performance-window';
import {
  firstSelectableDateTo,
  initialPerformanceRange,
  performanceRangeBounds,
  performanceRangeCalendars,
  syncStoredDateTo
} from '../model/performance-range';
import { AppHelper } from '../../lib/helper/app.helper';
import { GlobalparameterService } from '../../lib/services/globalparameter.service';
import { BusinessHelper } from '../../shared/helper/business.helper';
import { HelpIds } from '../../lib/help/help.ids';
import { IGlobalMenuAttach } from '../../lib/mainmenubar/component/iglobal.menu.attach';
import { ActivePanelService } from '../../lib/mainmenubar/service/active.panel.service';
import { MenuItem } from '@openng/optimus-ui/api';
import { DialogService } from '@openng/optimus-ui/dynamicdialog';
import { Subscription } from 'rxjs';
import moment from 'moment';
import { SelectOptionsHelper } from '../../lib/helper/select.options.helper';
import { DynamicFormComponent } from '../../lib/dynamic-form/containers/dynamic-form/dynamic-form.component';
import { DYNAMIC_FORM_IMPORTS } from '../../lib/dynamic-form/dynamic-form.imports';
import { PerformancePeriod, PerformanceBenchmarkView } from '../model/performance.period';
import { AppSettings } from '../../shared/app.settings';
import { Router } from '@angular/router';
import { FormHelper } from '../../lib/dynamic-form/components/FormHelper';
import { GlobalGTSessionNames } from '../../shared/global.gt.session.names';
import { BaseSettings } from '../../lib/base.settings';
import { InfoLevelType } from '../../lib/message/info.leve.type';
import { MessageToastService } from '../../lib/message/message.toast.service';
import { SecuritycurrencySearchAndSetDialogComponent } from '../../securitycurrency/component/securitycurrency-search-and-set-dialog.component';
import { SupplementCriteria } from '../../securitycurrency/model/supplement.criteria';

import { ProgressBarModule } from '@openng/optimus-ui/progressbar';
import { TenantPerformanceFromToDiffComponent } from './performance-period-from-to-diff.component';
import { TenantPerformanceTreetableComponent } from './performance-period-treetable.component';
import { PerformancePeriodMetricsComponent } from './performance-period-metrics.component';
import { PerformancePeriodBenchmarkComponent } from './performance-period-benchmark.component';
import { PerformanceReportDialogService } from '../service/performance-report-dialog.service';
import { PerformanceBenchmarkService } from '../service/performance-benchmark.service';
import { PerformancePeriodChartService } from '../service/performance-period-chart.service';

/** Source NLS key of a benchmark stored with the portfolio itself. */
const SOURCE_PORTFOLIO = 'BENCHMARK_SOURCE_PORTFOLIO';
/** Source NLS key of the benchmark stored with the tenant. */
const SOURCE_TENANT = 'BENCHMARK_SOURCE_TENANT';

/**
 * Performance over a certain period for a tenant or portfolio, optionally compared with a benchmark.
 *
 * The benchmark field shows the stored benchmark of the scope as the server resolves it. Choosing another instrument
 * or hiding the benchmark applies to the next calculation only. The button behind the field saves a chosen instrument
 * as default of the scope; it is one of the few actions offered outside the menu, because it belongs to the field.
 */
@Component({
  template: `
    <div
      class="data-container"
      (click)="onComponentClick($event)"
      [class]="{
        'active-border': isActivated(),
        'passiv-border': !isActivated()
      }">
      <p>
        {{ 'PERFORMANCE_REMARK' | translate }}
        {{ firstTradingDayText() }}
      </p>
      <dynamic-form
        [config]="config"
        [formConfig]="formConfig"
        [translateService]="translateService"
        #form="dynamicForm"
        (submitBt)="submit($event)" />

      @if (loading() || reportDialogService.opening()) {
        <div class="progress-bar-box">
          <h4>{{ 'LOADING' | translate }}</h4>
          <p-progressBar mode="indeterminate" [style]="{ height: '6px' }" />
        </div>
      }

      <performance-period-from-to-diff [periodHoldingsAndDiff]="periodHoldingsAndDiff()" />

      <performance-period-metrics [metrics]="performancePeriod()?.metrics ?? null" />

      <performance-period-benchmark [benchmark]="performancePeriod()?.benchmark ?? null" />

      <performance-period-treetable [performancePeriod]="performancePeriod()" />
    </div>
  `,
  providers: [DialogService],
  imports: [
    TranslateModule,
    ...DYNAMIC_FORM_IMPORTS,
    ProgressBarModule,
    TenantPerformanceFromToDiffComponent,
    PerformancePeriodMetricsComponent,
    PerformancePeriodBenchmarkComponent,
    TenantPerformanceTreetableComponent
  ]
})
export class PerformancePeriodComponent extends FormBase implements OnInit, OnDestroy, IGlobalMenuAttach {
  private router: Router = inject(Router);
  private activePanelService: ActivePanelService = inject(ActivePanelService);
  private holdingService: HoldingService = inject(HoldingService);
  private gps: GlobalparameterService = inject(GlobalparameterService);
  private chartService = inject(PerformancePeriodChartService);
  private benchmarkService = inject(PerformanceBenchmarkService);
  private messageToastService = inject(MessageToastService);
  private dialogService = inject(DialogService);
  public translateService: TranslateService = inject(TranslateService);

  /** Portfolio of the route; absent for the tab of the client. */
  readonly id = input<number | null, unknown>(null, {
    transform: (value: unknown) => (value == null || value === '' ? null : Number(value))
  });
  readonly idPortfolio = computed(() => this.id());

  // Access the form
  readonly form = viewChild.required(DynamicFormComponent);
  readonly performancePeriod = signal<PerformancePeriod | null>(null);
  readonly periodHoldingsAndDiff = computed(() => {
    const period = this.performancePeriod();
    return period ? [period.firstDayTotals, period.lastDayTotals, period.difference] : [];
  });

  dateFromSubscribe?: Subscription;
  dateToSubscribe?: Subscription;
  readonly firstAndMissingTradingDays = signal<FirstAndMissingTradingDays | null>(null);
  private readonly firstTradingDayField = ShowRecordConfigBase.createColumnConfig(
    DataType.DateNumeric,
    'firstEverTradingDay',
    ''
  );
  readonly firstTradingDayText = computed(
    () =>
      AppHelper.getValueByPathWithField(
        this.gps,
        this.translateService,
        this.firstAndMissingTradingDays(),
        this.firstTradingDayField,
        'firstEverTradingDay'
      ) ?? ''
  );
  private readonly renderInjector = inject(Injector);
  private calculationRequest?: Subscription;
  private calendarRequest?: Subscription;
  private benchmarkRequest?: Subscription;
  private menuRequest?: Subscription;
  readonly loading = signal(false);
  readonly reportDialogService = inject(PerformanceReportDialogService);
  readonly menuItems = signal<MenuItem[]>([]);
  private reportWindow?: PerformanceWindowDef;
  /** Benchmark of the displayed calculation as the PDF dialog names it. */
  private reportBenchmarkLabel?: string;
  /** Stored benchmark of the scope as the server resolves it, null when none is stored or inherited. */
  private storedBenchmark: PerformanceBenchmarkView | null = null;
  /** Benchmark chosen for the next calculation: undefined uses the stored one, 0 compares none. */
  private requestBenchmark?: number;
  private requestBenchmarkName: string | null = null;
  private formReady = false;
  private destroyRef = inject(DestroyRef);

  constructor() {
    super();
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps, 4, null, false);
    this.formConfig.labelColumns = 2;
    this.publishMenu();
    effect(() => {
      const idPortfolio = this.idPortfolio();
      untracked(() => this.scopeChanged(idPortfolio));
    });
  }

  ngOnInit(): void {
    this.createInputFormDefinition();
  }

  /**
   * A new portfolio of the route, or the client view: the calculation of the previous scope and its request-local
   * benchmark are discarded, and the dates and the stored benchmark of the new scope are loaded once the form exists.
   */
  private scopeChanged(idPortfolio: number | null): void {
    this.calculationRequest?.unsubscribe();
    this.performancePeriod.set(null);
    this.reportWindow = undefined;
    this.requestBenchmark = undefined;
    this.requestBenchmarkName = null;
    this.storedBenchmark = null;
    this.chartService.clearOtherScope(idPortfolio);
    this.publishMenu();
    this.loading.set(true);
    afterNextRender(
      () => {
        this.formReady = true;
        this.initializeDateRange(idPortfolio);
        this.loadStoredBenchmark(idPortfolio);
      },
      { injector: this.renderInjector }
    );
  }

  private initializeDateRange(idPortfolio: number | null): void {
    this.calendarRequest?.unsubscribe();
    this.calendarRequest = this.holdingService
      .getFirstAndMissingTradingDays(idPortfolio)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (famtd: FirstAndMissingTradingDays) => {
          this.loading.set(false);
          // The range is shared with the period performance per security through the session storage
          const range = initialPerformanceRange(
            famtd,
            sessionStorage.getItem(GlobalGTSessionNames.PERFORMANCE_DATE_FROM),
            sessionStorage.getItem(GlobalGTSessionNames.PERFORMANCE_DATE_TO)
          );
          const bounds = performanceRangeBounds(famtd);
          if (famtd.secondEverTradingDay && range && bounds) {
            FormHelper.disableEnableFieldConfigs(false, this.config);
            this.firstAndMissingTradingDays.set(famtd);
            requireFormControl(this.configObject.dateFrom).setValue(range.dateFrom);
            requireFormControl(this.configObject.dateTo).setValue(range.dateTo);
            const calendars = performanceRangeCalendars(bounds, range.dateFrom);
            this.configObject.dateFrom.calendarConfig = calendars.from;
            this.configObject.dateTo.calendarConfig = calendars.to;
            syncStoredDateTo(sessionStorage, GlobalGTSessionNames.PERFORMANCE_DATE_TO, range.dateTo, famtd);
            this.valueChangedOnDateTo();
            this.valueChangedOnDateFrom();
            this.setMonthWeekPeriod();
          } else {
            // A performance interval needs both selectable date boundaries.
            FormHelper.disableEnableFieldConfigs(true, this.config);
          }
        },
        error: () => this.loading.set(false)
      });
  }

  /** Reads the stored benchmark of the scope; a superseded read of another scope is cancelled. */
  private loadStoredBenchmark(idPortfolio: number | null): void {
    this.benchmarkRequest?.unsubscribe();
    this.benchmarkRequest = this.benchmarkService
      .getBenchmark(idPortfolio)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((stored) => {
        this.storedBenchmark = stored;
        this.showBenchmarkInField();
        this.publishMenu();
      });
  }

  valueChangedOnDateFrom(): void {
    this.dateFromSubscribe?.unsubscribe();
    this.dateFromSubscribe = requireFormControl(this.configObject.dateFrom)
      .valueChanges.pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((dateFrom: Date) => {
        this.setMinDateForToDate(dateFrom);
        BusinessHelper.saveDateToSessionStore(GlobalGTSessionNames.PERFORMANCE_DATE_FROM, dateFrom);
        this.setMonthWeekPeriod();
      });
  }

  valueChangedOnDateTo(): void {
    this.dateToSubscribe?.unsubscribe();
    this.dateToSubscribe = requireFormControl(this.configObject.dateTo)
      .valueChanges.pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((dateTo: Date | null) => {
        const days = this.firstAndMissingTradingDays();
        if (days) {
          syncStoredDateTo(sessionStorage, GlobalGTSessionNames.PERFORMANCE_DATE_TO, dateTo, days);
        }
        this.setMonthWeekPeriod();
      });
  }

  helpLink(): void {
    // Used in a dialog
    this.gps.toExternalHelpWebpage(this.gps.getUserLang(), HelpIds.HELP_PORTFOLIOS_PERIODPERFORMANCE);
  }

  isActivated(): boolean {
    return this.activePanelService.isActivated(this);
  }

  onComponentClick(event: PanelClickEvent | null): void {
    this.activePanelService.activatePanel(this, { showMenu: this.menuItems() });
  }

  hideContextMenu(): void {}

  callMeDeactivate(): void {}

  getHelpContextId(): string {
    return HelpIds.HELP_PORTFOLIOS_PERIODPERFORMANCE;
  }

  submit(value: { [name: string]: any }): void {
    const idPortfolio = this.idPortfolio();
    const draft: Partial<PerformanceWindowDef> = { idPortfolio };
    this.form().cleanMaskAndTransferValuesToBusinessObject(draft, true);
    const pWD = performanceWindowFromDraft({ ...draft, idBenchmark: this.requestBenchmark });
    if (!pWD) {
      this.configObject.submit.disabled = false;
      return;
    }
    this.calculationRequest?.unsubscribe();
    this.loading.set(true);
    this.calculationRequest = this.holdingService
      .getPeriodPerformance(pWD)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (periodPerformance) => {
          this.performancePeriod.set(periodPerformance);
          this.chartService.publish(idPortfolio, periodPerformance);
          this.configObject.submit.disabled = false;
          this.reportWindow = { ...pWD };
          this.reportBenchmarkLabel = this.benchmarkLabel(periodPerformance);
          this.publishMenu();
          this.loading.set(false);
        },
        error: () => {
          this.configObject.submit.disabled = false;
          this.loading.set(false);
        }
      });
  }

  ngOnDestroy(): void {
    this.dateFromSubscribe?.unsubscribe();
    this.dateToSubscribe?.unsubscribe();
    this.activePanelService.destroyPanel(this);
  }

  /** Publish a translated copy to both this view and the active global menu. */
  private publishMenu(): void {
    this.menuRequest?.unsubscribe();
    this.menuRequest = TranslateHelper.translateMenuItems$(
      [
        { label: 'SHOW_CHART', disabled: !this.performancePeriod(), command: () => this.navigateToChartRoute() },
        {
          label: 'PDF_REPORT' + BaseSettings.DIALOG_MENU_SUFFIX,
          disabled: !this.reportWindow,
          command: () => this.openPdfReport()
        },
        { separator: true },
        ...this.benchmarkMenuItems()
      ],
      this.translateService
    )
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((items) => {
        this.menuItems.set(items);
        if (this.isActivated()) this.onComponentClick(null);
      });
  }

  /**
   * The benchmark actions. Hiding applies to the next calculation only and is open to a read-only user as well; the
   * actions that change a stored default are offered only to a user who may write.
   */
  private benchmarkMenuItems(): MenuItem[] {
    const items: MenuItem[] = [
      {
        label: 'HIDE_BENCHMARK_FOR_REPORT',
        disabled: this.requestBenchmark === 0 || (!this.storedBenchmark && this.requestBenchmark === undefined),
        command: () => this.hideBenchmarkForReport()
      }
    ];
    if (this.gps.isReadOnlyUser()) {
      return items;
    }
    if (this.idPortfolio() != null) {
      items.push({
        label: 'USE_TENANT_BENCHMARK',
        disabled: this.storedBenchmark?.source !== SOURCE_PORTFOLIO,
        command: () => this.clearDefaultBenchmark()
      });
    } else {
      items.push({
        label: 'REMOVE_TENANT_BENCHMARK',
        disabled: this.storedBenchmark?.source !== SOURCE_TENANT,
        command: () => this.clearDefaultBenchmark()
      });
    }
    return items;
  }

  /** The dialog uses the dates of the displayed calculation, not the possibly edited form values. */
  private openPdfReport(): void {
    if (this.reportWindow) {
      this.reportDialogService.open(this.reportWindow, this.destroyRef, this.reportBenchmarkLabel);
    }
  }

  /** The benchmark the calculation was compared with and its source, or the note that none was compared. */
  private benchmarkLabel(period: PerformancePeriod): string {
    const benchmark = period.benchmark;
    return benchmark
      ? `${benchmark.name} (${this.translateService.instant(benchmark.source)})`
      : this.translateService.instant('NO_BENCHMARK');
  }

  private createInputFormDefinition(): void {
    this.config = [
      DynamicFieldHelper.createFieldPcalendarHeqF(DataType.DateString, 'dateFrom', true, { usedLayoutColumns: 4 }),
      DynamicFieldHelper.createFieldPcalendarHeqF(DataType.DateString, 'dateTo', true, { usedLayoutColumns: 4 }),
      DynamicFieldHelper.createFieldSelectStringHeqF('periodSplit', true, {
        usedLayoutColumns: 4
      }),
      DynamicFieldHelper.createFieldInputButtonHeqF(
        DataType.String,
        'benchmarkName',
        () => this.searchBenchmark(),
        false,
        { usedLayoutColumns: 6 }
      ),
      // Saving belongs to the field, so it stands behind it rather than in the menu; read-only users cannot store
      DynamicFieldHelper.createFunctionButtonFieldName(
        'saveBenchmark',
        'SAVE_AS_DEFAULT_BENCHMARK',
        () => this.saveAsDefaultBenchmark(),
        { buttonInForm: true, usedLayoutColumns: 2, disabled: true, invisible: this.gps.isReadOnlyUser() }
      ),
      DynamicFieldHelper.createSubmitButton('APPLY')
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
    this.translateService
      .get(['WM_WEEK', 'WM_YEAR'])
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((labels) => {
        this.configObject.periodSplit.valueKeyHtmlOptions = [
          new ValueKeyHtmlSelectOptions('', ''),
          ...['WM_WEEK', 'WM_YEAR'].map((key) => new ValueKeyHtmlSelectOptions(key, labels[key]))
        ];
      });
  }

  /** Lets the user choose any security or currency pair as benchmark of the next calculation. */
  private searchBenchmark(): void {
    const ref = this.dialogService.open(SecuritycurrencySearchAndSetDialogComponent, {
      header: this.translateService.instant('BENCHMARK_NAME'),
      width: '720px',
      modal: true,
      closable: true,
      closeOnEscape: true,
      focusOnShow: false,
      data: { supplementCriteria: new SupplementCriteria(false, false) }
    });
    if (!ref) return;
    ref.onClose.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((instrument) => {
      if (instrument) {
        this.requestBenchmark = instrument.idSecuritycurrency;
        this.requestBenchmarkName = instrument.name ?? instrument.fromCurrency + '/' + instrument.toCurrency;
        this.showBenchmarkInField();
        this.publishMenu();
      }
    });
  }

  private hideBenchmarkForReport(): void {
    this.requestBenchmark = 0;
    this.requestBenchmarkName = null;
    this.showBenchmarkInField();
    this.publishMenu();
  }

  /** Stores the chosen instrument as default of the scope, then shows what the server resolves from then on. */
  private saveAsDefaultBenchmark(): void {
    const idBenchmark = this.requestBenchmark;
    const idPortfolio = this.idPortfolio();
    if (!idBenchmark) return;
    this.benchmarkService
      .assignBenchmark(idPortfolio, idBenchmark)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((stored) => {
        this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'BENCHMARK_DEFAULT_SAVED');
        this.storedBenchmarkWritten(idPortfolio, stored);
      });
  }

  /** Removes the default of exactly this scope; a portfolio then inherits the benchmark of the tenant again. */
  private clearDefaultBenchmark(): void {
    const idPortfolio = this.idPortfolio();
    this.benchmarkService
      .clearBenchmark(idPortfolio)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'BENCHMARK_DEFAULT_REMOVED');
        this.requestBenchmark = undefined;
        this.requestBenchmarkName = null;
        this.loadStoredBenchmark(idPortfolio);
      });
  }

  private storedBenchmarkWritten(idPortfolio: number | null, stored: PerformanceBenchmarkView): void {
    if (idPortfolio !== this.idPortfolio()) return;
    this.storedBenchmark = stored;
    this.requestBenchmark = undefined;
    this.requestBenchmarkName = null;
    this.showBenchmarkInField();
    this.publishMenu();
  }

  /** The field shows the request-local choice when there is one, otherwise the stored benchmark of the scope. */
  private showBenchmarkInField(): void {
    if (!this.formReady || !this.configObject.benchmarkName) return;
    let text: string | null;
    if (this.requestBenchmark === 0) {
      text = this.translateService.instant('NO_BENCHMARK');
    } else if (this.requestBenchmark !== undefined) {
      text = this.requestBenchmarkName;
    } else {
      text = this.storedBenchmark
        ? `${this.storedBenchmark.name} (${this.translateService.instant(this.storedBenchmark.source)})`
        : null;
    }
    requireFormControl(this.configObject.benchmarkName).setValue(text);
    // Only an instrument chosen for this report and not yet stored can be saved
    if (this.configObject.saveBenchmark) {
      this.configObject.saveBenchmark.disabled = !this.requestBenchmark;
    }
  }

  private setMinDateForToDate(dateFrom: Date): void {
    const days = this.firstAndMissingTradingDays();
    const bounds = days ? performanceRangeBounds(days) : null;
    const calendar = this.configObject.dateTo.calendarConfig;
    if (!bounds || !calendar || !dateFrom) return;
    calendar.minDate = firstSelectableDateTo(dateFrom, bounds);
    const currentDateTo: Date = requireFormControl(this.configObject.dateTo).value;
    if (currentDateTo && currentDateTo.getTime() < calendar.minDate.getTime()) {
      requireFormControl(this.configObject.dateTo).setValue(null);
    }
  }

  private setMonthWeekPeriod(): void {
    const days = this.firstAndMissingTradingDays();
    const options = this.configObject.periodSplit.valueKeyHtmlOptions;
    if (!days || !options) return;
    const pWD: Partial<PerformanceWindowDef> = { idPortfolio: this.idPortfolio() };
    this.form().cleanMaskAndTransferValuesToBusinessObject(pWD, true);
    const disabled: any[] = [];
    if (pWD.dateFrom && pWD.dateTo) {
      if (moment(pWD.dateTo).diff(pWD.dateFrom, 'months', true) < days.minIncludeMonthLimit) {
        disabled.push(WeekYear.WM_YEAR);
      }
      if (moment(pWD.dateTo).diff(pWD.dateFrom, 'weeks', true) > days.maxWeekLimit) {
        disabled.push(WeekYear.WM_WEEK);
      }
      this.configObject.periodSplit.valueKeyHtmlOptions = SelectOptionsHelper.disableEnableExistingHtmlOptionsFromEnum(
        options.map((option) => ({ ...option })),
        WeekYear,
        disabled
      );
      if (disabled.indexOf(WeekYear[pWD.periodSplit as keyof typeof WeekYear]) >= 0) {
        requireFormControl(this.configObject.periodSplit).setValue('');
      }
    }
  }

  /** Opens the chart of this view in the lower display area; it follows every calculation of the view. */
  private navigateToChartRoute(): void {
    this.router.navigate([
      BaseSettings.MAINVIEW_KEY + '/',
      { outlets: { [AppSettings.MAIN_BOTTOM]: [AppSettings.PERFORMANCE_PERIOD_CHART] } }
    ]);
  }
}
