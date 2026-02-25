import {Component, Injector, OnDestroy, OnInit} from '@angular/core';
import {ActivatedRoute, Router} from '@angular/router';
import {TranslateService} from '@ngx-translate/core';
import {FilterService, MenuItem} from 'primeng/api';
import {ConfigurableTableComponent} from '../../lib/datashowbase/configurable-table.component';
import {Subscription} from 'rxjs';
import {TransactionCostGrandSummary} from '../../entities/view/transactioncost/transaction.cost.grand.summary';
import {TransactionCostGroupSummary} from '../../entities/view/transactioncost/transaction.cost.group.summary';
import {TransactionCostPosition} from '../../entities/view/transactioncost/transaction.cost.position';
import {BaseSettings} from '../../lib/base.settings';
import {ColumnConfig, ColumnGroupConfig} from '../../lib/datashowbase/column.config';
import {TableConfigBase} from '../../lib/datashowbase/table.config.base';
import {DataType} from '../../lib/dynamic-form/models/data.type';
import {TranslateHelper} from '../../lib/helper/translate.helper';
import {HelpIds} from '../../lib/help/help.ids';
import {IGlobalMenuAttach} from '../../lib/mainmenubar/component/iglobal.menu.attach';
import {ActivePanelService} from '../../lib/mainmenubar/service/active.panel.service';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {UserSettingsService} from '../../lib/services/user.settings.service';
import {ProcessedActionData} from '../../lib/types/processed.action.data';
import {PortfolioService} from '../../portfolio/service/portfolio.service';
import {AppSettings} from '../../shared/app.settings';
import {ChartTrace, PlotlyHelper} from '../../shared/chart/plotly.helper';
import {ChartDataService} from '../../shared/chart/service/chart.data.service';
import {TenantTransactionCostExtendedComponent} from './tenant-transaction-cost-extended.component';


/**
 * Report of transaction cost and transaction tax.
 */
@Component({
  template: `
    <configurable-table
      [data]="transactionCostGroupSummaries"
      [fields]="fields"
      dataKey="securityaccount.idSecuritycashAccount"
      [(selection)]="selectedRow"
      [expandable]="true"
      [expandedRowTemplate]="expandedContent"
      [expandedRowKeys]="expandedTCGSid"
      [multiSortMeta]="multiSortMeta"
      [valueGetterFn]="getValueByPath.bind(this)"
      [customSortFn]="customSort.bind(this)"
      [footerValueFn]="getFooterValue"
      footerCellClass="row-total"
      [containerClass]="{'data-container': true, 'active-border': isActivated(), 'passiv-border': !isActivated()}"
      customClass="datatable"
      (componentClick)="onComponentClick($event)">
    </configurable-table>
    <ng-template #expandedContent let-tcgs>
      <tenant-transaction-cost-extended
        [transactionCostPositions]="tcgs.transactionCostPositions"
        [transactionCostGrandSummary]="transactionCostGrandSummary"
        [baseSelectedRow]="baseSelectedRow"
        [firstRowIndex]="firstRowIndex"
        (dateChanged)="transactionDataChanged($event)">
      </tenant-transaction-cost-extended>
    </ng-template>
  `,
  standalone: true,
  imports: [ConfigurableTableComponent, TenantTransactionCostExtendedComponent]
})
export class TenantTransactionCostComponent extends TableConfigBase implements IGlobalMenuAttach, OnInit, OnDestroy {
  /**
   * Used to reference chart trace by the id of the security account. It is used when the
   * users clicks a data point in the chart to select the main table row
   */
  readonly refId = 'refId';

  chartData: Partial<ChartTrace>[] = [];

  selectedRow: TransactionCostGroupSummary;

  transactionCostGrandSummary: TransactionCostGrandSummary;
  transactionCostGroupSummaries: TransactionCostGroupSummary[];
  expandedTCGSid: { [s: string]: boolean } = {};
  baseSelectedRow: TransactionCostPosition;
  firstRowIndex = 0;

  getFooterValue = (field: ColumnConfig): string => {
    return this.getValueColumnTotal(field, 0, this.transactionCostGrandSummary, null);
  };

  private chartIdNumberToTransactionCostPosition: TransactionCostPosition[];
  private columnConfigs: ColumnConfig[] = [];

  private subscriptionRequestFromChart: Subscription;

  constructor(private portfolioService: PortfolioService,
    private activatedRoute: ActivatedRoute,
    private activePanelService: ActivePanelService,
    private router: Router,
    private chartDataService: ChartDataService,
    filterService: FilterService,
    translateService: TranslateService,
    gps: GlobalparameterService,
    usersettingsService: UserSettingsService,
    injector: Injector) {
    super(filterService, usersettingsService, translateService, gps, injector);
  }

  ngOnInit(): void {
    this.addColumn(DataType.String, 'securityaccount.name', 'NAME', true, false,
      {columnGroupConfigs: [new ColumnGroupConfig(null, 'GRAND_TOTAL')]});
    this.columnConfigs.push(this.addColumn(DataType.Numeric, 'groupTotalTaxCostMc', 'TAX_COST', true, false,
      {columnGroupConfigs: [new ColumnGroupConfig('grandTotalTaxCostMC')]}));

    this.addColumn(DataType.NumericInteger, 'groupCountPaidTransaction', 'PAID_TRANSACTIONS', true, false,
      {columnGroupConfigs: [new ColumnGroupConfig('grandCountPaidTransaction')]});
    this.columnConfigs.push(this.addColumn(DataType.Numeric, 'groupTotalAverageTransactionCostMC', 'TRANSACTION_AVERAGE_PAID', true, false,
      {columnGroupConfigs: [new ColumnGroupConfig('grandTotalAverageTransactionCostMC')]}));
    this.columnConfigs.push(this.addColumn(DataType.Numeric, 'groupTotalTransactionCostMC', 'TRANSACTION_COST', true, false,
      {columnGroupConfigs: [new ColumnGroupConfig('grandTotalTransactionCostMC')]}));

    this.multiSortMeta.push({field: 'securityaccount.name', order: 1});
    this.readData();
    this.onComponentClick(null);
  }

