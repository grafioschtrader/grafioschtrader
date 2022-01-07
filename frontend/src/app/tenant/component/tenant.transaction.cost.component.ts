import {Component, ElementRef, OnDestroy, OnInit, ViewChild} from '@angular/core';
import {IGlobalMenuAttach} from '../../shared/mainmenubar/component/iglobal.menu.attach';
import {TableConfigBase} from '../../shared/datashowbase/table.config.base';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {ActivePanelService} from '../../shared/mainmenubar/service/active.panel.service';
import {PortfolioService} from '../../portfolio/service/portfolio.service';
import {ActivatedRoute, Router} from '@angular/router';
import {TranslateService} from '@ngx-translate/core';
import {UserSettingsService} from '../../shared/service/user.settings.service';
import {DataType} from '../../dynamic-form/models/data.type';
import {Subscription} from 'rxjs';
import {TransactionCostGrandSummary} from '../../entities/view/transactioncost/transaction.cost.grand.summary';
import {TransactionCostGroupSummary} from '../../entities/view/transactioncost/transaction.cost.group.summary';
import {ColumnConfig, ColumnGroupConfig} from '../../shared/datashowbase/column.config';
import {ProcessedActionData} from '../../shared/types/processed.action.data';

import {AppSettings} from '../../shared/app.settings';
import {ChartDataService} from '../../shared/chart/service/chart.data.service';
import {ChartTrace, PlotlyHelper} from '../../shared/chart/plotly.helper';
import {TransactionCostPosition} from '../../entities/view/transactioncost/transaction.cost.position';
import {HelpIds} from '../../shared/help/help.ids';
import {TranslateHelper} from '../../shared/helper/translate.helper';
import {FilterService, MenuItem} from 'primeng/api';


/**
 * Report of transaction cost and transaction tax.
 */
@Component({
  template: `
    <div class="data-container" (click)="onComponentClick($event)"
         [ngClass]="{'active-border': isActivated(), 'passiv-border': !isActivated()}">
      <div class="datatable">

        <p-table #dataTable [columns]="fields" [value]="transactionCostGroupSummaries" selectionMode="single"
                 [(selection)]="selectedRow" dataKey="securityaccount.idSecuritycashAccount"
                 [expandedRowKeys]="expandedTCGSid" sortMode="multiple" [multiSortMeta]="multiSortMeta"
                 responsiveLayout="scroll"
                 styleClass="sticky-table p-datatable-striped p-datatable-gridlines">
          <ng-template pTemplate="header" let-fields>
            <tr>
              <th style="width:24px"></th>
              <th *ngFor="let field of fields" [pSortableColumn]="field.field" [style.max-width.px]="field.width"
                  [ngStyle]="field.width? {'flex-basis': '0 0 ' + field.width + 'px'}: {}">
                {{field.headerTranslated}}
                <p-sortIcon [field]="field.field"></p-sortIcon>
              </th>
            </tr>
          </ng-template>

          <ng-template pTemplate="body" let-el let-expanded="expanded" let-columns="fields">
            <tr [pSelectableRow]="el">
              <td style="width:24px">
                <a href="#" [pRowToggler]="el">
                  <i [ngClass]="expanded ? 'fa fa-fw fa-chevron-circle-down' : 'fa fa-fw fa-chevron-circle-right'"></i>
                </a>
              </td>
              <ng-container *ngFor="let field of fields">
                <td *ngIf="field.visible" [style.max-width.px]="field.width"
                    [ngStyle]="field.width? {'flex-basis': '0 0 ' + field.width + 'px'}: {}"
                    [ngClass]="(field.dataType===DataType.Numeric || field.dataType===DataType.DateTimeNumeric
                     || field.dataType===DataType.NumericInteger)? 'text-right': ''">
                  {{getValueByPath(el, field)}}
                </td>
              </ng-container>
            </tr>
          </ng-template>

          <ng-template pTemplate="footer">
            <tr>
              <td></td>
              <ng-container *ngFor="let field of fields">
                <td *ngIf="field.visible" class="row-total" [style.width.px]="field.width"
                    [ngClass]="(field.dataType===DataType.Numeric || field.dataType===DataType.DateTimeNumeric
                || field.dataType===DataType.NumericInteger)? 'text-right': ''">
                  {{getValueColumnTotal(field, 0, transactionCostGrandSummary, null)}}
                </td>
              </ng-container>
            </tr>
          </ng-template>

          <ng-template pTemplate="rowexpansion" let-tcgs let-columns="fields">
            <tr>
              <td [attr.colspan]="numberOfVisibleColumns + 1">
                <tenant-transaction-cost-extended
                  [transactionCostPositions]="tcgs.transactionCostPositions"
                  [transactionCostGrandSummary]="transactionCostGrandSummary"
                  [baseSelectedRow]="baseSelectedRow"
                  [firstRowIndex]="firstRowIndex"
                  (dateChanged)="transactionDataChanged($event)">>
                </tenant-transaction-cost-extended>
              </td>
            </tr>
          </ng-template>
        </p-table>
      </div>
    </div>
  `
})
export class TenantTransactionCostComponent extends TableConfigBase implements IGlobalMenuAttach, OnInit, OnDestroy {
  @ViewChild('dataTable') datatable: ElementRef;

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
              usersettingsService: UserSettingsService) {
    super(filterService, usersettingsService, translateService, gps);
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


  getMenuShowOptions(): MenuItem[] {
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

  public getHelpContextId(): HelpIds {
    return HelpIds.HELP_PORTFOLIOS_TRANSACTIONCOSTS;
  }

  ngOnDestroy(): void {
    this.activePanelService.destroyPanel(this);
  }

  private navigateToChartRoute() {
    !this.subscriptionRequestFromChart && this.prepareChartDataWithRequest();
    this.router.navigate([AppSettings.MAINVIEW_KEY + '/', {
      outlets: {
        mainbottom: [AppSettings.CHART_GENERAL_PURPOSE, AppSettings.TRANSACTION_COST_KEY]
      }
    }]);
  }

  private prepareChartDataWithRequest(): void {
    if (!this.subscriptionRequestFromChart) {
      this.subscriptionRequestFromChart = this.chartDataService.requestFromChart$.subscribe(id => {
          if (this.datatable && id === AppSettings.TRANSACTION_COST_KEY) {
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
