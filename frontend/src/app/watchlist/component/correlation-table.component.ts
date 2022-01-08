import {Component, Input, OnDestroy} from '@angular/core';
import {TableConfigBase} from '../../shared/datashowbase/table.config.base';
import {FilterService, MenuItem} from 'primeng/api';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {UserSettingsService} from '../../shared/service/user.settings.service';
import {
  CorrelationInstrument,
  CorrelationResult,
  CorrelationRollingResult,
  CorrelationSet,
  MinMaxDateHistoryquote,
  SamplingPeriodType
} from '../../entities/correlation.set';
import {DataType} from '../../dynamic-form/models/data.type';
import {AppSettings} from '../../shared/app.settings';
import {ColumnConfig} from '../../shared/datashowbase/column.config';
import {Securitycurrency} from '../../entities/securitycurrency';
import {Security} from '../../entities/security';
import {InfoLevelType} from '../../shared/message/info.leve.type';
import {ProcessedActionData} from '../../shared/types/processed.action.data';
import {CorrelationSetService} from '../service/correlation.set.service';
import {MessageToastService} from '../../shared/message/message.toast.service';
import {DataChangedService} from '../../shared/maintree/service/data.changed.service';
import {TenantLimit} from '../../entities/backend/tenant.limit';
import {ProcessedAction} from '../../shared/types/processed.action';
import {Subscription} from 'rxjs';
import {ChartDataService} from '../../shared/chart/service/chart.data.service';
import {Router} from '@angular/router';
import {PlotlyHelper} from '../../shared/chart/plotly.helper';
import * as moment from 'moment';
import {BusinessHelper} from '../../shared/helper/business.helper';
import {AppHelper} from '../../shared/helper/app.helper';

/**
 * Component to add and remove instruments to the correlation matrix. It supports also creation of line graph
 * for showing the rolling correlation.
 */
@Component({
  selector: 'correlation-table',
  template: `
    <p-table [columns]="fields" [value]="securitycurrencyList" selectionMode="single"
             [(selection)]="selectedEntity" dataKey="idSecuritycurrency"
             (sortFunction)="customSort($event)" [customSort]="true"
             responsiveLayout="scroll"
             styleClass="sticky-table p-datatable-striped p-datatable-gridlines">
      <ng-template pTemplate="header" let-fields>
        <tr>
          <th style="width:24px"></th>
          <th *ngFor="let field of fields" [pSortableColumn]="field.field" [pTooltip]="field.headerTooltipTranslated"
              [style.max-width.px]="field.width"
              [ngStyle]="field.width? {'flex-basis': '0 0 ' + field.width + 'px'}: {}">
            {{field.headerTranslated}}
            <p-sortIcon [field]="field.field"></p-sortIcon>
          </th>
        </tr>
      </ng-template>
      <ng-template pTemplate="body" let-expanded="expanded" let-el let-columns="fields">
        <tr [pSelectableRow]="el">
          <td>
            <a href="#" [pRowToggler]="el">
              <i [ngClass]="expanded ? 'fa fa-fw fa-chevron-circle-down' : 'fa fa-fw fa-chevron-circle-right'"></i>
            </a>
          </td>
          <td *ngFor="let field of fields" [style.background-color]="getBackgroundColor(el, field)"
              [ngClass]="field.dataType===DataType.NumericShowZero ? 'text-right': ''"
              (click)="cellClick(field)"
              [style.max-width.px]="field.width"
              [ngStyle]="field.width? {'flex-basis': '0 0 ' + field.width + 'px'}: {}">
            <ng-container [ngSwitch]="field.templateName">
              <ng-container *ngSwitchCase="'check'">
                <span><i [ngClass]="{'fa fa-check': getValueByPath(el, field)}" aria-hidden="true"></i></span>
              </ng-container>
              <ng-container *ngSwitchDefault>
                {{getValueByPath(el, field)}}
              </ng-container>
            </ng-container>
          </td>
        </tr>
      </ng-template>
      <ng-template pTemplate="rowexpansion" let-securitycrrency let-columns="fields">
        <tr>
          <td [attr.colspan]="numberOfVisibleColumns + 1" style="overflow:visible;">
            <instrument-statistics-result [idSecuritycurrency]="securitycrrency.idSecuritycurrency"
                                          [dateFrom]="correlationSet.dateFrom" [dateTo]="correlationSet.dateTo">
            </instrument-statistics-result>
          </td>
        </tr>
      </ng-template>
    </p-table>
    <correlation-add-instrument *ngIf="visibleAddInstrumentDialog" [idCorrelationSet]="correlationSet.idCorrelationSet"
                                [tenantLimits]="tenantLimits"
                                [visibleAddInstrumentDialog]="visibleAddInstrumentDialog"
                                (closeDialog)="handleCloseAddInstrumentDialog($event)">
    </correlation-add-instrument>
  `
})
export class CorrelationTableComponent extends TableConfigBase implements OnDestroy {
  @Input() childToParent: ChildToParent;

