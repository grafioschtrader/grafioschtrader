import {Component, Input, OnDestroy} from '@angular/core';
import {TableConfigBase} from '../../lib/datashowbase/table.config.base';
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
import {DataType} from '../../lib/dynamic-form/models/data.type';
import {AppSettings} from '../../shared/app.settings';
import {ColumnConfig} from '../../lib/datashowbase/column.config';
import {Securitycurrency} from '../../entities/securitycurrency';
import {Security} from '../../entities/security';
import {InfoLevelType} from '../../lib/message/info.leve.type';
import {ProcessedActionData} from '../../lib/types/processed.action.data';
import {CorrelationSetService} from '../service/correlation.set.service';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {DataChangedService} from '../../shared/maintree/service/data.changed.service';
import {TenantLimit} from '../../entities/backend/tenant.limit';
import {ProcessedAction} from '../../lib/types/processed.action';
import {Subscription} from 'rxjs';
import {ChartDataService} from '../../shared/chart/service/chart.data.service';
import {Router} from '@angular/router';
import {PlotlyHelper} from '../../shared/chart/plotly.helper';
import moment from 'moment';
import {BusinessHelper} from '../../shared/helper/business.helper';
import {AppHelper} from '../../lib/helper/app.helper';
import {BaseSettings} from '../../lib/base.settings';

/**
 * This component serves as the main table view for correlation sets, displaying securities and currency pairs
 * in a dynamic table format with correlation values. It provides extensive functionality for:
 *
 * **Core Features:**
 * - Dynamic table generation with correlation matrix visualization
 * - Interactive instrument management (add/remove securities and currency pairs)
 * - Correlation calculation display with color-coded background
 * - Rolling correlation chart integration with Plotly visualization
 * - Context menu operations for instrument and chart management
 *
 * **Table Functionality:**
 * - Expandable rows showing detailed instrument statistics
 * - Dynamic column creation based on correlation set contents
 * - Custom cell background coloring based on correlation values (-1 to +1 range)
 * - Support for both correlation matrix view and min/max date display
 *
 * **Chart Integration:**
 * - Creates interactive line charts for rolling correlation analysis
 * - Supports multiple correlation pairs on the same chart
 * - Chart navigation and data management through router outlets
 * - Real-time chart updates when correlation set changes
 *
 * **User Interactions:**
 * - Click-to-select instruments for correlation pair analysis
 * - Context menu with add/remove/chart operations
 * - Dialog-based instrument addition with tenant limit validation
 * - Responsive design with PrimeNG table components
 *
 * The component extends TableConfigBase to leverage common table functionality including sorting,
 * filtering, column management, and internationalization support.
 */
@Component({
  selector: 'correlation-table',
  template: `
    <p-table [columns]="fields" [value]="securitycurrencyList" selectionMode="single"
             [(selection)]="selectedEntity" dataKey="idSecuritycurrency"
             (sortFunction)="customSort($event)" [customSort]="true"
             stripedRows showGridlines>
      <ng-template #header let-fields>
        <tr>
          <th style="width:24px"></th>
          @for (field of fields; track field.field) {
            <th [pSortableColumn]="field.field" [pTooltip]="field.headerTooltipTranslated"
                [style.max-width.px]="field.width"
                [ngStyle]="field.width? {'flex-basis': '0 0 ' + field.width + 'px'}: {}">
              {{ field.headerTranslated }}
              <p-sortIcon [field]="field.field"></p-sortIcon>
            </th>
          }
        </tr>
      </ng-template>
      <ng-template #body let-expanded="expanded" let-el let-columns="fields">
        <tr [pSelectableRow]="el">
          <td>
            <a href="#" [pRowToggler]="el">
              <i [ngClass]="expanded ? 'fa fa-fw fa-chevron-circle-down' : 'fa fa-fw fa-chevron-circle-right'"></i>
            </a>
          </td>
          @for (field of fields; track field.field) {
            <td [style.background-color]="getBackgroundColor(el, field)"
                [ngClass]="field.dataType===DataType.NumericShowZero ? 'text-right': ''"
                (click)="cellClick(field)"
                [style.max-width.px]="field.width"
                [ngStyle]="field.width? {'flex-basis': '0 0 ' + field.width + 'px'}: {}">
              @switch (field.templateName) {
                @case ('check') {
                  <span><i [ngClass]="{'fa fa-check': getValueByPath(el, field)}" aria-hidden="true"></i></span>
                }
                @default {
                  {{ getValueByPath(el, field) }}
                }
              }
            </td>
          }
        </tr>
      </ng-template>
      <ng-template #expandedrow let-securitycrrency let-columns="fields">
        <tr>
          <td [attr.colspan]="numberOfVisibleColumns + 1" style="overflow:visible;">
            <instrument-statistics-result [idSecuritycurrency]="securitycrrency.idSecuritycurrency"
                                          [dateFrom]="correlationSet.dateFrom" [dateTo]="correlationSet.dateTo">
            </instrument-statistics-result>
          </td>
        </tr>
      </ng-template>
    </p-table>
    @if (visibleAddInstrumentDialog) {
      <correlation-add-instrument [idCorrelationSet]="correlationSet.idCorrelationSet"
                                  [tenantLimits]="tenantLimits"
                                  [visibleAddInstrumentDialog]="visibleAddInstrumentDialog"
                                  (closeDialog)="handleCloseAddInstrumentDialog($event)">
      </correlation-add-instrument>
    }
  `,
  standalone: false
})
export class CorrelationTableComponent extends TableConfigBase implements OnDestroy {
  /** Interface for communication with parent component */
  @Input() childToParent: ChildToParent;

