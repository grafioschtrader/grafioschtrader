import {Component, OnDestroy, OnInit} from '@angular/core';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {UserSettingsService} from '../../shared/service/user.settings.service';
import {DataType} from '../../dynamic-form/models/data.type';
import {AccountPositionGroupSummary} from '../../entities/view/account.position.group.summary';
import {AccountPositionGrandSummary} from '../../entities/view/account.position.grand.summary';
import {AccountPositionSummary} from '../../entities/view/account.position.summary';
import {Subscription} from 'rxjs';
import {PortfolioService} from '../../portfolio/service/portfolio.service';
import {ActivatedRoute, Router} from '@angular/router';
import {ActivePanelService} from '../../shared/mainmenubar/service/active.panel.service';
import {IGlobalMenuAttach} from '../../shared/mainmenubar/component/iglobal.menu.attach';
import {ColumnConfig, ColumnGroupConfig} from '../../shared/datashowbase/column.config';
import {TableConfigBase} from '../../shared/datashowbase/table.config.base';
import {AppSettings} from '../../shared/app.settings';
import {AppHelper} from '../../shared/helper/app.helper';
import {ChartDataService} from '../../shared/chart/service/chart.data.service';
import {PlotlyHelper} from '../../shared/chart/plotly.helper';
import {HelpIds} from '../../shared/help/help.ids';
import {TenantPortfolioSummary} from '../model/tenant.portfolio.summary';
import {TranslateHelper} from '../../shared/helper/translate.helper';
import {SelectOptionsHelper} from '../../shared/helper/select.options.helper';
import {FilterService, MenuItem, SelectItem} from 'primeng/api';


/**
 * Shows all cash account of a tenants portfolios, it also includes the value of securities. It is grouped by
 * currencies or portfolios.
 */
@Component({
  templateUrl: '../view/tenant.summaries.cashaccount.table.html'
})
export class TenantSummariesCashaccountComponent extends TableConfigBase implements OnInit, OnDestroy, IGlobalMenuAttach {

  TenantPortfolioSummary: typeof TenantPortfolioSummary = TenantPortfolioSummary;

  readonly CASHBALANCE_MC = 'cashBalanceMC';
  readonly VALUE_SECURITIES_MAIN_CURRENCY = 'valueSecuritiesMC';
  public groupChangeIndexMap: Map<number, AccountPositionGroupSummary> = new Map();
  untilDate: Date;
  accountPositionGrandSummary: AccountPositionGrandSummary;
  accountPositionSummaryAll: AccountPositionSummary [] = [];
  groupOptions: SelectItem[] = [];
  selectedGroup: string = TenantPortfolioSummary[TenantPortfolioSummary.GROUP_BY_CURRENCY];
  private idTenant: number;
  private routeSubscribe: Subscription;
  private columnConfigs: ColumnConfig[] = [];
  private subscriptionRequestFromChart: Subscription;
  private CHART_TITLE = 'CASH_BALANCE_SECURITIES';

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

    this.addColumn(DataType.String, 'cashaccount.name', 'NAME', true, false,
      {
        width: 100, columnGroupConfigs: [new ColumnGroupConfig('groupName', 'TOTAL'),
          new ColumnGroupConfig(null, 'GRAND_TOTAL')],
      });
    this.addColumnFeqH(DataType.String, 'cashaccount.currency', true, false,
      {width: 40});

    this.addColumn(DataType.Numeric, 'closePrice', 'CURRENCY_RATE', true, false,
      {maxFractionDigits: AppSettings.FID_MAX_FRACTION_DIGITS, templateName: 'greenRed'});

    this.columnConfigs.push(this.addColumnFeqH(DataType.Numeric, 'externalCashTransferMC',
      true, false, {
        templateName: 'greenRed',
        columnGroupConfigs: [new ColumnGroupConfig('groupExternalCashTransferMC'),
          new ColumnGroupConfig('grandExternalCashTransferMC')]
      }));

    this.columnConfigs.push(this.addColumnFeqH(DataType.Numeric, 'cashTransferMC',
      true, false, {
        templateName: 'greenRed',
        columnGroupConfigs: [new ColumnGroupConfig('groupCashTransferMC'),
          new ColumnGroupConfig('grandCashTransferMC')]
      }));

    this.columnConfigs.push(this.addColumnFeqH(DataType.Numeric, 'cashAccountTransactionFeeMC', true, false,
      {
        templateName: 'greenRed',
        columnGroupConfigs: [new ColumnGroupConfig('groupCashAccountTransactionFeeMC'),
          new ColumnGroupConfig('grandCashAccountTransactionFeeMC')]
      }));