  /**
   * Limit instruments per correlation set
   */
  tenantLimits: TenantLimit[];
  securitycurrencyList: Securitycurrency[];
  selectedEntity: Securitycurrency;
  visibleAddInstrumentDialog: boolean;

  private readonly nameHeader = 'name';
  private readonly tickerSymbol = 'tickerSymbol';
  private readonly minDate = 'minDate';
  private correlationResult: CorrelationResult;
  private correlationSet: CorrelationSet;
  private subscriptionInstrumentAdded: Subscription;
  private securityCurrencyColumn: Securitycurrency;
  private subscriptionRequestFromChart: Subscription;
  private traceShow: { [name: string]: any } = {};


  constructor(private router: Router,
    private correlationSetService: CorrelationSetService,
    private messageToastService: MessageToastService,
    private dataChangedService: DataChangedService,
    private chartDataService: ChartDataService,
    filterService: FilterService,
    translateService: TranslateService,
    gps: GlobalparameterService,
    usersettingsService: UserSettingsService) {
    super(filterService, usersettingsService, translateService, gps);
    this.createDynamicTableDefinition(null, null);
    this.addInstrumentsToCorrelationSet();
  }

  cellClick(field: ColumnConfig): void {
    const columnHeader = field.headerKey === this.nameHeader
    || field.headerKey === this.tickerSymbol ? null : field.headerKey;

    this.securityCurrencyColumn = this.correlationSet.securitycurrencyList.find(sc => (sc.hasOwnProperty(this.tickerSymbol)
      ? (<Security>sc)[this.tickerSymbol] : sc.name) === columnHeader);
  }

  /**
   * Called from the parent component
   */
  parentSelectionChanged(correlationSet: CorrelationSet, correlationResult: CorrelationResult): void {
    this.createDynamicTableDefinition(correlationSet, correlationResult);
    this.readListLimitOnce(correlationSet);
  }

  public prepareEditMenu(): MenuItem[] {
    const menuItems: MenuItem[] = [];
    menuItems.push({separator: true});
    menuItems.push(
      {
        label: 'ADD_EXISTING_SECURITY' + AppSettings.DIALOG_MENU_SUFFIX, command: (e) => this.addExistingSecurity(e),
        disabled: !this.tenantLimits || !this.correlationSet
          || this.correlationSet.securitycurrencyList.length >= this.tenantLimits[0].limit
      }
    );

    if (this.selectedEntity) {
      menuItems.push({
        label: 'REMOVE_INSTRUMENT', command: (event) => this.removeInstrumentFromCorrelationSet(this.selectedEntity
          .idSecuritycurrency)
      });
    }
    return menuItems;
  }