  /** Limit instruments per correlation set */
  tenantLimits: TenantLimit[];

  /** List of securities and currencies in the correlation set */
  securitycurrencyList: Securitycurrency[];

  /** Currently selected entity in the table */
  selectedEntity: Securitycurrency;

  /** Controls visibility of the add instrument dialog */
  visibleAddInstrumentDialog: boolean;

  /** Current correlation set being displayed */
  correlationSet: CorrelationSet;

  /** Header key for name column */
  private readonly nameHeader = 'name';

  /** Header key for ticker symbol column */
  private readonly tickerSymbol = 'tickerSymbol';

  /** Header key for minimum date column */
  private readonly minDate = 'minDate';

  /** Current correlation calculation results */
  private correlationResult: CorrelationResult;

  /** Subscription for instrument addition events */
  private subscriptionInstrumentAdded: Subscription;

  /** Security currency selected in column header */
  private securityCurrencyColumn: Securitycurrency;

  /** Subscription for chart data requests */
  private subscriptionRequestFromChart: Subscription;

  /** Map of chart traces currently being displayed */
  private traceShow: { [name: string]: any } = {};

  /**
   * Creates an instance of CorrelationTableComponent.
   * @param router Angular router for navigation
   * @param correlationSetService Service for correlation set operations
   * @param messageToastService Service for displaying toast messages
   * @param dataChangedService Service for data change notifications
   * @param chartDataService Service for chart data management
   * @param filterService PrimeNG filter service
   * @param translateService Service for internationalization
   * @param gps Global parameter service
   * @param usersettingsService Service for user settings persistence
   */
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

  /**
   * Handles cell click events to select column security currency.
   * @param field The column configuration that was clicked
   */
  cellClick(field: ColumnConfig): void {
    const columnHeader = field.headerKey === this.nameHeader
    || field.headerKey === this.tickerSymbol ? null : field.headerKey;

    this.securityCurrencyColumn = this.correlationSet.securitycurrencyList.find(sc => (sc.hasOwnProperty(this.tickerSymbol)
      ? (<Security>sc)[this.tickerSymbol] : sc.name) === columnHeader);
  }

  /**
   * Called from the parent component when correlation set or results change.
   * @param correlationSet The selected correlation set
   * @param correlationResult The correlation calculation results
   */
  parentSelectionChanged(correlationSet: CorrelationSet, correlationResult: CorrelationResult): void {
    this.createDynamicTableDefinition(correlationSet, correlationResult);
    this.readListLimitOnce(correlationSet);
  }