    this.columnConfigs.push(this.addColumn(DataType.Numeric, 'accountFeesMC', 'FEE', true, false,
      {
        templateName: 'greenRed',
        columnGroupConfigs: [new ColumnGroupConfig('groupAccountFeesMC'),
          new ColumnGroupConfig('grandAccountFeesMC')]
      }));
    this.columnConfigs.push(this.addColumn(DataType.Numeric, 'accountInterestMC', 'INTEREST_CASHACCOUNT', true, false,
      {
        templateName: 'greenRed',
        columnGroupConfigs: [new ColumnGroupConfig('groupAccountInterestMC'),
          new ColumnGroupConfig('grandAccountInterestMC')]
      }));
    this.columnConfigs.push(this.addColumnFeqH(DataType.Numeric, 'gainLossCurrencyMC', true, false,
         {
           templateName: 'greenRed',
           columnGroupConfigs: [new ColumnGroupConfig('groupGainLossCurrencyMC'),
             new ColumnGroupConfig('grandGainLossCurrencyMC')]
         }));
    this.columnConfigs.push(this.addColumnFeqH(DataType.Numeric, 'gainLossSecurities', true, false,
      {
        templateName: 'greenRed',
        columnGroupConfigs: [new ColumnGroupConfig('groupGainLossSecuritiesMC'),
          new ColumnGroupConfig('grandGainLossSecuritiesMC')]
      }));
    this.columnConfigs.push(this.addColumn(DataType.Numeric, this.VALUE_SECURITIES_MAIN_CURRENCY,
      AppSettings.SECURITY.toUpperCase(), true, false,
      {
        templateName: 'greenRed',
        columnGroupConfigs: [new ColumnGroupConfig('groupValueSecuritiesMC'),
          new ColumnGroupConfig('grandValueSecuritiesMC')]
      }));
    this.addColumnFeqH(DataType.Numeric, 'cashBalance', true, false);
    this.columnConfigs.push(this.addColumn(DataType.Numeric, this.CASHBALANCE_MC, 'CASH_BALANCE', true, false,
      {
        templateName: 'greenRed',
        columnGroupConfigs: [new ColumnGroupConfig('groupCashBalanceMC'),
          new ColumnGroupConfig('grandCashBalanceMC')]
      }));
    this.columnConfigs.push(this.addColumnFeqH(DataType.Numeric, 'valueMC', true, false,
      {
        templateName: 'greenRed',
        columnGroupConfigs: [new ColumnGroupConfig('groupValueMC'), new ColumnGroupConfig('grandValueMC')]
      }));

    this.untilDate = AppHelper.getUntilDateBySessionStorage();

    SelectOptionsHelper.createSelectItemForEnum(translateService, TenantPortfolioSummary, this.groupOptions);
  }

  ngOnInit(): void {
    this.translateService.get(this.CHART_TITLE).subscribe(translated => this.CHART_TITLE = translated);
    this.idTenant = this.gps.getIdTenant();
    this.onComponentClick(null);
    this.readData();
  }

  public filterDate(event): void {
    this.readData();
  }

  onResetToDay(event): void {
    this.untilDate = new Date();
    this.readData();
  }

  getMenuShowOptions(): MenuItem[] {
    const otherMenuShowOptions: MenuItem[] = super.getMenuShowOptions();

    const menuItems: MenuItem[] = [];
    if (otherMenuShowOptions) {
      menuItems.push(...otherMenuShowOptions);
    }
    menuItems.push({separator: true});
    menuItems.push({label: 'SHOW_CHART', command: (event) => this.navigateToChartRoute()});

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

  public hideContextMenu(): void {
  }

  callMeDeactivate(): void {
  }

  public getHelpContextId(): HelpIds {
    return HelpIds.HELP_PROTFOLIOS_PORTFOLIOS;
  }

  onComponentClick(event): void {
    this.activePanelService.activatePanel(this, {showMenu: this.getMenuShowOptions()});
  }

  ngOnDestroy(): void {
    AppHelper.saveUntilDateInSessionStorage(this.untilDate);
    this.activePanelService.destroyPanel(this);
    this.routeSubscribe && this.routeSubscribe.unsubscribe();
    this.subscriptionRequestFromChart && this.subscriptionRequestFromChart.unsubscribe();
  }

  private readData(): void {
    this.portfolioService.getGroupedAccountsSecuritiesTenantSummary(this.untilDate, TenantPortfolioSummary[this.selectedGroup]).subscribe(
      result => {
        this.transformToFlatArray(result);
        this.columnConfigs.forEach(columnConfig => columnConfig.headerSuffix = this.accountPositionGrandSummary.mainCurrency);
        this.prepareTableAndTranslate();
        this.changeToOpenChart();
      });
  }

  private transformToFlatArray(accountPositionGrandSummary: AccountPositionGrandSummary) {
    this.accountPositionGrandSummary = accountPositionGrandSummary;
    const aPSA: AccountPositionSummary [] = [];
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
    this.router.navigate([AppSettings.MAINVIEW_KEY + '/', {
      outlets: {
        mainbottom: [AppSettings.CHART_GENERAL_PURPOSE, AppSettings.PORTFOLIO_KEY]
      }
    }]);
  }

  private prepareChartDataWithRequest(): void {
    this.subscriptionRequestFromChart = this.chartDataService.requestFromChart$.subscribe(id => {
        if (id === AppSettings.PORTFOLIO_KEY) {
          this.chartDataService.sentToChart(this.getChartDefinition());
        }
      }
    );
  }

  private getChartDefinition(): any {
    const securityBar = PlotlyHelper.initializeChartTrace(this.getColumnConfigByField(
      this.VALUE_SECURITIES_MAIN_CURRENCY).headerTranslated, 'bar');
    const cashBalance = PlotlyHelper.initializeChartTrace(this.getColumnConfigByField(this.CASHBALANCE_MC).headerTranslated, 'bar');
    const data = [securityBar, cashBalance];

    for (const accountPositionGroupSummary of this.accountPositionGrandSummary.accountPositionGroupSummaryList) {
      if (Math.abs(accountPositionGroupSummary.groupValueSecuritiesMC) > 0.02
        || Math.abs(accountPositionGroupSummary.groupCashBalanceMC) > 0.02) {
        securityBar.x.push(accountPositionGroupSummary.groupName);
        securityBar.y.push(accountPositionGroupSummary.groupValueSecuritiesMC);
        cashBalance.y.push(accountPositionGroupSummary.groupCashBalanceMC);
      }
    }
    cashBalance.x = securityBar.x;

    const layout = {barmode: 'stack', title: {text: this.CHART_TITLE}};
    return {data, layout};
  }
}