  public prepareShowMenu(): MenuItem[] {
    const menuItems: MenuItem[] = [];
    if (this.correlationSet && SamplingPeriodType[this.correlationSet.samplingPeriod] !== SamplingPeriodType.ANNUAL_RETURNS) {
      if (this.selectedEntity && this.securityCurrencyColumn && this.selectedEntity !== this.securityCurrencyColumn
        && !this.isGraphForPairShowing(this.selectedEntity.idSecuritycurrency, this.securityCurrencyColumn.idSecuritycurrency)) {
        menuItems.push({separator: true});
        menuItems.push({
          label: 'CORRELATION_SET_GRAPH|' + this.getMenuName(this.selectedEntity, this.securityCurrencyColumn),
          command: (event) => this.navigateToChartRoute()
        });
        if (Object.keys(this.traceShow).length > 0) {
          menuItems.push({
            label: 'CORRELATION_ADD_GRAPH|' + this.getMenuName(this.selectedEntity, this.securityCurrencyColumn),
            command: (event) => this.changeToOpenChart()
          });
        }
      }
    }
    this.selectedEntity && menuItems.push(...BusinessHelper.getUrlLinkMenus(this.selectedEntity));
    return menuItems;
  }

  private isGraphForPairShowing(id1: number, id2: number): boolean {
    return !!Object.keys(this.traceShow).find(key => {
      const ids: number[] = key.split(',').map(Number);
      return ids.indexOf(id1) >= 0 && ids.indexOf(id2) >= 0;
    });
  }

  private getMenuName(securityCurrencyRow: Securitycurrency, securityCurrencyColumn: Securitycurrency): string {
    return this.getTickerOrName(securityCurrencyRow) + '<->' + this.getTickerOrName(securityCurrencyColumn);
  }

  private getTickerOrName(securityCurrency: Securitycurrency): string {
    if (securityCurrency.hasOwnProperty(this.tickerSymbol)) {
      const ticker = (<Security>securityCurrency)[this.tickerSymbol];
      return ticker ? ticker : securityCurrency.name.slice(0, 20);
    }
    return securityCurrency.name;
  }

  addExistingSecurity(event) {
    this.tenantLimits[0].actual = this.securitycurrencyList.length;
    this.visibleAddInstrumentDialog = true;
  }

  handleCloseAddInstrumentDialog(processedActionData: ProcessedActionData) {
    this.visibleAddInstrumentDialog = false;
    this.childToParent.refreshData(null);
  }

