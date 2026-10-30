import type { PanelClickEvent } from '../../lib/mainmenubar/component/iglobal.menu.attach';
import { afterNextRender, computed, DestroyRef, inject, signal, Component, OnDestroy, OnInit } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { MenuItem } from '@openng/optimus-ui/api';
import { ConfigurableTableComponent } from '../../lib/datashowbase/configurable-table.component';
import { Subscription } from 'rxjs';
import { TransactionCostGrandSummary } from '../../entities/view/transactioncost/transaction.cost.grand.summary';
import { TransactionCostGroupSummary } from '../../entities/view/transactioncost/transaction.cost.group.summary';
import { TransactionCostPosition } from '../../entities/view/transactioncost/transaction.cost.position';
import { BaseSettings } from '../../lib/base.settings';
import { ColumnConfig, ColumnGroupConfig } from '../../lib/datashowbase/column.config';
import { TableConfigBase } from '../../lib/datashowbase/table.config.base';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { TranslateHelper } from '../../lib/helper/translate.helper';
import { HelpIds } from '../../lib/help/help.ids';
import { IGlobalMenuAttach } from '../../lib/mainmenubar/component/iglobal.menu.attach';
import { ActivePanelService } from '../../lib/mainmenubar/service/active.panel.service';
import { ProcessedActionData } from '../../lib/types/processed.action.data';
import { PortfolioService } from '../../portfolio/service/portfolio.service';
import { AppSettings } from '../../shared/app.settings';
import { PlotlyHelper } from '../../shared/chart/plotly.helper';
import { ChartDataService } from '../../shared/chart/service/chart.data.service';
import { TenantTransactionCostExtendedComponent } from './tenant-transaction-cost-extended.component';

/**
 * Report of transaction cost and transaction tax.
 */
@Component({
  template: `
    <configurable-table
      [data]="transactionCostGroupSummaries()"
      [fields]="fields"
      [fieldsRevision]="fieldsRevision()"
      dataKey="securityaccount.idSecuritycashAccount"
      [(selection)]="selectedRow"
      [expandable]="true"
      [expandedRowTemplate]="expandedContent"
      [expandedRowKeys]="expandedTCGSid()"
      [multiSortMeta]="multiSortMeta"
      [valueGetterFn]="valueGetter"
      [customSortFn]="customSorter"
      [footerValueFn]="getFooterValue"
      footerCellClass="row-total"
      [containerClass]="{
        'data-container': true,
        'active-border': isActivated(),
        'passiv-border': !isActivated()
      }"
      customClass="datatable"
      (componentClick)="onComponentClick($event)" />
    <ng-template #expandedContent let-tcgs>
      @if (transactionCostGrandSummary(); as summary) {
        <tenant-transaction-cost-extended
          [transactionCostPositions]="tcgs.transactionCostPositions"
          [transactionCostGrandSummary]="summary"
          [baseSelectedRow]="baseSelectedRow()"
          [firstRowIndex]="firstRowIndex()"
          (dateChanged)="transactionDataChanged($event)" />
      }
    </ng-template>
  `,
  imports: [ConfigurableTableComponent, TenantTransactionCostExtendedComponent]
})
export class TenantTransactionCostComponent extends TableConfigBase implements IGlobalMenuAttach, OnInit, OnDestroy {
  private portfolioService: PortfolioService = inject(PortfolioService);
  private activatedRoute: ActivatedRoute = inject(ActivatedRoute);
  private activePanelService: ActivePanelService = inject(ActivePanelService);
  private router: Router = inject(Router);
  private chartDataService: ChartDataService = inject(ChartDataService);

  /**
   * Used to reference chart trace by the id of the security account. It is used when the
   * users clicks a data point in the chart to select the main table row
   */
  readonly refId = 'refId';

  chartData: (ReturnType<typeof PlotlyHelper.initializeChartTrace> & { refId: (number | undefined)[] })[] = [];
  private chartAccountIds: (number | null | undefined)[] = [];

  selectedRow: TransactionCostGroupSummary;

  readonly transactionCostGrandSummary = signal<TransactionCostGrandSummary | null>(null);
  readonly transactionCostGroupSummaries = computed(
    () => this.transactionCostGrandSummary()?.transactionCostGroupSummaries ?? []
  );
  readonly expandedTCGSid = signal<{ [s: string]: boolean }>({});
  readonly baseSelectedRow = signal<TransactionCostPosition | null>(null);
  readonly firstRowIndex = signal(0);

  readonly getFooterValue = (field: ColumnConfig): string => {
    return this.getValueColumnTotal(field, 0, this.transactionCostGrandSummary(), null);
  };

  private readonly destroyRef = inject(DestroyRef);
  private readRequest: Subscription;
  private menuRequest: Subscription;

  private chartIdNumberToTransactionCostPosition: TransactionCostPosition[];
  private columnConfigs: ColumnConfig[] = [];

  private subscriptionRequestFromChart: Subscription;

  constructor() {
    super();
  }

  ngOnInit(): void {
    this.addColumn(DataType.String, 'securityaccount.name', 'NAME', true, false, {
      columnGroupConfigs: [new ColumnGroupConfig(null, 'GRAND_TOTAL')]
    });
    this.columnConfigs.push(
      this.addColumn(DataType.Numeric, 'groupTotalTaxCostMc', 'TAX_COST', true, false, {
        columnGroupConfigs: [new ColumnGroupConfig('grandTotalTaxCostMC')]
      })
    );

    this.addColumn(DataType.NumericInteger, 'groupCountPaidTransaction', 'PAID_TRANSACTIONS', true, false, {
      columnGroupConfigs: [new ColumnGroupConfig('grandCountPaidTransaction')]
    });
    this.columnConfigs.push(
      this.addColumn(DataType.Numeric, 'groupTotalAverageTransactionCostMC', 'TRANSACTION_AVERAGE_PAID', true, false, {
        columnGroupConfigs: [new ColumnGroupConfig('grandTotalAverageTransactionCostMC')]
      })
    );
    this.columnConfigs.push(
      this.addColumn(DataType.Numeric, 'groupTotalTransactionCostMC', 'TRANSACTION_COST', true, false, {
        columnGroupConfigs: [new ColumnGroupConfig('grandTotalTransactionCostMC')]
      })
    );

    this.multiSortMeta.push({ field: 'securityaccount.name', order: 1 });
    this.readData();
    this.onComponentClick(null);
  }