  /**
   * Prepares the edit menu items for the correlation table.
   * @returns Array of menu items for editing operations
   */
  public prepareEditMenu(): MenuItem[] {
    const menuItems: MenuItem[] = [];
    menuItems.push({separator: true});
    menuItems.push(
      {
        label: 'ADD_EXISTING_SECURITY' + BaseSettings.DIALOG_MENU_SUFFIX, command: (e) => this.addExistingSecurity(e),
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

  /**
   * Prepares the show menu items for correlation visualization.
   * @returns Array of menu items for show operations
   */
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

  /**
   * Checks if a graph is already showing for the given security pair.
   * @param id1 First security ID
   * @param id2 Second security ID
   * @returns True if graph is already showing for this pair
   */
  private isGraphForPairShowing(id1: number, id2: number): boolean {
    return !!Object.keys(this.traceShow).find(key => {
      const ids: number[] = key.split(',').map(Number);
      return ids.indexOf(id1) >= 0 && ids.indexOf(id2) >= 0;
    });
  }

  /**
   * Creates a display name for the correlation menu between two securities.
   * @param securityCurrencyRow The row security currency
   * @param securityCurrencyColumn The column security currency
   * @returns Formatted menu name
   */
  private getMenuName(securityCurrencyRow: Securitycurrency, securityCurrencyColumn: Securitycurrency): string {
    return this.getTickerOrName(securityCurrencyRow) + '<->' + this.getTickerOrName(securityCurrencyColumn);
  }

  /**
   * Gets ticker symbol or name for display purposes.
   * @param securityCurrency The security currency to get display name for
   * @returns Ticker symbol or truncated name
   */
  private getTickerOrName(securityCurrency: Securitycurrency): string {
    if (securityCurrency.hasOwnProperty(this.tickerSymbol)) {
      const ticker = (<Security>securityCurrency)[this.tickerSymbol];
      return ticker ? ticker : securityCurrency.name.slice(0, 20);
    }
    return securityCurrency.name;
  }

  /**
   * Opens the add existing security dialog.
   * @param event The triggering event
   */
  addExistingSecurity(event) {
    this.tenantLimits[0].actual = this.securitycurrencyList.length;
    this.visibleAddInstrumentDialog = true;
  }

  /**
   * Handles the close event of the add instrument dialog.
   * @param processedActionData The action data from the dialog
   */
  handleCloseAddInstrumentDialog(processedActionData: ProcessedActionData) {
    this.visibleAddInstrumentDialog = false;
    this.childToParent.refreshData(null);
  }

  /**
   * Removes an instrument from the correlation set.
   * @param idSecuritycurrency The ID of the security currency to remove
   */
  removeInstrumentFromCorrelationSet(idSecuritycurrency: number) {
    this.correlationSetService.removeInstrumentFromCorrelationSet(this.correlationSet.idCorrelationSet,
      idSecuritycurrency).subscribe(correlationSet => {
      this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'REMOVED_INSTRUMENT_FROM_CORRELATIONSET');
      this.selectedEntity = null;
      this.childToParent.refreshData(correlationSet);
    });
  }

  /**
   * Gets the correlation value for a specific security and field.
   * @param dataobject The security currency object
   * @param field The column configuration
   * @param valueField The value field (unused)
   * @returns The correlation value or null
   */
  getCorrelation(dataobject: Securitycurrency, field: ColumnConfig, valueField: any): string | number {
    if (this.correlationResult) {
      const ci: CorrelationInstrument = this.correlationResult.correlationInstruments.find(cIn =>
        cIn.idSecuritycurrency === dataobject.idSecuritycurrency);
      return ci ? ci.correlations[field.userValue] : null;
    } else {
      return null;
    }
  }

  /**
   * Gets the min/max date for a specific security.
   * @param dataobject The security currency object
   * @param field The column configuration
   * @param valueField The value field (unused)
   * @returns The formatted date or null
   */
  getMinMaxDate(dataobject: Securitycurrency, field: ColumnConfig, valueField: any): string | number {
    if (this.correlationResult) {
      const mmdh: MinMaxDateHistoryquote = this.correlationResult.mmdhList.find(mmdHist =>
        mmdHist.idSecuritycurrency === dataobject.idSecuritycurrency);
      return AppHelper.getValueByPathWithField(this.gps, this.translateService, mmdh, field, field.field);
    } else {
      return null;
    }
  }

  /**
   * Gets the background color based on correlation value.
   * @param dataobject The security currency object
   * @param field The column configuration
   * @returns HSL color string or null
   */
  getBackgroundColor(dataobject: Securitycurrency, field: ColumnConfig): string {
    if (field.userValue != null) {
      const value: number = this.getCorrelation(dataobject, field, null) as number;
      return 'hsl(' + ((value + 1) * 58) + ',100%, 50%)';
    }
    return null;
  }

  /** Component cleanup */
  ngOnDestroy(): void {
    this.subscriptionInstrumentAdded && this.subscriptionInstrumentAdded.unsubscribe();
  }

  /**
   * Reads tenant limits once when correlation set is available.
   * @param correlationSet The correlation set to get limits for
   */
  private readListLimitOnce(correlationSet: CorrelationSet): void {
    if (!this.tenantLimits && correlationSet) {
      this.correlationSetService.getCorrelationSetInstrumentLimit(correlationSet.idCorrelationSet).subscribe(limit => {
        this.tenantLimits = [limit];
      });
    }
  }

  /**
   * Creates dynamic table definition based on correlation set and results.
   * @param correlationSet The correlation set to display
   * @param correlationResult The correlation calculation results
   */
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

  /**
   * Adds ticker and correlation columns to the table.
   * @param correlationSet The correlation set containing securities
   */
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

  /** Adds min/max date columns to the table */
  private addMinMaxColumnDefinition(): void {
    this.addColumnFeqH(DataType.DateString, this.minDate, true, false, {fieldValueFN: this.getMinMaxDate.bind(this)});
    this.addColumnFeqH(DataType.DateString, 'maxDate', true, false, {fieldValueFN: this.getMinMaxDate.bind(this)});
  }

  /** Sets up subscription for instrument addition events */
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

  /** Refreshes chart when correlation set changes */
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

  /** Used when correlation chart is already show. Reset chart or add another correlation line */
  private changeToOpenChart(): void {
    this.isChartShow() && this.getAndSetRollingCorrelation();
  }

  /** Open chart line or reset existing chart */
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

  /**
   * Checks if correlation chart is currently showing.
   * @returns True if chart is visible
   */
  private isChartShow(): boolean {
    return !!this.subscriptionRequestFromChart || this.chartDataService.isChartOfIdShown(AppSettings.CORRELATION_CHART);
  }

  /** For the first call from the chart (callback) */
  private prepareChartDataWithRequest(): void {
    this.subscriptionRequestFromChart = this.chartDataService.requestFromChart$.subscribe(id => {
        if (id === AppSettings.CORRELATION_CHART) {
          this.getAndSetRollingCorrelation();
        }
      }
    );
  }

  /** Add single line to chart. It gets the data from the backend */
  private getAndSetRollingCorrelation(): void {
    this.correlationSetService.getRollingCorrelations(this.correlationSet.idCorrelationSet,
      [[this.securityCurrencyColumn.idSecuritycurrency, this.selectedEntity.idSecuritycurrency]])
      .subscribe((crs: CorrelationRollingResult[]) => this.prepareCharDataAndSentToChart(crs));
  }


  /**
   * Prepares chart data and sends it to the chart service.
   * @param crsArray Array of correlation rolling results
   */
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
      layout: this.getChartLayout(minDate.format(BaseSettings.FORMAT_DATE_SHORT_NATIVE),
        maxDate.format(BaseSettings.FORMAT_DATE_SHORT_NATIVE)),
      legendTooltipMap,
      options: {
        modeBarButtonsToRemove: ['hoverCompareCartesian', 'hoverClosestCartesian']
      },
    });
  }

  /**
   * Creates the chart layout configuration.
   * @param minDate Minimum date for chart range
   * @param maxDate Maximum date for chart range
   * @returns Chart layout configuration object
   */
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

/**
 * Interface for communication between child correlation table component and parent component.
 */
export interface ChildToParent {
  /**
   * Refreshes data in the parent component.
   * @param correlationSet The updated correlation set or null
   */
  refreshData(correlationSet: CorrelationSet): void;

  /**
   * Gets period and rolling parameters with prefix for display.
   * @returns Array of formatted parameter strings
   */
  getPeriodAndRollingWithParamPrefix(): string[];
}



