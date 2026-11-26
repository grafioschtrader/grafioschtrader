import {
  Component,
  DestroyRef,
  ElementRef,
  Injector,
  OnDestroy,
  afterNextRender,
  computed,
  effect,
  inject,
  input,
  numberAttribute,
  signal,
  untracked,
  viewChild
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { DialogService } from '@openng/optimus-ui/dynamicdialog';
import { ProgressBarModule } from '@openng/optimus-ui/progressbar';
import type { Layout } from 'plotly.js';
import { Subscription, finalize } from 'rxjs';

import { ShowRecordConfigBase } from '../../lib/datashowbase/show.record.config.base';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { FormConfig } from '../../lib/dynamic-form/models/form.config';
import type { FieldFormGroup } from '../../lib/dynamic-form/models/form.group.definition';
import { SignalDynamicFormComponent } from '../../lib/dynamic-form/signal/signal-dynamic-form.component';
import { AppHelper } from '../../lib/helper/app.helper';
import { DynamicFieldHelper } from '../../lib/helper/dynamic.field.helper';
import { TranslateHelper } from '../../lib/helper/translate.helper';
import { GlobalparameterService } from '../../lib/services/globalparameter.service';
import { PerformancePeriodBenchmarkComponent } from '../../performanceperiod/component/performance-period-benchmark.component';
import {
  benchmarkTraces,
  PerformanceChartNames,
  RETURN_FIELDS,
  WEALTH_FIELDS
} from '../../performanceperiod/model/performance-period.chart';
import { SecuritycurrencySearchAndSetDialogComponent } from '../../securitycurrency/component/securitycurrency-search-and-set-dialog.component';
import { SupplementCriteria } from '../../securitycurrency/model/supplement.criteria';
import { PlotlyHelper } from '../../shared/chart/plotly.helper';
import { PlotlyInstance, PlotlyLoaderService } from '../../shared/chart/plotly.loader';
import { PlotlyLocales } from '../../shared/plotlylocale/plotly.locales';
import type { ReplayBenchmarkComparison } from '../model/replay-benchmark';
import { AlgoSimulationRunService } from '../service/algo-simulation-run.service';

/** The one field of the form. */
interface ReplayBenchmarkFormModel {
  benchmarkName: string | null;
}

/**
 * Comparison of the completed historical replay of a simulation environment with a benchmark. The server calculates it
 * on every request from the stored equity curve; this view only shows it: the comparison, the relative risk and the
 * shadow portfolio in the fieldsets of the period performance, the cumulative returns and the values of replay and
 * shadow portfolio as two charts, and the moment of the calculation, since a later request can differ after a price
 * correction or a changed default. The benchmark field chooses an instrument for the next calculation only; the stored
 * benchmark of the environment or of its main client is used otherwise.
 */
@Component({
  selector: 'algo-simulation-run-benchmark',
  template: `
    <div class="data-container">
      <h4>{{ 'SIMULATION_RUN_BENCHMARK' | translate }}</h4>
      <signal-dynamic-form
        [config]="config"
        [formConfig]="formConfig"
        #form="signalDynamicForm"
        (submitBt)="submit()" />
      @if (loading()) {
        <p-progressBar mode="indeterminate" [style]="{ height: '6px' }" />
      }
      @if (result(); as calculated) {
        <p>{{ 'CALCULATED_AT' | translate }}: {{ calculatedAtText() }}</p>
        <performance-period-benchmark [benchmark]="calculated.comparison" />
        <div class="replay-benchmark-chart" #returnsChart></div>
        <div class="replay-benchmark-chart" #wealthChart></div>
      } @else if (empty()) {
        <p>{{ 'SIMULATION_RUN_BENCHMARK_NONE' | translate }}</p>
      }
    </div>
  `,
  styles: [
    `
      .replay-benchmark-chart {
        width: 100%;
        height: 360px;
      }
    `
  ],
  providers: [DialogService],
  imports: [TranslateModule, ProgressBarModule, SignalDynamicFormComponent, PerformancePeriodBenchmarkComponent]
})
export class AlgoSimulationRunBenchmarkComponent implements OnDestroy {
  /** Tenant id of the simulation environment. */
  readonly id = input.required({ transform: numberAttribute });

  private readonly runService = inject(AlgoSimulationRunService);
  private readonly translateService = inject(TranslateService);
  private readonly gps = inject(GlobalparameterService);
  private readonly dialogService = inject(DialogService);
  private readonly plotlyLoader = inject(PlotlyLoaderService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly injector = inject(Injector);

  private readonly form = viewChild<SignalDynamicFormComponent<ReplayBenchmarkFormModel>>('form');
  private readonly returnsChart = viewChild<ElementRef<HTMLElement>>('returnsChart');
  private readonly wealthChart = viewChild<ElementRef<HTMLElement>>('wealthChart');

  readonly formConfig: FormConfig = { labelColumns: 2, nonModal: true };
  readonly config: FieldFormGroup[] = [
    DynamicFieldHelper.createFieldInputButtonHeqF(
      DataType.String,
      'benchmarkName',
      () => this.searchBenchmark(),
      false,
      {
        usedLayoutColumns: 8
      }
    ),
    DynamicFieldHelper.createSubmitButton('APPLY')
  ];

  readonly result = signal<ReplayBenchmarkComparison | null>(null);
  /** True once a request returned no comparison: no completed replay or no benchmark. */
  readonly empty = signal(false);
  readonly loading = signal(false);

  /** Benchmark chosen for the next calculation only; undefined uses the stored one. */
  private readonly requestBenchmark = signal<number | undefined>(undefined);
  private readonly requestBenchmarkName = signal<string | null>(null);

  private readonly calculatedAtField = ShowRecordConfigBase.createColumnConfig(
    DataType.DateTimeString,
    'calculatedAt',
    null
  );
  readonly calculatedAtText = computed(
    () =>
      AppHelper.getValueByPathWithField(
        this.gps,
        this.translateService,
        this.result(),
        this.calculatedAtField,
        'calculatedAt'
      ) ?? ''
  );

  private request?: Subscription;
  private plotly?: PlotlyInstance;
  private destroyed = false;

  constructor() {
    TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config, false);
    effect(() => {
      const idTenant = this.id();
      untracked(() => {
        this.requestBenchmark.set(undefined);
        this.requestBenchmarkName.set(null);
        this.load(idTenant);
      });
    });
    effect(() => {
      const form = this.form();
      const text = this.benchmarkText();
      // The form builds its fields after the first render; this effect runs again once the field exists
      const ready = !!form?.hasField('benchmarkName');
      untracked(() => {
        if (ready && form) {
          form.setValue('benchmarkName', text);
        }
      });
    });
    effect(() => {
      const result = this.result();
      untracked(() => this.draw(result));
    });
  }

  ngOnDestroy(): void {
    this.destroyed = true;
    this.purge();
  }

  submit(): void {
    this.load(this.id());
  }

  /** Requests the comparison; a request still pending is cancelled. */
  private load(idTenant: number): void {
    this.request?.unsubscribe();
    this.loading.set(true);
    this.request = this.runService
      .benchmarkComparison(idTenant, this.requestBenchmark())
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          this.loading.set(false);
          this.form()?.enableSubmit();
        })
      )
      .subscribe((result) => {
        this.result.set(result);
        this.empty.set(!result);
      });
  }

  /** The chosen instrument, otherwise the benchmark of the last calculation with its source. */
  private benchmarkText(): string | null {
    if (this.requestBenchmark() !== undefined) {
      return this.requestBenchmarkName();
    }
    const comparison = this.result()?.comparison;
    return comparison ? `${comparison.name} (${this.translateService.instant(comparison.source)})` : null;
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

  /** Draws both charts once their elements exist; a result replaced while Plotly loads is not drawn. */
  private draw(result: ReplayBenchmarkComparison | null): void {
    this.purge();
    if (!result?.comparison.chart.length) {
      return;
    }
    const names: PerformanceChartNames = {};
    [...RETURN_FIELDS, ...WEALTH_FIELDS].forEach(
      (field) =>
        (names[field] = this.translateService.instant(AppHelper.removeSomeStringAndToUpperCaseWithUnderscore(field)))
    );
    afterNextRender(
      () =>
        this.plotlyLoader.load().then(
          (plotly) => {
            const returnsElement = this.returnsChart()?.nativeElement;
            const wealthElement = this.wealthChart()?.nativeElement;
            if (this.destroyed || result !== this.result() || !returnsElement || !wealthElement) {
              return;
            }
            this.plotly = plotly;
            const config = { ...PlotlyLocales.setPlotyLocales(this.gps), displaylogo: false };
            plotly.newPlot(
              returnsElement,
              benchmarkTraces(result.comparison, names, RETURN_FIELDS),
              this.layout(this.translateService.instant('RETURNS_PERCENT'), true),
              config
            );
            plotly.newPlot(
              wealthElement,
              benchmarkTraces(result.comparison, names, WEALTH_FIELDS),
              this.layout(`${this.translateService.instant('WEALTH')} ${result.comparison.mainCurrency}`, false),
              config
            );
          },
          () => {}
        ),
      { injector: this.injector }
    );
  }

  private layout(title: string, percent: boolean): Partial<Layout> {
    return {
      title: { text: title },
      legend: PlotlyHelper.getLegendUnderChart(12),
      hovermode: 'x unified',
      xaxis: { type: 'date' },
      yaxis: percent ? { ticksuffix: ' %' } : {}
    };
  }

  private purge(): void {
    [this.returnsChart()?.nativeElement, this.wealthChart()?.nativeElement].forEach((element) => {
      if (element && this.plotly) {
        this.plotly.purge(element);
      }
    });
  }
}
