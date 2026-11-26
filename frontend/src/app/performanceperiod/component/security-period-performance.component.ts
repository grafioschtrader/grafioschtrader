import {
  Component,
  DestroyRef,
  Injector,
  OnDestroy,
  afterNextRender,
  computed,
  effect,
  inject,
  input,
  signal,
  untracked,
  viewChild
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { metadata, SchemaPathTree } from '@angular/forms/signals';
import { Router } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { MenuItem } from '@openng/optimus-ui/api';
import { DialogService } from '@openng/optimus-ui/dynamicdialog';
import { ProgressBarModule } from '@openng/optimus-ui/progressbar';
import { Subscription, finalize } from 'rxjs';
import moment from 'moment';

import { ShowRecordConfigBase } from '../../lib/datashowbase/show.record.config.base';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { FormConfig } from '../../lib/dynamic-form/models/form.config';
import type { FieldFormGroup } from '../../lib/dynamic-form/models/form.group.definition';
import { SignalDynamicFormComponent } from '../../lib/dynamic-form/signal/signal-dynamic-form.component';
import { FIELD_DISPLAY } from '../../lib/dynamic-form/signal/signal-form.model';
import { AppHelper } from '../../lib/helper/app.helper';
import { DynamicFieldHelper } from '../../lib/helper/dynamic.field.helper';
import { TranslateHelper } from '../../lib/helper/translate.helper';
import { HelpIds } from '../../lib/help/help.ids';
import type { PanelClickEvent } from '../../lib/mainmenubar/component/iglobal.menu.attach';
import { IGlobalMenuAttach } from '../../lib/mainmenubar/component/iglobal.menu.attach';
import { ActivePanelService } from '../../lib/mainmenubar/service/active.panel.service';
import { GlobalparameterService } from '../../lib/services/globalparameter.service';
import { BaseSettings } from '../../lib/base.settings';
import { BusinessHelper } from '../../shared/helper/business.helper';
import { GlobalGTSessionNames } from '../../shared/global.gt.session.names';
import { AppSettings } from '../../shared/app.settings';
import { SecurityPeriodPerformanceChartService } from '../service/security-period-performance-chart.service';
import { FirstAndMissingTradingDays, HoldingService } from '../service/holding.service';
import {
  firstSelectableDateTo,
  initialPerformanceRange,
  performanceRangeBounds,
  performanceRangeCalendars,
  syncStoredDateTo
} from '../model/performance-range';
import type { SecurityPeriodPerformance } from '../model/security-period-performance';
import type { PerformanceBenchmarkView } from '../model/performance.period';
import { PerformanceBenchmarkService } from '../service/performance-benchmark.service';
import { InfoLevelType } from '../../lib/message/info.leve.type';
import { MessageToastService } from '../../lib/message/message.toast.service';
import { benchmarkNoticeTexts } from '../../dashboard/model/benchmark.notice.text';
import { SecuritycurrencySearchAndSetDialogComponent } from '../../securitycurrency/component/securitycurrency-search-and-set-dialog.component';
import { SupplementCriteria } from '../../securitycurrency/model/supplement.criteria';
import { SecurityPeriodPerformanceTableComponent } from './security-period-performance-table.component';

/** The two date fields of the form. */
interface SecurityPeriodFormModel {
  dateFrom: Date | null;
  dateTo: Date | null;
  benchmarkName: string | null;
}

/**
 * Period performance per security of the client or of one portfolio: the absolute gain and the time-weighted return
 * of every security held or traded in a period. The view shares its date range with the period performance through
 * the session storage; the calculation starts with the button. The form is this component, the table a child.
 *
 * The show menu opens the charts of the view in the lower display area. Every calculation is published to them and
 * kept beyond the life of the view, so the chart stays filled in another tab and the table shows the calculation again
 * on return. A click on a security in a chart expands its row.
 */
@Component({
  // Routed; the selector names the host element for the e2e spec
  selector: 'security-period-performance',
  template: `
    <div
      class="data-container"
      (click)="onComponentClick($event)"
      [class.active-border]="isActivated()"
      [class.passiv-border]="!isActivated()">
      <p>
        {{ 'PERFORMANCE_REMARK' | translate }}
        {{ firstTradingDayText() }}
      </p>
      <signal-dynamic-form
        [config]="config"
        [formConfig]="formConfig"
        [rules]="rules"
        #form="signalDynamicForm"
        (submitBt)="submit()" />
      @if (loading()) {
        <div class="progress-bar-box">
          <h4>{{ 'LOADING' | translate }}</h4>
          <p-progressBar mode="indeterminate" [style]="{ height: '6px' }" />
        </div>
      }
      @if (result()?.benchmark; as benchmark) {
        <p class="mb-1">
          {{ 'BENCHMARK_TWR_PERCENT' | translate }}: {{ benchmark.name }} ({{ benchmark.source | translate }})
        </p>
        @for (notice of noticeTexts(); track $index) {
          <p class="mb-1 fst-italic">{{ notice }}</p>
        }
      }
      @if (result(); as calculated) {
        <security-period-performance-table
          [result]="calculated"
          [idPortfolio]="idPortfolio()"
          [expandedRowKeys]="expandedRowKeys()" />
      }
    </div>
  `,
  providers: [DialogService],
  imports: [TranslateModule, ProgressBarModule, SignalDynamicFormComponent, SecurityPeriodPerformanceTableComponent]
})
export class SecurityPeriodPerformanceComponent implements IGlobalMenuAttach, OnDestroy {
  private readonly holdingService = inject(HoldingService);
  private readonly activePanelService = inject(ActivePanelService);
  private readonly gps = inject(GlobalparameterService);
  private readonly translateService = inject(TranslateService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly router = inject(Router);
  private readonly chartService = inject(SecurityPeriodPerformanceChartService);
  private readonly injector = inject(Injector);
  private readonly benchmarkService = inject(PerformanceBenchmarkService);
  private readonly dialogService = inject(DialogService);
  private readonly messageToastService = inject(MessageToastService);

  private readonly form = viewChild<SignalDynamicFormComponent<SecurityPeriodFormModel>>('form');
  private readonly table = viewChild(SecurityPeriodPerformanceTableComponent);

  /** Expanded rows of the table by idSecuritycurrency; replaced for a click in the chart and for a new result. */
  readonly expandedRowKeys = signal<{ [idSecuritycurrency: string]: boolean }>({});

  /** Portfolio of the route; absent for the tab of the client. */
  readonly id = input<number | null, unknown>(null, {
    transform: (value: unknown) => (value == null || value === '' ? null : Number(value))
  });
  readonly idPortfolio = computed(() => this.id());

  readonly formConfig: FormConfig = AppHelper.getDefaultFormConfig(this.gps, 4, null, false);
  readonly config: FieldFormGroup[] = [
    DynamicFieldHelper.createFieldPcalendarHeqF(DataType.DateString, 'dateFrom', true, { usedLayoutColumns: 4 }),
    DynamicFieldHelper.createFieldPcalendarHeqF(DataType.DateString, 'dateTo', true, { usedLayoutColumns: 4 }),
    DynamicFieldHelper.createFieldInputButtonHeqF(
      DataType.String,
      'benchmarkName',
      () => this.searchBenchmark(),
      false,
      // With the two dates taking 8 of 12 columns, 6 columns start a line of its own, as in the period performance
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

  /** Stored benchmark of the scope as the server resolves it, null when none is stored or inherited. */
  private readonly storedBenchmark = signal<PerformanceBenchmarkView | null>(null);
  /** Benchmark chosen for the next calculation only: undefined uses the stored one, 0 compares none. */
  private readonly requestBenchmark = signal<number | undefined>(undefined);
  private readonly requestBenchmarkName = signal<string | null>(null);

  /** The notices of the benchmark comparison, translated with their dates formatted for the user. */
  readonly noticeTexts = computed(() =>
    benchmarkNoticeTexts(
      this.result()?.benchmark?.notices,
      (key, parameters) => this.translateService.instant(key, parameters),
      (date) => AppHelper.getDateByFormat(this.gps, date)
    )
  );

  readonly days = signal<FirstAndMissingTradingDays | null>(null);
  readonly result = signal<SecurityPeriodPerformance | null>(null);
  readonly loading = signal(false);
  private readonly dateFrom = signal<Date | null>(null);

  private readonly calendars = computed(() => {
    const days = this.days();
    const bounds = days ? performanceRangeBounds(days) : null;
    return bounds ? performanceRangeCalendars(bounds, this.dateFrom()) : null;
  });

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
        this.days(),
        this.firstTradingDayField,
        'firstEverTradingDay'
      ) ?? ''
  );

  /** The calendars follow the trading days of the scope; the end date starts after the start date. */
  readonly rules = (path: SchemaPathTree<SecurityPeriodFormModel>): void => {
    metadata(path.dateFrom, FIELD_DISPLAY, () => ({ calendarConfig: this.calendars()?.from }));
    metadata(path.dateTo, FIELD_DISPLAY, () => ({ calendarConfig: this.calendars()?.to }));
  };

  private calendarRequest?: Subscription;
  private benchmarkRequest?: Subscription;
  private resultRequest?: Subscription;
  private menuRequest?: Subscription;

  constructor() {
    this.formConfig.labelColumns = 2;
    TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config, false);
    effect(() => {
      const idPortfolio = this.idPortfolio();
      untracked(() => this.loadScope(idPortfolio));
    });
    effect(() => {
      const form = this.form();
      const dateFrom = (form?.value('dateFrom') as Date | null | undefined) ?? null;
      const dateTo = (form?.value('dateTo') as Date | null | undefined) ?? null;
      untracked(() => this.datesChanged(dateFrom, dateTo));
    });
    effect(() => {
      const form = this.form();
      const text = this.benchmarkText();
      // Only an instrument chosen for this report and not yet stored can be saved
      const savable = !!this.requestBenchmark();
      // The form builds its fields after the first render; this effect runs again once the field exists
      const ready = !!form?.hasField('benchmarkName');
      untracked(() => {
        if (ready && form) {
          form.setValue('benchmarkName', text);
        }
        form?.setButtonState('saveBenchmark', { disabled: !savable });
      });
    });
    effect(() => {
      const result = this.result();
      untracked(() => {
        this.expandedRowKeys.set({});
        if (result) {
          this.chartService.publish(this.idPortfolio(), result);
        }
      });
    });
    this.chartService.securityClicked$
      .pipe(takeUntilDestroyed())
      .subscribe((clicked) => this.chartSecurityClicked(clicked.result, clicked.idSecuritycurrency));
    this.onComponentClick(null);
  }

  isActivated(): boolean {
    return this.activePanelService.isActivated(this);
  }

  /** Registers the show menu; a click inside an expanded transaction table keeps the menu of that table. */
  onComponentClick(event: PanelClickEvent | null): void {
    if (event?.consumedGT) {
      return;
    }
    this.menuRequest?.unsubscribe();
    const showMenu: MenuItem[] = [
      { label: 'SHOW_CHART', command: () => this.showChart() },
      { label: 'HIDE_BENCHMARK_FOR_REPORT', command: () => this.hideBenchmarkForReport() }
    ];
    this.menuRequest = TranslateHelper.translateMenuItems$(showMenu, this.translateService)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((translated) => this.activePanelService.activatePanel(this, { showMenu: translated }));
  }

  hideContextMenu(): void {}

  callMeDeactivate(): void {}

  getHelpContextId(): string {
    return HelpIds.HELP_PORTFOLIOS_SECURITY_PERIODPERFORMANCE;
  }

  ngOnDestroy(): void {
    // The result stays with the chart service, so an open chart keeps it while another tab is shown
    this.activePanelService.destroyPanel(this);
  }

  /** Calculates the period of the form; a request still pending is cancelled. */
  submit(): void {
    const form = this.form();
    const dateFrom = form?.value('dateFrom') as Date | null | undefined;
    const dateTo = form?.value('dateTo') as Date | null | undefined;
    if (!form || !dateFrom || !dateTo) {
      form?.enableSubmit();
      return;
    }
    this.resultRequest?.unsubscribe();
    this.loading.set(true);
    this.resultRequest = this.holdingService
      .getSecurityPeriodPerformance(
        this.idPortfolio(),
        this.isoDate(dateFrom),
        this.isoDate(dateTo),
        this.requestBenchmark()
      )
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          form.enableSubmit();
          this.loading.set(false);
        })
      )
      .subscribe((result) => this.result.set(result));
  }

  /**
   * Starts a scope: pending requests of the previous one are cancelled, then the calendar of the new scope is loaded
   * and the shared date range applied to it. The last calculation of the same scope is shown again, so returning from
   * another tab does not lose it; the result of another scope is removed.
   */
  private loadScope(idPortfolio: number | null): void {
    this.calendarRequest?.unsubscribe();
    this.resultRequest?.unsubscribe();
    this.result.set(this.chartService.takeOverForScope(idPortfolio));
    this.days.set(null);
    this.requestBenchmark.set(undefined);
    this.requestBenchmarkName.set(null);
    this.storedBenchmark.set(null);
    this.benchmarkRequest?.unsubscribe();
    this.benchmarkRequest = this.benchmarkService
      .getBenchmark(idPortfolio)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((stored) => this.storedBenchmark.set(stored));
    this.loading.set(true);
    this.calendarRequest = this.holdingService
      .getFirstAndMissingTradingDays(idPortfolio)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.loading.set(false))
      )
      .subscribe((days) => this.applyRange(days));
  }

  private applyRange(days: FirstAndMissingTradingDays): void {
    const range = initialPerformanceRange(
      days,
      sessionStorage.getItem(GlobalGTSessionNames.PERFORMANCE_DATE_FROM),
      sessionStorage.getItem(GlobalGTSessionNames.PERFORMANCE_DATE_TO)
    );
    this.days.set(days);
    const form = this.form();
    if (range && form) {
      this.dateFrom.set(range.dateFrom);
      form.setValue('dateFrom', range.dateFrom);
      form.setValue('dateTo', range.dateTo);
    }
  }

  /** Stores the range for the period performance and keeps the end date after the start date. */
  private datesChanged(dateFrom: Date | null, dateTo: Date | null): void {
    const days = this.days();
    const bounds = days ? performanceRangeBounds(days) : null;
    if (!days || !bounds) {
      return;
    }
    this.dateFrom.set(dateFrom);
    if (dateFrom) {
      BusinessHelper.saveDateToSessionStore(GlobalGTSessionNames.PERFORMANCE_DATE_FROM, dateFrom);
      if (dateTo && moment(dateTo).isBefore(firstSelectableDateTo(dateFrom, bounds), 'day')) {
        this.form()?.setValue('dateTo', null);
        return;
      }
    }
    syncStoredDateTo(sessionStorage, GlobalGTSessionNames.PERFORMANCE_DATE_TO, dateTo, days);
  }

  /** What the benchmark field shows: the choice for the next calculation, otherwise the stored benchmark. */
  private benchmarkText(): string | null {
    const requested = this.requestBenchmark();
    if (requested === 0) {
      return this.translateService.instant('NO_BENCHMARK');
    }
    if (requested !== undefined) {
      return this.requestBenchmarkName();
    }
    const stored = this.storedBenchmark();
    return stored ? `${stored.name} (${this.translateService.instant(stored.source)})` : null;
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
        this.requestBenchmarkName.set(instrument.name ?? instrument.fromCurrency + '/' + instrument.toCurrency);
        this.requestBenchmark.set(instrument.idSecuritycurrency);
      }
    });
  }

  /** Stores the chosen instrument as default of the scope; the next calculation uses it like any stored benchmark. */
  private saveAsDefaultBenchmark(): void {
    const idBenchmark = this.requestBenchmark();
    const idPortfolio = this.idPortfolio();
    if (!idBenchmark) return;
    this.benchmarkService
      .assignBenchmark(idPortfolio, idBenchmark)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((stored) => {
        this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'BENCHMARK_DEFAULT_SAVED');
        // A scope changed while the request ran keeps its own stored benchmark
        if (idPortfolio === this.idPortfolio()) {
          this.requestBenchmark.set(undefined);
          this.requestBenchmarkName.set(null);
          this.storedBenchmark.set(stored);
        }
      });
  }

  /** The next calculation compares no benchmark; the stored default stays as it is. */
  private hideBenchmarkForReport(): void {
    this.requestBenchmarkName.set(null);
    this.requestBenchmark.set(0);
  }

  private isoDate(date: Date): string {
    return moment(date).format(BaseSettings.FORMAT_DATE_SHORT_NATIVE);
  }

  /** Opens the charts of this view in the lower display area, as the other reports do. */
  private showChart(): void {
    this.router.navigate([
      BaseSettings.MAINVIEW_KEY + '/',
      { outlets: { [AppSettings.MAIN_BOTTOM]: [AppSettings.SECURITY_PERFORMANCE_CHART] } }
    ]);
  }

  /** Expands the row of the security clicked in a chart and brings it into view, while its result is still shown. */
  private chartSecurityClicked(result: SecurityPeriodPerformance, idSecuritycurrency: number): void {
    if (result !== this.result()) {
      return;
    }
    this.expandedRowKeys.update((keys) => ({ ...keys, [idSecuritycurrency]: true }));
    afterNextRender(() => this.table()?.scrollToExpandedRow(idSecuritycurrency), { injector: this.injector });
  }
}
