import {
  afterNextRender,
  computed,
  Injector,
  signal,
  Component,
  DestroyRef,
  OnDestroy,
  OnInit,
  ViewChild,
  inject
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
import { AppHelper } from '../../lib/helper/app.helper';
import { GlobalparameterService } from '../../lib/services/globalparameter.service';
import { BusinessHelper } from '../../shared/helper/business.helper';
import { HelpIds } from '../../lib/help/help.ids';
import { IGlobalMenuAttach } from '../../lib/mainmenubar/component/iglobal.menu.attach';
import { ActivePanelService } from '../../lib/mainmenubar/service/active.panel.service';
import { MenuItem } from '@openng/optimus-ui/api';
import { Subscription } from 'rxjs';
import moment from 'moment';
import { Weekday } from '../../shared/helper/weekday';
import { SelectOptionsHelper } from '../../lib/helper/select.options.helper';
import { DynamicFormComponent } from '../../lib/dynamic-form/containers/dynamic-form/dynamic-form.component';
import { DYNAMIC_FORM_IMPORTS } from '../../lib/dynamic-form/dynamic-form.imports';
import { PerformancePeriod } from '../model/performance.period';
import { AppSettings } from '../../shared/app.settings';
import { ActivatedRoute, Router } from '@angular/router';
import { ChartDataService } from '../../shared/chart/service/chart.data.service';
import { ChartTrace, PlotlyHelper } from '../../shared/chart/plotly.helper';
import { FormHelper } from '../../lib/dynamic-form/components/FormHelper';
import { GlobalGTSessionNames } from '../../shared/global.gt.session.names';
import { BaseSettings } from '../../lib/base.settings';

import { ProgressBarModule } from '@openng/optimus-ui/progressbar';
import { TenantPerformanceFromToDiffComponent } from './performance-period-from-to-diff.component';
import { TenantPerformanceTreetableComponent } from './performance-period-treetable.component';
import { PerformancePeriodMetricsComponent } from './performance-period-metrics.component';
import { PerformanceReportDialogService } from '../service/performance-report-dialog.service';

/**
 * Performance over a certain period for a tenant or portfolio.
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

      <performance-period-metrics [metrics]="performancePeriod()?.metrics" />

      <performance-period-treetable [performancePeriod]="performancePeriod()" />
    </div>
  `,
  imports: [
    TranslateModule,
    ...DYNAMIC_FORM_IMPORTS,
    ProgressBarModule,
    TenantPerformanceFromToDiffComponent,
    PerformancePeriodMetricsComponent,
    TenantPerformanceTreetableComponent
  ]
})
export class PerformancePeriodComponent extends FormBase implements OnInit, OnDestroy, IGlobalMenuAttach {
  // Access the form
  @ViewChild(DynamicFormComponent, { static: true }) form: DynamicFormComponent;
  readonly performancePeriod = signal<PerformancePeriod>(null);
  readonly periodHoldingsAndDiff = computed(() => {
    const period = this.performancePeriod();
    return period ? [period.firstDayTotals, period.lastDayTotals, period.difference] : [];
  });

  dateFromSubscribe: Subscription;
  dateToSubscribe: Subscription;
  readonly firstAndMissingTradingDays = signal<FirstAndMissingTradingDays>(null);
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
  private calculationRequest: Subscription;
  private menuRequest: Subscription;
  readonly loading = signal(false);
  readonly reportDialogService = inject(PerformanceReportDialogService);
  readonly menuItems = signal<MenuItem[]>([]);
  chartData: Partial<ChartTrace>[];
  private subscriptionRequestFromChart: Subscription;
  private idPortfolio: number;
  private reportWindow: PerformanceWindowDef;
  private destroyRef = inject(DestroyRef);

  constructor(
    private router: Router,
    private activatedRoute: ActivatedRoute,
    private activePanelService: ActivePanelService,
    private holdingService: HoldingService,
    private gps: GlobalparameterService,
    private chartDataService: ChartDataService,
    public translateService: TranslateService
  ) {
    super();
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps, 4, null, false);
    this.formConfig.labelColumns = 2;

    this.publishMenu();
  }

  ngOnInit(): void {
    this.idPortfolio = +this.activatedRoute.snapshot.paramMap.get('id');
    this.createInputFormDefinition();
    this.loading.set(true);
    afterNextRender(() => this.initializeDateRange(), { injector: this.renderInjector });
  }

  private initializeDateRange(): void {
    this.holdingService
      .getFirstAndMissingTradingDays(this.idPortfolio)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (famtd: FirstAndMissingTradingDays) => {
          this.loading.set(false);
          if (famtd.firstEverTradingDay) {
            this.firstAndMissingTradingDays.set(famtd);
            const fromDate =
              famtd.firstEverTradingDay > famtd.lastTradingDayOfLastYear
                ? famtd.firstEverTradingDay
                : famtd.lastTradingDayOfLastYear;
            this.configObject.dateFrom.formControl.setValue(
              BusinessHelper.getDateFromSessionStorage(GlobalGTSessionNames.PERFORMANCE_DATE_FROM, new Date(fromDate))
            );
            this.configObject.dateTo.formControl.setValue(new Date(famtd.latestTradingDay));
            this.configObject.dateFrom.calendarConfig = {
              minDate: new Date(famtd.firstEverTradingDay),
              maxDate: new Date(famtd.secondLatestTradingDay),
              disabledDays: [Weekday.Saturday, Weekday.Sunday],
              disabledDates: famtd.holidayAndMissingQuoteDays
            };
            this.configObject.dateTo.calendarConfig = {
              minDate: new Date(famtd.secondEverTradingDay),
              maxDate: new Date(famtd.latestTradingDay),
              disabledDays: [Weekday.Saturday, Weekday.Sunday],
              disabledDates: famtd.holidayAndMissingQuoteDays
            };
            this.valueChangedOnDateTo();
            this.valueChangedOnDateFrom();
            this.setMonthWeekPeriod();
          } else {
            // There are no trades -> performance calculation not possible
            FormHelper.disableEnableFieldConfigs(true, this.config);
          }
        },
        error: () => this.loading.set(false)
      });
  }

  valueChangedOnDateFrom(): void {
    this.dateFromSubscribe?.unsubscribe();
    this.dateFromSubscribe = this.configObject.dateFrom.formControl.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((dateFrom: Date) => {
        this.setMinDateForToDate(dateFrom);
        BusinessHelper.saveDateToSessionStore(GlobalGTSessionNames.PERFORMANCE_DATE_FROM, dateFrom);
        this.setMonthWeekPeriod();
      });
  }

  valueChangedOnDateTo(): void {
    this.dateToSubscribe?.unsubscribe();
    this.dateToSubscribe = this.configObject.dateTo.formControl.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((dateTo: Date) => {
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

  onComponentClick(event): void {
    this.activePanelService.activatePanel(this, { showMenu: this.menuItems() });
  }

  hideContextMenu(): void {}

  callMeDeactivate(): void {}

  getHelpContextId(): string {
    return HelpIds.HELP_PORTFOLIOS_PERIODPERFORMANCE;
  }

  submit(value: { [name: string]: any }): void {
    const pWD: PerformanceWindowDef = new PerformanceWindowDef(this.idPortfolio);
    this.form.cleanMaskAndTransferValuesToBusinessObject(pWD, true);
    this.calculationRequest?.unsubscribe();
    this.loading.set(true);
    this.calculationRequest = this.holdingService
      .getPeriodPerformance(pWD)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (periodPerformance) => {
          this.performancePeriod.set(periodPerformance);
          this.configObject.submit.disabled = false;
          this.reportWindow = { ...pWD };
          this.publishMenu();
          this.changeToOpenChart();
          this.loading.set(false);
        },
        error: () => {
          this.configObject.submit.disabled = false;
          this.loading.set(false);
        }
      });
  }

  ngOnDestroy(): void {
    this.dateFromSubscribe && this.dateFromSubscribe.unsubscribe();
    this.dateToSubscribe && this.dateToSubscribe.unsubscribe();
    this.subscriptionRequestFromChart && this.subscriptionRequestFromChart.unsubscribe();
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
        }
      ],
      this.translateService
    )
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((items) => {
        this.menuItems.set(items);
        if (this.isActivated()) this.onComponentClick(null);
      });
  }

  /** The dialog uses the dates of the displayed calculation, not the possibly edited form values. */
  private openPdfReport(): void {
    if (this.reportWindow) {
      this.reportDialogService.open(this.reportWindow, this.destroyRef);
    }
  }

  private createInputFormDefinition(): void {
    this.config = [
      DynamicFieldHelper.createFieldPcalendarHeqF(DataType.DateString, 'dateFrom', true, { usedLayoutColumns: 4 }),
      DynamicFieldHelper.createFieldPcalendarHeqF(DataType.DateString, 'dateTo', true, { usedLayoutColumns: 4 }),
      DynamicFieldHelper.createFieldSelectStringHeqF('periodSplit', true, {
        usedLayoutColumns: 4
      }),
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

  private setMinDateForToDate(dateFrom: Date): void {
    let dateTo = moment(dateFrom).add(1, 'd');
    while (
      dateTo.isBefore(this.firstAndMissingTradingDays().latestTradingDay) &&
      (dateTo.day() === Weekday.Saturday ||
        dateTo.day() === Weekday.Sunday ||
        this.firstAndMissingTradingDays().holidayAndMissingQuoteDays.some((day) => dateTo.isSame(day, 'day')))
    ) {
      dateTo = moment(dateTo).add(1, 'd');
    }
    this.configObject.dateTo.calendarConfig.minDate = dateTo.toDate();
    const currentDateTo: Date = this.configObject.dateTo.formControl.value;
    if (currentDateTo && currentDateTo.getTime() < this.configObject.dateTo.calendarConfig.minDate.getTime()) {
      this.configObject.dateTo.formControl.setValue(null);
    }
  }

  private setMonthWeekPeriod(): void {
    const pWD: PerformanceWindowDef = new PerformanceWindowDef(this.idPortfolio);
    this.form.cleanMaskAndTransferValuesToBusinessObject(pWD, true);
    // const dateFrom = this.configObject.dateFrom.formControl.value;
    // const dateTo = this.configObject.dateTo.formControl.value;
    // const period = this.configObject.periodSplit.formControl.value;
    const disabled: any[] = [];
    if (pWD.dateFrom && pWD.dateTo) {
      if (
        moment(pWD.dateTo).diff(pWD.dateFrom, 'months', true) < this.firstAndMissingTradingDays().minIncludeMonthLimit
      ) {
        disabled.push(WeekYear.WM_YEAR);
      }
      if (moment(pWD.dateTo).diff(pWD.dateFrom, 'weeks', true) > this.firstAndMissingTradingDays().maxWeekLimit) {
        disabled.push(WeekYear.WM_WEEK);
      }
      this.configObject.periodSplit.valueKeyHtmlOptions = SelectOptionsHelper.disableEnableExistingHtmlOptionsFromEnum(
        this.configObject.periodSplit.valueKeyHtmlOptions.map((option) => ({ ...option })),
        WeekYear,
        disabled
      );
      if (disabled.indexOf(WeekYear[pWD.periodSplit]) >= 0) {
        this.configObject.periodSplit.formControl.setValue('');
      }
    }
  }

  private changeToOpenChart(): void {
    if (this.subscriptionRequestFromChart) {
      this.prepareCharDataAndSentToChart();
    } else {
      const urlPattern = new RegExp(`.*\/\/${AppSettings.MAIN_BOTTOM}.*\/${AppSettings.PERFORMANCE_KEY}\\\)`);
      urlPattern.test(this.router.url) && this.prepareCharDataAndSentToChart();
    }
  }

  private navigateToChartRoute(): void {
    !this.subscriptionRequestFromChart && this.prepareChartDataWithRequest();
    this.router.navigate([
      BaseSettings.MAINVIEW_KEY + '/',
      {
        outlets: {
          mainbottom: [AppSettings.CHART_GENERAL_PURPOSE, AppSettings.PERFORMANCE_KEY]
        }
      }
    ]);
  }

  private prepareChartDataWithRequest(): void {
    this.subscriptionRequestFromChart = this.chartDataService.requestFromChart$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((id) => {
        if (id === AppSettings.PERFORMANCE_KEY) {
          this.prepareCharDataAndSentToChart();
        }
      });
  }

  private prepareCharDataAndSentToChart(): void {
    this.chartData = [];
    const pcdds = this.performancePeriod().performanceChartDayDiff;
    for (let i = 0; i < pcdds.length; i++) {
      const pcdd = pcdds[i];
      Object.keys(pcdd)
        .filter((key, tIndex) => key !== 'date')
        .forEach((key, tIndex) => {
          let trace: Partial<ChartTrace>;
          if (i === 0) {
            // Create the trace
            trace = PlotlyHelper.initializeChartTrace(key, 'scatter', 'lines');
            this.translateService
              .get(AppHelper.removeSomeStringAndToUpperCaseWithUnderscore(key))
              .pipe(takeUntilDestroyed(this.destroyRef))
              .subscribe((translated) => (trace.name = translated));
            this.chartData.push(trace);
          } else {
            trace = this.chartData[tIndex];
          }
          trace.x.push(pcdd.date);
          trace.y.push(pcdd[key]);
        });
    }

    this.chartDataService.sentToChart({
      data: this.chartData,
      layout: {
        legend: PlotlyHelper.getLegendUnderChart(12),
        hovermode: 'closest',
        xaxis: {
          autorange: true,
          rangeslider: { range: [pcdds[0].date, pcdds[pcdds.length - 1].date] },
          type: 'date'
        }
      },
      options: {
        modeBarButtonsToRemove: ['hoverCompareCartesian', 'hoverClosestCartesian']
      }
    });
  }
}