  readData() {
    this.readRequest?.unsubscribe();
    this.readRequest = this.portfolioService
      .getTransactionCostGrandSummaryByTenant()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((data: TransactionCostGrandSummary) => {
        this.transactionCostGrandSummary.set(data);
        this.columnConfigs.forEach((columnConfig) => {
          columnConfig.headerSuffix = data.mainCurrency;
          columnConfig.fixedCurrency = data.mainCurrency;
        });
        this.prepareTableAndTranslate();

        const urlPattern = new RegExp(`.*\/\/${AppSettings.MAIN_BOTTOM}.*\/${AppSettings.TRANSACTION_COST_KEY}\\\)`);
        urlPattern.test(this.router.url) && this.prepareCharDataAndSentToChart();
      });
  }

  override getMenuShowOptions(): MenuItem[] {
    const otherMenuShowOptions = super.getMenuShowOptions();
    const menuItems: MenuItem[] = [];
    if (otherMenuShowOptions) {
      menuItems.push(...otherMenuShowOptions);
    }

    menuItems.push({
      label: 'SHOW_CHART',
      command: (event) => this.navigateToChartRoute()
    });

    return menuItems;
  }

  chartDataPointClicked(traceIndex: number, dataPointIndex: number) {
    const summary = this.transactionCostGrandSummary();
    if (!summary) return;
    const id = this.chartAccountIds[traceIndex];
    const idTransaction = this.chartData[traceIndex]?.refId[dataPointIndex];
    if (id == null || idTransaction == null) return;
    const tcgs = summary.transactionCostGroupSummaries.find(
      (tcgsTemp) => tcgsTemp.securityaccount.idSecuritycashAccount === id
    );
    if (!tcgs) return;
    this.expandedTCGSid.update((keys) => ({ ...keys, [id]: true }));
    afterNextRender(
      () => {
        if (this.transactionCostGrandSummary() === summary) this.selectRowAndGoToPage(idTransaction, tcgs);
      },
      { injector: this.injector }
    );
  }

  isActivated(): boolean {
    return this.activePanelService.isActivated(this);
  }

  hideContextMenu(): void {}

  callMeDeactivate(): void {}

  onComponentClick(event: PanelClickEvent | null): void {
    if (event == null || !event[this.consumedGT]) {
      this.menuRequest?.unsubscribe();
      this.menuRequest = TranslateHelper.translateMenuItems$(this.getMenuShowOptions(), this.translateService)
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe((showMenu) => this.activePanelService.activatePanel(this, { showMenu }));
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
    this.router.navigate([
      BaseSettings.MAINVIEW_KEY + '/',
      {
        outlets: {
          mainbottom: [AppSettings.CHART_GENERAL_PURPOSE, AppSettings.TRANSACTION_COST_KEY]
        }
      }
    ]);
  }

  private prepareChartDataWithRequest(): void {
    if (!this.subscriptionRequestFromChart) {
      this.subscriptionRequestFromChart = this.chartDataService.requestFromChart$
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe((id) => {
          if (this.transactionCostGroupSummaries() && id === AppSettings.TRANSACTION_COST_KEY) {
            this.prepareCharDataAndSentToChart();
          }
        });
    }
  }

  private prepareCharDataAndSentToChart(): void {
    const summary = this.transactionCostGrandSummary();
    if (!summary) return;
    this.chartData = [];
    this.chartAccountIds = [];
    this.chartIdNumberToTransactionCostPosition = [];
    summary.transactionCostGroupSummaries.forEach((tcgs) => {
      const transactionIds: (number | undefined)[] = [];
      const traceSecurityaccount = {
        ...PlotlyHelper.initializeChartTrace(tcgs.securityaccount.name, 'scatter', 'markers'),
        refId: transactionIds
      };
      traceSecurityaccount.visible = 'legendonly';
      this.chartData.push(traceSecurityaccount);
      this.chartAccountIds.push(tcgs.securityaccount.idSecuritycashAccount);

      tcgs.transactionCostPositions.forEach((tcp) => {
        traceSecurityaccount.x.push(tcp.basePriceForTransactionCostMC);
        traceSecurityaccount.y.push(tcp.transactionCostMC);
        transactionIds.push(tcp.transaction.idTransaction);
        this.chartIdNumberToTransactionCostPosition.push(tcp);
      });
    });

    this.chartDataService.sentToChart({
      data: this.chartData,
      layout: { hovermode: 'closest' },
      options: {
        modeBarButtonsToRemove: ['hoverCompareCartesian', 'hoverClosestCartesian']
      },
      callBackFN: this.chartDataPointClicked.bind(this)
    });
  }

  private selectRowAndGoToPage(idTransaction: number, tcgs: TransactionCostGroupSummary): void {
    let rowIndex = 0;
    for (let i = 0; i < tcgs.transactionCostPositions.length; i++) {
      if (tcgs.transactionCostPositions[i].transaction.idTransaction === idTransaction) {
        rowIndex = i;
        this.baseSelectedRow.set(tcgs.transactionCostPositions[i]);
      }
    }
    this.firstRowIndex.set(rowIndex);
  }
}
