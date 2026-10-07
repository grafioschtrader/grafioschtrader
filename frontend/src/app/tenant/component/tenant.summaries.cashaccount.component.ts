import { DestroyRef, inject } from '@angular/core';
import { PerformanceReportDialogService } from '../../performanceperiod/service/performance-report-dialog.service';
import { Component, Injector, OnDestroy, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { TranslateService, TranslateModule } from '@ngx-translate/core';
import { GlobalparameterService } from '../../lib/services/globalparameter.service';
import { UserSettingsService } from '../../lib/services/user.settings.service';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { AccountPositionGroupSummary } from '../../entities/view/account.position.group.summary';
import { AccountPositionGrandSummary } from '../../entities/view/account.position.grand.summary';
import { AccountPositionSummary } from '../../entities/view/account.position.summary';
import { Subscription } from 'rxjs';
import { PortfolioService } from '../../portfolio/service/portfolio.service';
import { ActivatedRoute, Router } from '@angular/router';
import { ActivePanelService } from '../../lib/mainmenubar/service/active.panel.service';
import { IGlobalMenuAttach } from '../../lib/mainmenubar/component/iglobal.menu.attach';
import { ColumnConfig, ColumnGroupConfig } from '../../lib/datashowbase/column.config';
import { TableConfigBase } from '../../lib/datashowbase/table.config.base';
import { AppSettings } from '../../shared/app.settings';
import { ChartDataService } from '../../shared/chart/service/chart.data.service';
import { PlotlyHelper } from '../../shared/chart/plotly.helper';
import { HelpIds } from '../../lib/help/help.ids';
import { TenantPortfolioSummary } from '../model/tenant.portfolio.summary';
import { TranslateHelper } from '../../lib/helper/translate.helper';
import { SelectOptionsHelper } from '../../lib/helper/select.options.helper';
import { FilterService, MenuItem, SelectItem } from '@openng/optimus-ui/api';
import { BusinessHelper } from '../../shared/helper/business.helper';
import { BaseSettings } from '../../lib/base.settings';
import { GlobalparameterGTService } from '../../gtservice/globalparameter.gt.service';
import { DisposalCostHelper } from '../../shared/helper/disposal.cost.helper';
import { CommonModule } from '@angular/common';
import { TableModule } from '@openng/optimus-ui/table';
import { DatePicker } from '@openng/optimus-ui/datepicker';
import { FormsModule } from '@angular/forms';
import { SelectModule } from '@openng/optimus-ui/select';
import { TooltipModule } from '@openng/optimus-ui/tooltip';

/**
 * Shows all cash account of a tenants portfolios, it also includes the value of securities. It is grouped by
 * currencies or portfolios.
 */
@Component({
  templateUrl: '../view/tenant.summaries.cashaccount.table.html',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.Eager,
  imports: [CommonModule, TranslateModule, TableModule, DatePicker, FormsModule, SelectModule, TooltipModule]
})
export class TenantSummariesCashaccountComponent
  extends TableConfigBase
  implements OnInit, OnDestroy, IGlobalMenuAttach
{
  TenantPortfolioSummary: typeof TenantPortfolioSummary = TenantPortfolioSummary;

  readonly CASHBALANCE_MC = 'cashBalanceMC';
  readonly VALUE_SECURITIES_MAIN_CURRENCY = 'valueSecuritiesMC';
  public groupChangeIndexMap: Map<number, AccountPositionGroupSummary> = new Map();
  untilDate: Date;
  accountPositionGrandSummary: AccountPositionGrandSummary;
  accountPositionSummaryAll: AccountPositionSummary[] = [];
  groupOptions: SelectItem[] = [];
  selectedGroup: string = TenantPortfolioSummary[TenantPortfolioSummary.GROUP_BY_CURRENCY];
  private idTenant: number;
  private routeSubscribe: Subscription;
  private columnConfigs: ColumnConfig[] = [];
  private excludedDivTaxColumn: ColumnConfig;
  private subscriptionRequestFromChart: Subscription;
  private CHART_TITLE = 'CASH_BALANCE_SECURITIES';

  private statementReports = inject(PerformanceReportDialogService);
  private reportDestroyRef = inject(DestroyRef);

  constructor(
    private portfolioService: PortfolioService,
    private activatedRoute: ActivatedRoute,
    private activePanelService: ActivePanelService,
    private router: Router,
    private chartDataService: ChartDataService,
    filterService: FilterService,
    translateService: TranslateService,
    gps: GlobalparameterService,
    usersettingsService: UserSettingsService,
    injector: Injector
  ) {
    super(filterService, usersettingsService, translateService, gps, injector);

    this.addColumn(DataType.String, 'cashaccount.name', 'NAME', true, false, {
      width: 100,
      columnGroupConfigs: [new ColumnGroupConfig('groupName', 'TOTAL'), new ColumnGroupConfig(null, 'GRAND_TOTAL')]
    });
    this.addColumnFeqH(DataType.String, 'cashaccount.currency', true, false, {
      width: 40
    });

    this.addColumn(DataType.Numeric, 'closePrice', 'CURRENCY_RATE', true, false, {
      maxFractionDigits: this.gps.getMaxFractionDigits(),
      templateName: 'greenRed'
    });

    this.columnConfigs.push(
      this.addColumnFeqH(DataType.Numeric, 'externalCashTransferMC', true, false, {
        templateName: 'greenRed',
        columnGroupConfigs: [
          new ColumnGroupConfig('groupExternalCashTransferMC'),
          new ColumnGroupConfig('grandExternalCashTransferMC')
        ]
      })
    );

    this.columnConfigs.push(
      this.addColumnFeqH(DataType.Numeric, 'cashTransferMC', true, false, {
        templateName: 'greenRed',
        columnGroupConfigs: [new ColumnGroupConfig('groupCashTransferMC'), new ColumnGroupConfig('grandCashTransferMC')]
      })
    );

    this.columnConfigs.push(
      this.addColumnFeqH(DataType.Numeric, 'cashAccountTransactionFeeMC', true, false, {
        width: 70,
        templateName: 'greenRed',
        columnGroupConfigs: [
          new ColumnGroupConfig('groupCashAccountTransactionFeeMC'),
          new ColumnGroupConfig('grandCashAccountTransactionFeeMC')
        ]
      })
    );

    this.columnConfigs.push(
      this.addColumn(DataType.Numeric, 'accountFeesMC', 'FEE', true, false, {
        width: 80,
        templateName: 'greenRed',
        columnGroupConfigs: [new ColumnGroupConfig('groupAccountFeesMC'), new ColumnGroupConfig('grandAccountFeesMC')]
      })
    );
    this.columnConfigs.push(
      this.addColumn(DataType.Numeric, 'accountInterestMC', 'INTEREST_CASHACCOUNT', true, false, {
        templateName: 'greenRed',
        columnGroupConfigs: [
          new ColumnGroupConfig('groupAccountInterestMC'),
          new ColumnGroupConfig('grandAccountInterestMC')
        ]
      })
    );
    this.columnConfigs.push(
      this.addColumnFeqH(DataType.Numeric, 'gainLossCurrencyMC', true, false, {
        templateName: 'greenRed',
        columnGroupConfigs: [
          new ColumnGroupConfig('groupGainLossCurrencyMC'),
          new ColumnGroupConfig('grandGainLossCurrencyMC')
        ]
      })
    );
    this.columnConfigs.push(
      this.addColumnFeqH(DataType.Numeric, 'gainLossSecurities', true, false, {
        templateName: 'greenRed',
        columnGroupConfigs: [
          new ColumnGroupConfig('groupGainLossSecuritiesMC'),
          new ColumnGroupConfig('grandGainLossSecuritiesMC')
        ]
      })
    );
    this.excludedDivTaxColumn = this.addColumnFeqH(DataType.Numeric, 'excludedDivTaxMC', false, false, {
      templateName: 'greenRed',
      columnGroupConfigs: [
        new ColumnGroupConfig('groupExcludedDivTaxMC'),
        new ColumnGroupConfig('grandExcludedDivTaxMC')
      ]
    });
    this.columnConfigs.push(this.excludedDivTaxColumn);

    this.columnConfigs.push(
      this.addColumn(
        DataType.Numeric,
        this.VALUE_SECURITIES_MAIN_CURRENCY,
        AppSettings.SECURITY.toUpperCase(),
        true,
        false,
        {
          templateName: 'greenRed',
          columnGroupConfigs: [
            new ColumnGroupConfig('groupValueSecuritiesMC'),
            new ColumnGroupConfig('grandValueSecuritiesMC')
          ]
        }
      )
    );
    this.addColumnFeqH(DataType.Numeric, 'cashBalance', true, false, {
      currencyPrecisionField: 'cashaccount.currency'
    });
    this.columnConfigs.push(
      this.addColumn(DataType.Numeric, this.CASHBALANCE_MC, 'CASH_BALANCE', true, false, {
        templateName: 'greenRed',
        columnGroupConfigs: [new ColumnGroupConfig('groupCashBalanceMC'), new ColumnGroupConfig('grandCashBalanceMC')]
      })
    );
    this.columnConfigs.push(
      this.addColumnFeqH(DataType.Numeric, 'valueMC', true, false, {
        templateName: 'greenRed',
        columnGroupConfigs: [new ColumnGroupConfig('groupValueMC'), new ColumnGroupConfig('grandValueMC')]
      })
    );
    this.addDisposalCostColumns(injector.get(GlobalparameterGTService));

    this.untilDate = BusinessHelper.getUntilDateBySessionStorage();

    SelectOptionsHelper.createSelectItemForEnum(translateService, TenantPortfolioSummary, this.groupOptions);
  }

  ngOnInit(): void {
    this.translateService.get(this.CHART_TITLE).subscribe((translated) => (this.CHART_TITLE = translated));
    this.idTenant = this.gps.getIdTenant();
    this.onComponentClick(null);
    this.readData();
  }

  public override filterDate(event): void {
    this.readData();
  }

  onResetToDay(event): void {
    this.untilDate = new Date();
    this.readData();
  }

  override getMenuShowOptions(): MenuItem[] {
    const otherMenuShowOptions: MenuItem[] = super.getMenuShowOptions();

    const menuItems: MenuItem[] = [];
    if (otherMenuShowOptions) {
      menuItems.push(...otherMenuShowOptions);
    }
    menuItems.push(
      this.statementReports.menu(
        () => this.untilDate,
        () => null,
        this.reportDestroyRef
      )
    );
    menuItems.push({ separator: true });
    menuItems.push({
      label: 'SHOW_CHART',
      command: (event) => this.navigateToChartRoute()
    });

    TranslateHelper.translateMenuItems(menuItems, this.translateService);
    return menuItems;
  }

  handleChangeGroup(event) {
    this.selectedGroup = event.value;
    this.readData();
  }

  isActivated(): boolean {
    return this.activePanelService.isActivated(this);
  }

  public hideContextMenu(): void {}

  callMeDeactivate(): void {}

  public getHelpContextId(): string {
    return HelpIds.HELP_PORTFOLIOS_PORTFOLIOS;
  }

  /**
   * Lists the currency pairs that could not be converted, for the warning shown above the table. It is only these
   * accounts that are missing from the totals, so naming them tells the user exactly what the shown sums leave out.
   *
   * @returns the affected pairs as a readable enumeration, for example 'BTC/CHF', or null when nothing is missing
   */
  get missingCurrenciesText(): string {
    const missingExchangeRates = this.accountPositionGrandSummary?.missingExchangeRates;
    return missingExchangeRates?.length
      ? missingExchangeRates.map((mer) => `${mer.fromCurrency}/${mer.toCurrency}`).join(', ')
      : null;
  }

  onComponentClick(event): void {
    this.activePanelService.activatePanel(this, {
      showMenu: this.getMenuShowOptions()
    });
  }

  ngOnDestroy(): void {
    BusinessHelper.saveUntilDateInSessionStorage(this.untilDate);
    this.activePanelService.destroyPanel(this);
    this.routeSubscribe && this.routeSubscribe.unsubscribe();
    this.subscriptionRequestFromChart && this.subscriptionRequestFromChart.unsubscribe();
  }

  /**
   * Highlights the disposal cost cells of an account whose estimate is incomplete.
   *
   * @param accountPositionSummary - The cash account of the row
   * @param field - The column
   * @returns The highlighting style or an empty style
   */
  getCellStyle(accountPositionSummary: AccountPositionSummary, field: ColumnConfig): { [key: string]: string } {
    return DisposalCostHelper.DISPOSAL_FIELDS.includes(field.field) &&
      accountPositionSummary?.disposalComplete === false
      ? DisposalCostHelper.INCOMPLETE_STYLE
      : {};
  }

  /**
   * Highlights the disposal cost total of a group whose estimate is incomplete.
   *
   * @param field - The column of the group row
   * @param group - The group summary of that row
   * @returns The highlighting style or an empty style
   */
  getGroupCellStyle(field: ColumnConfig, group: AccountPositionGroupSummary): { [key: string]: string } {
    return DisposalCostHelper.DISPOSAL_FIELDS.includes(field.field) && group?.groupDisposalComplete === false
      ? DisposalCostHelper.INCOMPLETE_STYLE
      : {};
  }

  /**
   * Highlights the disposal cost grand total when the estimate of any account is incomplete.
   *
   * @param field - The column of the footer row
   * @returns The highlighting style merged with the column width
   */
  getGrandCellStyle(field: ColumnConfig): { [key: string]: string } {
    const widthStyle = field.width ? { 'flex-basis': '0 0 ' + field.width + 'px' } : {};
    return DisposalCostHelper.DISPOSAL_FIELDS.includes(field.field) &&
      this.accountPositionGrandSummary?.grandDisposalComplete === false
      ? { ...widthStyle, ...DisposalCostHelper.INCOMPLETE_STYLE }
      : widthStyle;
  }

  /**
   * Tooltip of an account cell: for the disposal cost columns the matched rule or the reason of an unknown conversion
   * markup into the main currency, otherwise the displayed value.
   *
   * @param accountPositionSummary - The cash account of the row
   * @param field - The column
   * @returns The tooltip text
   */
  getCellTooltip(accountPositionSummary: AccountPositionSummary, field: ColumnConfig): string {
    if (DisposalCostHelper.DISPOSAL_FIELDS.includes(field.field) && accountPositionSummary?.disposalDetails) {
      return DisposalCostHelper.getDetailsText(accountPositionSummary.disposalDetails, this.translateService, this.gps);
    }
    return this.getValueByPath(accountPositionSummary, field);
  }

  /**
   * Adds the estimated disposal costs and the value after them, only when gt.disposal.cost.estimate and the switch of
   * the tenant are both on.
   *
   * @param gpsGT - Supplies the switches stored at login
   */
  private addDisposalCostColumns(gpsGT: GlobalparameterGTService): void {
    if (gpsGT.useDisposalCostEstimate()) {
      this.columnConfigs.push(
        this.addColumnFeqH(DataType.Numeric, 'disposalCostMC', true, true, {
          templateName: 'greenRed',
          columnGroupConfigs: [
            new ColumnGroupConfig('groupDisposalCostMC'),
            new ColumnGroupConfig('grandDisposalCostMC')
          ]
        })
      );
      this.columnConfigs.push(
        this.addColumnFeqH(DataType.Numeric, 'valueAfterDisposalMC', true, true, {
          templateName: 'greenRed',
          columnGroupConfigs: [
            new ColumnGroupConfig('groupValueAfterDisposalMC'),
            new ColumnGroupConfig('grandValueAfterDisposalMC')
          ]
        })
      );
    }
  }

  private readData(): void {
    this.portfolioService
      .getGroupedAccountsSecuritiesTenantSummary(this.untilDate, TenantPortfolioSummary[this.selectedGroup])
      .subscribe((result) => {
        this.transformToFlatArray(result);
        this.excludedDivTaxColumn.visible = result.accountPositionGroupSummaryList.some((g) => g.excludeDivTax);
        this.columnConfigs.forEach((columnConfig) => {
          columnConfig.headerSuffix = this.accountPositionGrandSummary.mainCurrency;
          columnConfig.fixedCurrency = this.accountPositionGrandSummary.mainCurrency;
        });
        this.prepareTableAndTranslate();
        this.changeToOpenChart();
      });
  }

  private transformToFlatArray(accountPositionGrandSummary: AccountPositionGrandSummary) {
    this.accountPositionGrandSummary = accountPositionGrandSummary;
    const aPSA: AccountPositionSummary[] = [];
    this.groupChangeIndexMap = new Map();
    let rowIndex = -1;
    for (const accountPositionGroupSummary of accountPositionGrandSummary.accountPositionGroupSummaryList) {
      for (const accountPositionSummary of accountPositionGroupSummary.accountPositionSummaryList) {
        aPSA.push(accountPositionSummary);
        rowIndex++;
      }
      this.groupChangeIndexMap.set(rowIndex, accountPositionGroupSummary);
    }
    this.accountPositionSummaryAll = aPSA;
  }

  private changeToOpenChart(): void {
    this.subscriptionRequestFromChart && this.chartDataService.sentToChart(this.getChartDefinition());
  }

  private navigateToChartRoute(): void {
    !this.subscriptionRequestFromChart && this.prepareChartDataWithRequest();
    this.router.navigate([
      BaseSettings.MAINVIEW_KEY + '/',
      {
        outlets: {
          mainbottom: [AppSettings.CHART_GENERAL_PURPOSE, AppSettings.PORTFOLIO_KEY]
        }
      }
    ]);
  }

  private prepareChartDataWithRequest(): void {
    this.subscriptionRequestFromChart = this.chartDataService.requestFromChart$.subscribe((id) => {
      if (id === AppSettings.PORTFOLIO_KEY) {
        this.chartDataService.sentToChart(this.getChartDefinition());
      }
    });
  }

  private getChartDefinition(): any {
    const securityBar = PlotlyHelper.initializeChartTrace(
      this.getColumnConfigByField(this.VALUE_SECURITIES_MAIN_CURRENCY).headerTranslated,
      'bar'
    );
    const cashBalance = PlotlyHelper.initializeChartTrace(
      this.getColumnConfigByField(this.CASHBALANCE_MC).headerTranslated,
      'bar'
    );
    const data = [securityBar, cashBalance];

    for (const accountPositionGroupSummary of this.accountPositionGrandSummary.accountPositionGroupSummaryList) {
      if (
        Math.abs(accountPositionGroupSummary.groupValueSecuritiesMC) > 0.02 ||
        Math.abs(accountPositionGroupSummary.groupCashBalanceMC) > 0.02
      ) {
        securityBar.x.push(accountPositionGroupSummary.groupName);
        securityBar.y.push(accountPositionGroupSummary.groupValueSecuritiesMC);
        cashBalance.y.push(accountPositionGroupSummary.groupCashBalanceMC);
      }
    }
    cashBalance.x = securityBar.x;

    const layout = { barmode: 'stack', title: { text: this.CHART_TITLE } };
    return { data, layout };
  }
}