  removeInstrumentFromCorrelationSet(idSecuritycurrency: number) {
    this.correlationSetService.removeInstrumentFromCorrelationSet(this.correlationSet.idCorrelationSet,
      idSecuritycurrency).subscribe(correlationSet => {
      this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'REMOVED_INSTRUMENT_FROM_CORRELATIONSET');
      this.selectedEntity = null;
      this.childToParent.refreshData(correlationSet);
    });
  }

  getCorrelation(dataobject: Securitycurrency, field: ColumnConfig, valueField: any): string | number {
    if (this.correlationResult) {
      const ci: CorrelationInstrument = this.correlationResult.correlationInstruments.find(cIn =>
        cIn.idSecuritycurrency === dataobject.idSecuritycurrency);
      return ci ? ci.correlations[field.userValue] : null;
    } else {
      return null;
    }
  }

  getMinMaxDate(dataobject: Securitycurrency, field: ColumnConfig, valueField: any): string | number {
    if (this.correlationResult) {
      const mmdh: MinMaxDateHistoryquote = this.correlationResult.mmdhList.find(mmdHist =>
        mmdHist.idSecuritycurrency === dataobject.idSecuritycurrency);
      return AppHelper.getValueByPathWithField(this.gps, this.translateService, mmdh, field, field.field);
    } else {
      return null;
    }
  }

  getBackgroundColor(dataobject: Securitycurrency, field: ColumnConfig): string {
    if (field.userValue != null) {
      const value: number = this.getCorrelation(dataobject, field, null) as number;
      return 'hsl(' + ((value + 1) * 58) + ',100%, 50%)';
    }
    return null;
  }

  ngOnDestroy(): void {
    this.subscriptionInstrumentAdded && this.subscriptionInstrumentAdded.unsubscribe();
  }

  private readListLimitOnce(correlationSet: CorrelationSet): void {
    if (!this.tenantLimits && correlationSet) {
      this.correlationSetService.getCorrelationSetInstrumentLimit(correlationSet.idCorrelationSet).subscribe(limit => {
        this.tenantLimits = [limit];
      });
    }
  }

  private createDynamicTableDefinition(correlationSet: CorrelationSet, correlationResult: CorrelationResult): void {
    this.removeAllColumns();
    this.correlationResult = correlationResult;
    this.addColumnFeqH(DataType.String, this.nameHeader, true, false,
      {width: 250, templateName: AppSettings.OWNER_TEMPLATE});
    this.addColumnFeqH(DataType.String, 'currency', true, false);

    if (correlationSet) {
      if (correlationResult) {
        if (correlationResult.mmdhList.length === 0) {
          this.addTickerAndCorrelationColumnDefinition(correlationSet);
        } else {
          this.addMinMaxColumnDefinition();
        }
      }
      this.securitycurrencyList = correlationSet.securitycurrencyList;
      this.correlationSet = correlationSet;
    } else {
      this.securitycurrencyList = [];
    }
    this.translateHeadersAndColumns();
  }

  private addTickerAndCorrelationColumnDefinition(correlationSet: CorrelationSet): void {
    this.addColumnFeqH(DataType.String, this.tickerSymbol, true, false);
    let i = 0;
    correlationSet.securitycurrencyList.forEach(sc => {
      let label = sc.hasOwnProperty(this.tickerSymbol) ? (<Security>sc)[this.tickerSymbol] : sc.name;
      if (!label) {
        label = '(' + i + ')';
        (<Security>sc).tickerSymbol = label;
      }
      this.addColumnFeqH(DataType.NumericShowZero, label,
        true, false, {fieldValueFN: this.getCorrelation.bind(this), userValue: i++});
    });
  }

  private addMinMaxColumnDefinition(): void {
    this.addColumnFeqH(DataType.DateString, this.minDate, true, false, {fieldValueFN: this.getMinMaxDate.bind(this)});
    this.addColumnFeqH(DataType.DateString, 'maxDate', true, false, {fieldValueFN: this.getMinMaxDate.bind(this)});
  }

  private addInstrumentsToCorrelationSet(): void {
    this.subscriptionInstrumentAdded = this.dataChangedService.dateChanged$.subscribe(processedActionData => {
      if (processedActionData.data.hasOwnProperty('idCorrelationSet')
        && processedActionData.action === ProcessedAction.UPDATED) {
        this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'ADDED_SECURITY_TO_WATCHLIST');
        this.childToParent.refreshData(processedActionData.data);
        this.tenantLimits[0].actual = this.securitycurrencyList.length;
      }
    });
  }

  public refreshChartWhenCorrelationSetChanges(): void {
    if (SamplingPeriodType[this.correlationSet.samplingPeriod] !== SamplingPeriodType.ANNUAL_RETURNS
      && this.isChartShow()) {
      const idsPairs: number[][] = [];
      Object.keys(this.traceShow).filter(key => {
        const ids: string[] = key.split(',');
        return this.correlationSet.securitycurrencyList.find(s => s.idSecuritycurrency === +ids[0])
          && this.correlationSet.securitycurrencyList.find(s => s.idSecuritycurrency === +ids[1]);
      }).forEach(key => {
        const ids: string[] = key.split(',');
        idsPairs.push([+ids[0], +ids[1]]);
      });
      this.traceShow = [];
      if (idsPairs.length > 0) {
        this.correlationSetService.getRollingCorrelations(this.correlationSet.idCorrelationSet, idsPairs)
          .subscribe((crs: CorrelationRollingResult[]) => this.prepareCharDataAndSentToChart(crs));
      }
    }
  }

  /**
   * Used when correlation chart is already show. Reset chart or add another correlation line
   */
  private changeToOpenChart(): void {
    this.isChartShow() && this.getAndSetRollingCorrelation();
  }

  /**
   * Open chart line or reset existing chart.
   */
  private navigateToChartRoute(): void {
    if (this.isChartShow()) {
      this.traceShow = {};
      this.getAndSetRollingCorrelation();
    } else {
      this.prepareChartDataWithRequest();
      this.router.navigate([AppSettings.MAINVIEW_KEY + '/', {
        outlets: {
          mainbottom: [AppSettings.CHART_GENERAL_PURPOSE, AppSettings.CORRELATION_CHART]
        }
      }]);
    }
  }

  private isChartShow(): boolean {
    return !!this.subscriptionRequestFromChart || this.chartDataService.isChartOfIdShown(AppSettings.CORRELATION_CHART);
  }

  /**
   * For the first call from the chart (callback)
   */
  private prepareChartDataWithRequest(): void {
    this.subscriptionRequestFromChart = this.chartDataService.requestFromChart$.subscribe(id => {
        if (id === AppSettings.CORRELATION_CHART) {
          this.getAndSetRollingCorrelation();
        }
      }
    );
  }

  /**
   * Add single line to chart. It gets the data from the backend.
   */
  private getAndSetRollingCorrelation(): void {
    this.correlationSetService.getRollingCorrelations(this.correlationSet.idCorrelationSet,
      [[this.securityCurrencyColumn.idSecuritycurrency, this.selectedEntity.idSecuritycurrency]])
      .subscribe((crs: CorrelationRollingResult[]) => this.prepareCharDataAndSentToChart(crs));
  }


  private prepareCharDataAndSentToChart(crsArray: CorrelationRollingResult[]): void {
    let minDate = moment();
    let maxDate = moment('2000-01-01');
    const legendTooltipMap = new Map<string, string>();
    crsArray.forEach(crs => {
      const traceName = this.getTickerOrName(crs.securitycurrencyList[0]) + '<->' + this.getTickerOrName(crs.securitycurrencyList[1]);
      const fullName = crs.securitycurrencyList[0].name + '<->' + crs.securitycurrencyList[1].name;
      legendTooltipMap.set(traceName, fullName);
      const trace = {
        type: 'scatter',
        mode: 'lines',
        name: traceName,
        x: crs.dates,
        y: crs.correlation
      };

      minDate = moment.min(minDate, moment(crs.dates[0]));
      maxDate = moment.max(maxDate, moment(crs.dates[crs.dates.length - 1]));
      this.traceShow[crs.securitycurrencyList[0].idSecuritycurrency + ',' + crs.securitycurrencyList[1].idSecuritycurrency] = trace;
    });

    const chartData = Object.values(this.traceShow);

    this.chartDataService.sentToChart({
      data: chartData,
      layout: this.getChartLayout(minDate.format(AppSettings.FORMAT_DATE_SHORT_NATIVE),
        maxDate.format(AppSettings.FORMAT_DATE_SHORT_NATIVE)),
      legendTooltipMap,
      options: {
        modeBarButtonsToRemove: ['hoverCompareCartesian', 'hoverClosestCartesian']
      },
    });
  }

  private getChartLayout(minDate: string, maxDate: string): any {
    const layout = {
      title: 'CORRELATION_ROLLING|' + this.childToParent.getPeriodAndRollingWithParamPrefix().join('|'),
      showlegend: true,
      legend: PlotlyHelper.getLegendUnderChart(11),
      xaxis: {
        autorange: true,
        range: [minDate, maxDate],
        rangeslider: {range: [minDate, maxDate]},
        type: 'date'
      },
      yaxis: {
        autorange: true,
        range: [-1, 1],
        type: 'linear'
      }
    };
    PlotlyHelper.translateLayout(this.translateService, layout);
    return layout;
  }

}

export interface ChildToParent {
  refreshData(correlationSet: CorrelationSet): void;

  getPeriodAndRollingWithParamPrefix(): string[];
}



