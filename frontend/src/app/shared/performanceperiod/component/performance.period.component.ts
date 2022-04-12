import {Component, OnDestroy, OnInit, ViewChild} from '@angular/core';
import {DynamicFieldHelper} from '../../helper/dynamic.field.helper';
import {DataType} from '../../../dynamic-form/models/data.type';
import {FormBase} from '../../edit/form.base';
import {TranslateHelper} from '../../helper/translate.helper';
import {TranslateService} from '@ngx-translate/core';
import {FirstAndMissingTradingDays, HoldingService, PerformanceWindowDef, WeekYear} from '../service/holding.service';
import {GlobalSessionNames} from '../../global.session.names';
import {AppHelper} from '../../helper/app.helper';
import {GlobalparameterService} from '../../service/globalparameter.service';
import {BusinessHelper} from '../../helper/business.helper';
import {HelpIds} from '../../help/help.ids';
import {IGlobalMenuAttach} from '../../mainmenubar/component/iglobal.menu.attach';
import {ActivePanelService} from '../../mainmenubar/service/active.panel.service';
import {MenuItem} from 'primeng/api';
import {Subscription} from 'rxjs';
import * as moment from 'moment';
import {Weekday} from '../../helper/weekday';
import {SelectOptionsHelper} from '../../helper/select.options.helper';
import {DynamicFormComponent} from '../../../dynamic-form/containers/dynamic-form/dynamic-form.component';
import {PerformancePeriod, PeriodHoldingAndDiff} from '../model/performance.period';
import {AppSettings} from '../../app.settings';
import {ActivatedRoute, Router} from '@angular/router';
import {ChartDataService} from '../../chart/service/chart.data.service';
import {ChartTrace, PlotlyHelper} from '../../chart/plotly.helper';
import {FormHelper} from '../../../dynamic-form/components/FormHelper';

/**
 * Performance over a certain period for a tenant or portfolio.
 */
@Component({
  template: `
    <div class="data-container" (click)="onComponentClick($event)"
         [ngClass]="{'active-border': isActivated(), 'passiv-border': !isActivated()}">
      <p>{{'PERFORMANCE_REMARK' | translate}} {{firstAndMissingTradingDays?.firstEverTradingDay
        | date:dateFormatPipe}}</p>
      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService"
                    #form="dynamicForm"
                    (submitBt)="submit($event)">
      </dynamic-form>
      <div *ngIf="loading" class="progress-bar-box">
        <h4>{{'LOADING' | translate}}</h4>
        <p-progressBar mode="indeterminate" [style]="{'height': '6px'}"></p-progressBar>
      </div>
      <performance-period-from-to-diff [periodHoldingsAndDiff]="periodHoldingsAndDiff">
      </performance-period-from-to-diff>

      <performance-period-treetable [performancePeriod]="performancePeriod">
      </performance-period-treetable>
    </div>
  `
})
export class PerformancePeriodComponent extends FormBase implements OnInit, OnDestroy, IGlobalMenuAttach {

  // Access the form
  @ViewChild(DynamicFormComponent, {static: true}) form: DynamicFormComponent;
  performancePeriod: PerformancePeriod = null;
  periodHoldingsAndDiff: PeriodHoldingAndDiff[] = [];

  dateFromSubscribe: Subscription;
  dateToSubscribe: Subscription;
  dateFormatPipe: string;
  firstAndMissingTradingDays: FirstAndMissingTradingDays;
  loading = false;
  menuItems: MenuItem[] = [{
    label: 'SHOW_CHART',
    disabled: !this.performancePeriod, command: (event) => this.navigateToChartRoute()
  }];
  chartData: Partial<ChartTrace>[];
  private subscriptionRequestFromChart: Subscription;
  private idPortfolio: number;

  constructor(private router: Router,
              private activatedRoute: ActivatedRoute,
              private activePanelService: ActivePanelService,
              private holdingService: HoldingService,
              private gps: GlobalparameterService,
              private chartDataService: ChartDataService,
              public translateService: TranslateService) {
    super();
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps,
      4, null, false);
    this.formConfig.labelcolumns = 2;