  readData() {
    this.portfolioService.getTransactionCostGrandSummaryByTenant().subscribe((data: TransactionCostGrandSummary) => {
      this.transactionCostGrandSummary = data;
      this.transactionCostGroupSummaries = this.transactionCostGrandSummary.transactionCostGroupSummaries;
      this.columnConfigs.forEach(columnConfig => columnConfig.headerSuffix = this.transactionCostGrandSummary.mainCurrency);
      this.prepareTableAndTranslate();

      const urlPattern = new RegExp(`.*\/\/${AppSettings.MAIN_BOTTOM}.*\/${AppSettings.TRANSACTION_COST_KEY}\\\)`);
      urlPattern.test(this.router.url) && this.prepareCharDataAndSentToChart();
    });
  }

  override getMenuShowOptions(): MenuItem[] {
    const otherMenuShowOptions: MenuItem[] = super.getMenuShowOptions();
    const menuItems: MenuItem[] = [];
    if (otherMenuShowOptions) {
      menuItems.push(...otherMenuShowOptions);
    }

    menuItems.push({
      label: 'SHOW_CHART',
      command: (event) => this.navigateToChartRoute()
    });

    TranslateHelper.translateMenuItems(menuItems, this.translateService);
    return menuItems;
  }

  chartDataPointClicked(traceIndex: number, dataPointIndex: number) {
    const tcgs = this.transactionCostGrandSummary.transactionCostGroupSummaries.find(
      tcgsTemp => tcgsTemp.securityaccount.idSecuritycashAccount === this.chartData[this.refId][traceIndex]);
    this.expandedTCGSid[tcgs.securityaccount.idSecuritycashAccount] = true;
    setTimeout(() => this.selectRowAndGoToPage(traceIndex, dataPointIndex, tcgs));
  }

  isActivated(): boolean {
    return this.activePanelService.isActivated(this);
  }

  hideContextMenu(): void {
  }

  callMeDeactivate(): void {
  }

  onComponentClick(event): void {
    if (event == null || !event[this.consumedGT]) {
      this.activePanelService.activatePanel(this, {
        showMenu: this.getMenuShowOptions()
      });
    }
  }

  transactionDataChanged(processedActionData: ProcessedActionData) {
    this.readData();
  }

  public getHelpContextId(): string {
    return HelpIds.HELP_PORTFOLIOS_TRANSACTIONCOSTS;
  }

  ngOnDestroy(): void {
    this.activePanelService.destroyPanel(this);
  }

  private navigateToChartRoute() {
    !this.subscriptionRequestFromChart && this.prepareChartDataWithRequest();
    this.router.navigate([BaseSettings.MAINVIEW_KEY + '/', {
      outlets: {
        mainbottom: [AppSettings.CHART_GENERAL_PURPOSE, AppSettings.TRANSACTION_COST_KEY]
      }
    }]);
  }

  private prepareChartDataWithRequest(): void {
    if (!this.subscriptionRequestFromChart) {
      this.subscriptionRequestFromChart = this.chartDataService.requestFromChart$.subscribe(id => {
          if (this.transactionCostGroupSummaries && id === AppSettings.TRANSACTION_COST_KEY) {
            this.prepareCharDataAndSentToChart();
          }
        }
      );
    }
  }

  private prepareCharDataAndSentToChart(): void {
    this.chartData[this.refId] = [];
    this.chartIdNumberToTransactionCostPosition = [];
    this.transactionCostGrandSummary.transactionCostGroupSummaries.forEach(tcgs => {
      const traceSecurityaccount: Partial<ChartTrace> = PlotlyHelper.initializeChartTrace(tcgs.securityaccount.name,
        'scatter', 'markers');
      traceSecurityaccount.visible = 'legendonly';
      this.chartData.push(traceSecurityaccount);
      this.chartData[this.refId].push(tcgs.securityaccount.idSecuritycashAccount);

      traceSecurityaccount[this.refId] = [];
      tcgs.transactionCostPositions.forEach(tcp => {
        traceSecurityaccount.x.push(tcp.basePriceForTransactionCostMC);
        traceSecurityaccount.y.push(tcp.transactionCostMC);
        traceSecurityaccount[this.refId].push(tcp.transaction.idTransaction);
        this.chartIdNumberToTransactionCostPosition.push(tcp);
      });
    });

    this.chartDataService.sentToChart({
      data: this.chartData, layout: {hovermode: 'closest'},
      options: {
        modeBarButtonsToRemove: ['hoverCompareCartesian', 'hoverClosestCartesian']
      },
      callBackFN: this.chartDataPointClicked.bind(this)
    });

  }

  private selectRowAndGoToPage(traceIndex: number, dataPointIndex: number, tcgs: TransactionCostGroupSummary): void {
    const idTransaction = this.chartData[traceIndex][this.refId][dataPointIndex];
    let rowIndex = 0;
    for (let i = 0; i < tcgs.transactionCostPositions.length; i++) {
      if (tcgs.transactionCostPositions[i].transaction.idTransaction === idTransaction) {
        rowIndex = i;
        this.baseSelectedRow = tcgs.transactionCostPositions[i];
      }
    }
    this.firstRowIndex = rowIndex;
  }
}