    this.dateFormatPipe = gps.getDateFormat().replace(/Y/g, 'y').replace(/D/g, 'd');
    TranslateHelper.translateMenuItems(this.menuItems, this.translateService);
  }

  ngOnInit(): void {
    this.idPortfolio = +this.activatedRoute.snapshot.paramMap.get('id');
    this.createInputFormDefinition();
    this.loading = true;
    this.holdingService.getFirstAndMissingTradingDays(this.idPortfolio).subscribe((famtd: FirstAndMissingTradingDays) => {
      this.loading = false;
      if (famtd.firstEverTradingDay) {
        this.firstAndMissingTradingDays = famtd;

        const fromDate = famtd.firstEverTradingDay > famtd.lastTradingDayOfLastYear
          ? famtd.firstEverTradingDay : famtd.lastTradingDayOfLastYear;
        this.configObject.dateFrom.formControl.setValue(AppHelper.getDateFromSessionStorage(GlobalSessionNames.PERFORMANCE_DATE_FROM,
          new Date(fromDate)));
        this.configObject.dateTo.formControl.setValue(new Date(famtd.latestTradingDay));
        this.configObject.dateFrom.calendarConfig = {
          minDate: new Date(famtd.firstEverTradingDay),
          maxDate: new Date(famtd.secondLatestTradingDay),
          disabledDays: [Weekday.Saturday, Weekday.Sunday], disabledDates: famtd.holidayAndMissingQuoteDays
        };
        this.configObject.dateTo.calendarConfig = {
          minDate: new Date(famtd.secondEverTradingDay),
          maxDate: new Date(famtd.latestTradingDay),
          disabledDays: [Weekday.Saturday, Weekday.Sunday], disabledDates: famtd.holidayAndMissingQuoteDays
        };
        this.valueChangedOnDateTo();
        this.valueChangedOnDateFrom();
        this.setMonthWeekPeriod();
      } else {
        // There are no trades -> performance calculation not possible
        FormHelper.disableEnableFieldConfigs(true, this.config);
      }
    });
  }

  valueChangedOnDateFrom(): void {
    this.dateFromSubscribe = this.configObject.dateFrom.formControl.valueChanges.subscribe((dateFrom: Date) => {
      this.setMinDateForToDate(dateFrom);
      this.setMonthWeekPeriod();
    });
  }

  valueChangedOnDateTo(): void {
    this.dateToSubscribe = this.configObject.dateTo.formControl.valueChanges.subscribe((dateTo: Date) => {
      this.setMonthWeekPeriod();
    });
  }

  helpLink(): void {
    // Used in a dialog
    BusinessHelper.toExternalHelpWebpage(this.gps.getUserLang(), HelpIds.HELP_PORTFOLIOS_PERIODPERFORMANCE);
  }

  isActivated(): boolean {
    return this.activePanelService.isActivated(this);
  }

  onComponentClick(event): void {
    this.activePanelService.activatePanel(this, {showMenu: this.menuItems});
  }

  hideContextMenu(): void {
  }

  callMeDeactivate(): void {
  }

  getHelpContextId(): HelpIds {
    return HelpIds.HELP_PORTFOLIOS_PERIODPERFORMANCE;
  }

  submit(value: { [name: string]: any }): void {
    const pWD: PerformanceWindowDef = new PerformanceWindowDef(this.idPortfolio);
    this.form.cleanMaskAndTransferValuesToBusinessObject(pWD, true);
    this.loading = true;
    this.holdingService.getPeriodPerformance(pWD).subscribe(periodPerformance => {
      this.performancePeriod = periodPerformance;
      this.periodHoldingsAndDiff = [periodPerformance.firstDayTotals, periodPerformance.lastDayTotals, periodPerformance.difference];
      this.configObject.submit.disabled = false;
      this.menuItems[0].disabled = false;
      this.changeToOpenChart();
      this.loading = false;
    }, () => {
      this.configObject.submit.disabled = false;
      this.loading = false;
    });
  }

  ngOnDestroy(): void {
    this.dateFromSubscribe && this.dateFromSubscribe.unsubscribe();
    this.dateToSubscribe && this.dateToSubscribe.unsubscribe();
    this.subscriptionRequestFromChart && this.subscriptionRequestFromChart.unsubscribe();
  }

  private createInputFormDefinition(): void {
    this.config = [
      DynamicFieldHelper.createFieldPcalendarHeqF(DataType.DateString, 'dateFrom', true,
        {usedLayoutColumns: 4}),
      DynamicFieldHelper.createFieldPcalendarHeqF(DataType.DateString, 'dateTo', true,
        {usedLayoutColumns: 4}),
      DynamicFieldHelper.createFieldSelectStringHeqF('periodSplit', true,
        {usedLayoutColumns: 4}),
      DynamicFieldHelper.createSubmitButton('APPLY')
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
    this.configObject.periodSplit.valueKeyHtmlOptions =
      SelectOptionsHelper.createHtmlOptionsFromEnumAddEmpty(this.translateService, WeekYear);

  }

  private setMinDateForToDate(dateFrom: Date): void {
    let dateTo = moment(dateFrom).add(1, 'd');
    while (dateTo.isBefore(this.firstAndMissingTradingDays.latestTradingDay) && (dateTo.day() === Weekday.Saturday
      || dateTo.day() === Weekday.Sunday
      || this.firstAndMissingTradingDays.holidayAndMissingQuoteDays.indexOf(dateTo.toDate()) >= 0)) {
      dateTo = moment(dateTo).add(1, 'd');
    }
    this.configObject.dateTo.calendarConfig.minDate = dateTo.toDate();
    if (this.configObject.dateTo.formControl.value.getTime() < this.configObject.dateTo.calendarConfig.minDate.getTime()) {
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
      if (moment(pWD.dateTo).diff(pWD.dateFrom, 'months', true) < this.firstAndMissingTradingDays.minIncludeMonthLimit) {
        disabled.push(WeekYear.WM_YEAR);
      }
      if (moment(pWD.dateTo).diff(pWD.dateFrom, 'weeks', true) > this.firstAndMissingTradingDays.maxWeekLimit) {
        disabled.push(WeekYear.WM_WEEK);
      }
      SelectOptionsHelper.disableEnableExistingHtmlOptionsFromEnum(this.configObject.periodSplit.valueKeyHtmlOptions, WeekYear, disabled);
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
    this.router.navigate([AppSettings.MAINVIEW_KEY + '/', {
      outlets: {
        mainbottom: [AppSettings.CHART_GENERAL_PURPOSE, AppSettings.PERFORMANCE_KEY]
      }
    }]);
  }

  private prepareChartDataWithRequest(): void {
    this.subscriptionRequestFromChart = this.chartDataService.requestFromChart$.subscribe(id => {
        if (id === AppSettings.PERFORMANCE_KEY) {
          this.prepareCharDataAndSentToChart();
        }
      }
    );
  }

  private prepareCharDataAndSentToChart(): void {
    this.chartData = [];
    const pcdds = this.performancePeriod.performanceChartDayDiff;
    for (let i = 0; i < pcdds.length; i++) {
      const pcdd = pcdds[i];
      Object.keys(pcdd).filter((key, tIndex) => key !== 'date').forEach((key, tIndex) => {
        let trace: Partial<ChartTrace>;
        if (i === 0) {
          // Create the trace
          trace = PlotlyHelper.initializeChartTrace(key, 'scatter', 'lines');
          this.translateService.get(AppHelper.convertPropertyForLabelOrHeaderKey(key))
            .subscribe(translated => trace.name = translated);
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
          rangeslider: {range: [pcdds[0].date, pcdds[pcdds.length - 1].date]},
          type: 'date'
        }
      },
      options: {
        modeBarButtonsToRemove: ['hoverCompareCartesian', 'hoverClosestCartesian']
      },

    });
  }
}
