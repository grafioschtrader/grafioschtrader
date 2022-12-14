import {IGlobalMenuAttach} from '../../shared/mainmenubar/component/iglobal.menu.attach';
import {TableConfigBase} from '../../shared/datashowbase/table.config.base';
import {SecurityPositionDynamicGroupSummary} from '../../entities/view/security.position.dynamic.group.summary';
import {Directive, ElementRef, ViewChild} from '@angular/core';
import {SecurityaccountGroupBase} from './securityaccount.group.base';
import {SecurityPositionCurrenyGroupSummary} from '../../entities/view/security.position.curreny.group.summary';
import {Subscription} from 'rxjs';
import {ColumnConfig, ColumnGroupConfig} from '../../shared/datashowbase/column.config';
import {SecurityPositionSummary} from '../../entities/view/security.position.summary';
import {TransactionCallParam} from '../../transaction/component/transaction.call.parm';
import {FilterService, MenuItem} from 'primeng/api';
import {Portfolio} from '../../entities/portfolio';
import {SecurityPositionGrandSummary} from '../../entities/view/security.position.grand.summary';
import {SecurityaccountService} from '../service/securityaccount.service';
import {ActivePanelService} from '../../shared/mainmenubar/service/active.panel.service';
import {ActivatedRoute, Router} from '@angular/router';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {ChartDataService} from '../../shared/chart/service/chart.data.service';
import {MessageToastService} from '../../shared/message/message.toast.service';
import {UserSettingsService} from '../../shared/service/user.settings.service';
import {TranslateService} from '@ngx-translate/core';
import {AppSettings} from '../../shared/app.settings';
import {DataType} from '../../dynamic-form/models/data.type';
import {AppHelper} from '../../shared/helper/app.helper';
import {TransactionType} from '../../shared/types/transaction.type';
import {Security} from '../../entities/security';
import {ProcessedAction} from '../../shared/types/processed.action';
import {ProcessedActionData} from '../../shared/types/processed.action.data';
import {HelpIds} from '../../shared/help/help.ids';
import {OptionalParameters, TimeSeriesQuotesService} from '../../historyquote/service/time.series.quotes.service';
import {AssetclassType} from '../../shared/types/assetclass.type';
import {Securitycurrency} from '../../entities/securitycurrency';
import {TranslateHelper} from '../../shared/helper/translate.helper';
import {BusinessHelper} from '../../shared/helper/business.helper';
import {ProductIconService} from '../../securitycurrency/service/product.icon.service';
import {SpecialInvestmentInstruments} from '../../shared/types/special.investment.instruments';


@Directive()
export abstract class SecurityaccountBaseTable extends TableConfigBase implements IGlobalMenuAttach {
  // @ViewChild('cm', { static: false }) contextMenu: ContextMenu;
  @ViewChild('cm') contextMenu: any;
  @ViewChild('title') titleElementRef: ElementRef;

  translatedTitle: string;

  showTable = true;
  untilDate: Date;

  securityaccountGroupBase: SecurityaccountGroupBase<SecurityPositionDynamicGroupSummary<any> | SecurityPositionCurrenyGroupSummary, any>;
  securityPositionAll: SecurityPositionSummary[];
  securityPositionSummary: SecurityPositionGrandSummary;
  groupMap: Map<any, SecurityaccountGroupBase<any, any>>;
  groupChangeIndexMap: Map<number, SecurityaccountGroupBase<any, any>>;
  // For child transaction dialog
  transactionCallParam: TransactionCallParam;
  visibleSecurityTransactionDialog = false;
  // For row expansion, used when a transaction was changed
  expandedSecurityPositionSummary: SecurityPositionSummary[] = [];
  expandedIdSecuritycurrency: number[] = [];
  contextMenuItems: MenuItem[] = [];
  selectedSecurityPositionSummary: SecurityPositionSummary;
  portfolio: Portfolio;
  protected includeClosedPosition = false;
  protected internalColumnConfigs: ColumnConfig[];
  private subscriptionRequestFromChart: Subscription;

  protected constructor(protected timeSeriesQuotesService: TimeSeriesQuotesService,
                        protected activePanelService: ActivePanelService,
                        protected messageToastService: MessageToastService,
                        protected securityaccountService: SecurityaccountService,
                        protected productIconService: ProductIconService,
                        protected activatedRoute: ActivatedRoute,
                        protected router: Router,
                        private chartDataService: ChartDataService,
                        filterService: FilterService,
                        translateService: TranslateService,
                        gps: GlobalparameterService,
                        usersettingsService: UserSettingsService) {
    super(filterService, usersettingsService, translateService, gps);
    this.untilDate = AppHelper.getUntilDateBySessionStorage();
  }

  getGroupValueByRowIndex(columnConfig: ColumnConfig, securityPositionSummary: SecurityPositionSummary): string {
    const groupValue = this.securityaccountGroupBase.getGroupValue(securityPositionSummary.security);
    return this.getValueColumnTotal(columnConfig, 0, this.groupMap, groupValue);
  }

  getGroupValue(columnConfig: ColumnConfig, securityPositionSummary: SecurityPositionSummary): string {
    const groupValue = this.securityaccountGroupBase.getGroupValue(securityPositionSummary.security);
    return this.getValueColumnTotal(columnConfig, 0, this.groupMap, groupValue);
  }

  transformToFlatArray(securityPositionGrandSummary: SecurityPositionGrandSummary): void {
    this.groupMap = new Map();
    this.groupChangeIndexMap = new Map();
    let rowIndex = -1;
    for (const securityPositionGroupSummary of securityPositionGrandSummary.securityPositionGroupSummaryList) {
      for (const securityPositionSummary of securityPositionGroupSummary.securityPositionSummaryList) {
        this.securityPositionAll.push(securityPositionSummary);
        if (this.expandedIdSecuritycurrency.indexOf(securityPositionSummary.security.idSecuritycurrency) >= 0) {
          this.expandedSecurityPositionSummary.push(securityPositionSummary);
        }
        rowIndex++;
      }
      this.groupChangeIndexMap.set(rowIndex, <any>securityPositionGroupSummary);
      this.groupMap.set(this.securityaccountGroupBase.getGroupValueFromGroupRow(<any>securityPositionGroupSummary),
        <any>securityPositionGroupSummary);
    }
    this.showTable = true;
  }

  getMenuShowOptionsParam(securitycurrencySelected: Securitycurrency): MenuItem[] {
    const otherMenuShowOptions: MenuItem[] = super.getMenuShowOptions();

    let menuItems: MenuItem[] = [];
    if (otherMenuShowOptions) {
      menuItems.push(...otherMenuShowOptions);
    }

    if (securitycurrencySelected && securitycurrencySelected.idSecuritycurrency >= 0) {
      menuItems = this.addTimeSeriesMenus(<Security>securitycurrencySelected, menuItems, false);
    }

    menuItems.push({separator: true});
    menuItems.push({
      label: 'SHOW_CHART',
      command: (event) => this.navigateToChartRoute()
    });

    menuItems.push({separator: true});
    menuItems.push({
      label: 'SHOW_CLOSE_POSITION',
      command: (event) => this.handleOpenClosePosition(event),
      icon: this.includeClosedPosition ? AppSettings.ICONNAME_SQUARE_CHECK : AppSettings.ICONNAME_SQUARE_EMTPY
    });

    if (securitycurrencySelected && securitycurrencySelected.idSecuritycurrency >= 0) {
      menuItems.push(...BusinessHelper.getUrlLinkMenus(securitycurrencySelected));
    }

    TranslateHelper.translateMenuItems(menuItems, this.translateService);
    return menuItems;
  }

  public hideContextMenu() {
    this.contextMenu && this.contextMenu.hide();
  }

  public filterDate(event) {
    this.readData();
  }

  handleTransaction(transactionType: TransactionType, security: Security) {
    this.transactionCallParam = Object.assign(new TransactionCallParam(), {
      transactionType,
      security
    });
    this.extendTransactionParamData(this.transactionCallParam);
    this.visibleSecurityTransactionDialog = true;
  }

  handleCloseTransactionDialog(processedActionData: ProcessedActionData) {
    this.visibleSecurityTransactionDialog = false;
    if (processedActionData.action !== ProcessedAction.NO_CHANGE) {
      this.readData();
    }
  }

  handleOpenClosePosition(event) {
    if (event.item.icon === AppSettings.ICONNAME_SQUARE_EMTPY) {
      event.item.icon = AppSettings.ICONNAME_SQUARE_CHECK;
      this.includeClosedPosition = true;
    } else {
      event.item.icon = AppSettings.ICONNAME_SQUARE_EMTPY;
      this.includeClosedPosition = false;
    }
    this.readData();
  }

  transactionDataChanged(event: ProcessedActionData) {
    this.readData();
  }

  isActivated(): boolean {
    return this.activePanelService.isActivated(this);
  }

  callMeDeactivate(): void {
  }

  onRightClick(event): void {
    this.isActivated() && this.resetMenu();
  }

  onResetToDay(event): void {
    this.untilDate = new Date();
    this.readData();
  }

  ////////////////////////////////////////////////
  // For Chart

  onComponentClick(event): void {
    if (!event || !event[this.consumedGT]) {
      this.resetMenu();
    }
  }

  ///////////////////////////////////////////////
  onRowExpand(event) {
    this.expandedIdSecuritycurrency.push(event.data.security.idSecuritycurrency);
  }

  onRowCollapse(event) {
    const index = this.expandedIdSecuritycurrency.indexOf(event.data.security.idSecuritycurrency);
    this.expandedIdSecuritycurrency.splice(index, 1);
  }

  public getHelpContextId(): HelpIds {
    return HelpIds.HELP_PROTFOLIOS_SECURITY_ACCOUNT_REPORT;
  }

  destroy(): void {
    AppHelper.saveUntilDateInSessionStorage(this.untilDate);
    this.activePanelService.destroyPanel(this);
    this.subscriptionRequestFromChart && this.subscriptionRequestFromChart.unsubscribe();
  }

  getInstrumentIcon(securityPositionSummary: SecurityPositionSummary): string {
    return this.productIconService.getIconForInstrument(securityPositionSummary.security, null);
  }

  addUrlLinkMenus(securitycurrency: Securitycurrency, menuItems: MenuItem[], translate: boolean): MenuItem[] {
    const urlLinkMenuItems = BusinessHelper.getUrlLinkMenus(securitycurrency);
    translate && TranslateHelper.translateMenuItems(urlLinkMenuItems, this.translateService);
    return menuItems.concat(urlLinkMenuItems);
  }

  isMarginProduct(security: Security): boolean {
    return BusinessHelper.isMarginProduct(security);
  }

  protected abstract readData(): void;

  protected abstract extendTransactionParamData(transactionCallParam: TransactionCallParam): void;

  protected abstract getOptionalParameters(): OptionalParameters;

  protected createColumns() {
    this.removeAllColumns();
    this.internalColumnConfigs = [];

    this.addColumn(DataType.String, 'security.name', 'NAME', true, false,
      {
        columnGroupConfigs: [new ColumnGroupConfig('security.currency', 'EXCHANGE_RATE',
          this.securityaccountGroupBase.getGroupRowFieldTextByRowIndex.bind(this.securityaccountGroupBase),
          {colspan: 99}),
          new ColumnGroupConfig(null, 'GRAND_TOTAL')], width: 170
      });
    this.addColumn(DataType.String, AppSettings.SECURITY.toLowerCase(), AppSettings.INSTRUMENT_HEADER, true, true,
      {fieldValueFN: this.getInstrumentIcon.bind(this), templateName: 'icon', width: 20});

    this.addColumn(DataType.Numeric, 'units', 'QUANTITY', true, false, {width: 55});
    this.addColumnFeqH(DataType.String, 'security.currency', true, false, {width: 35});

    this.addColumn(DataType.DateTimeNumeric, 'closeDate', 'TIMEDATE', true, false,
      {width: 90});
    this.addColumn(DataType.Numeric, 'closePrice', 'LAST', true, false,
      {columnGroupConfigs: [new ColumnGroupConfig(null, 'TOTAL')], width: 60});

    this.addColumnFeqH(DataType.Numeric, 'gainLossSecurity', true, false,
      {columnGroupConfigs: [new ColumnGroupConfig('groupGainLossSecurity')]});

    this.internalColumnConfigs.push(this.addColumnFeqH(DataType.Numeric, 'gainLossSecurityMC', true, true,
      {
        columnGroupConfigs: [new ColumnGroupConfig('groupGainLossSecurityMC'),
          new ColumnGroupConfig('grandGainLossSecurityMC')]
      }));
/*
    this.addColumnFeqH(DataType.Numeric, 'currencyGainLossMC', true, false,
      { columnGroupConfigs: [new ColumnGroupConfig('groupCurrencyGainLossMC'),
          new ColumnGroupConfig('grandCurrencyGainLossMC')]});
*/

    this.addColumn(DataType.Numeric, 'accountValueSecurity', AppSettings.VALUE_SECURITY_ACCOUNT_HEADER, true, false,
      {columnGroupConfigs: [new ColumnGroupConfig('groupAccountValueSecurity')]});

    this.internalColumnConfigs.push(this.addColumn(DataType.Numeric, AppSettings.VALUE_SECURITY_MAIN_CURRENCY_FIELD,
      AppSettings.VALUE_SECURITY_ACCOUNT_HEADER,
      true, true, {
        width: 100,
        columnGroupConfigs: [new ColumnGroupConfig('groupAccountValueSecurityMC'),
          new ColumnGroupConfig('grandAccountValueSecurityMC')]
      }));

    this.securityaccountGroupBase.extendColumns(this.internalColumnConfigs);
    this.fields.filter(cc => cc.dataType === DataType.Numeric).map(cc => cc.templateName = 'greenRed');
  }

  protected getDataToView(data: SecurityPositionGrandSummary) {
    this.securityPositionSummary = data;
    this.securityPositionAll = [];
    this.transformToFlatArray(this.securityPositionSummary);
  }

  protected initTableTextTranslation() {
    this.internalColumnConfigs.forEach(columnConfig => columnConfig.headerSuffix = this.securityPositionSummary.currency);
    this.prepareTableAndTranslate();
    this.securityaccountGroupBase.translateGroupValues(<any[]>this.securityPositionSummary.securityPositionGroupSummaryList);
    this.changeToOpenChart();
  }

  protected addTimeSeriesMenus(security: Security, menuItems: MenuItem[], translate: boolean): MenuItem[] {
    if (security) {
      menuItems = menuItems.concat(this.timeSeriesQuotesService.getMenuItems(security.idSecuritycurrency, security.currency,
        true, this.addNoMarketValueOpt(security)));
      translate && TranslateHelper.translateMenuItems(menuItems, this.translateService);
    }
    return menuItems;
  }

  protected changeToOpenChart() {
    this.subscriptionRequestFromChart && this.chartDataService.sentToChart(
      this.securityaccountGroupBase.getChartDefinition(this.getTitleChart(), this.securityPositionSummary));
  }

  protected getTitleChart(): string {
    return this.titleElementRef.nativeElement.innerHTML.replace(/\s+/g, ' ');
  }

  protected getComponentId(): string {
    const routeUrlComponent = /(\/\w+\/)\({0,1}(\w+)/.exec(this.router.url);
    return routeUrlComponent[2];
  }

  private addNoMarketValueOpt(security: Security): OptionalParameters {
    const optionalParameters = this.getOptionalParameters() || {};
    optionalParameters.noMarketValue = security.stockexchange.noMarketValue;
    return optionalParameters;
  }

  private navigateToChartRoute() {
    !this.subscriptionRequestFromChart && this.prepareChartDataWithRequest();
    this.router.navigate([AppSettings.MAINVIEW_KEY + '/', {
      outlets: {
        mainbottom: [AppSettings.CHART_GENERAL_PURPOSE, this.getComponentId()]
      }
    }]);
  }

  private prepareChartDataWithRequest(): void {
    this.subscriptionRequestFromChart = this.chartDataService.requestFromChart$.subscribe(id => {
      if (id === this.getComponentId()) {
        this.chartDataService.sentToChart(this.securityaccountGroupBase.getChartDefinition(
          this.getTitleChart(), this.securityPositionSummary));
      }
    });
  }

  private resetMenu() {
    this.contextMenuItems = this.getEditMenu(this.selectedSecurityPositionSummary);
    this.activePanelService.activatePanel(this, {
      showMenu: this.getMenuShowOptionsParam(this.selectedSecurityPositionSummary ?
        this.selectedSecurityPositionSummary.security : null),
      editMenu: this.contextMenuItems
    });
    if (this.contextMenuItems) {
      this.contextMenuItems = this.addTimeSeriesMenus(this.selectedSecurityPositionSummary.security,
        this.contextMenuItems, true);
      this.contextMenuItems = this.addUrlLinkMenus(this.selectedSecurityPositionSummary.security, this.contextMenuItems, true);
    }
  }

  private getEditMenu(securityPositionSummary: SecurityPositionSummary): MenuItem[] {
    let menuItems: MenuItem[] = null;

    if (securityPositionSummary
      && (AssetclassType[securityPositionSummary.security.assetClass.categoryType] < AssetclassType.CURRENCY_PAIR
        || (AssetclassType[securityPositionSummary.security.assetClass.categoryType] === AssetclassType.CURRENCY_PAIR
          && SpecialInvestmentInstruments[securityPositionSummary.security.assetClass.specialInvestmentInstrument]
          === SpecialInvestmentInstruments.ISSUER_RISK_PRODUCT))) {
      menuItems = [];
      menuItems.push({
        label: 'ACCUMULATE',
        command: (e) => this.handleTransaction(TransactionType.ACCUMULATE, securityPositionSummary.security)
      });

      menuItems.push({
        label: 'REDUCE',
        command: (e) => this.handleTransaction(TransactionType.REDUCE, securityPositionSummary.security),
        disabled: !securityPositionSummary || securityPositionSummary.units === 0
      });

      if (!BusinessHelper.isMarginProduct(securityPositionSummary.security)) {
        menuItems.push({
          label: AppSettings.DIVIDEND.toUpperCase(),
          command: (e) => this.handleTransaction(TransactionType.DIVIDEND, securityPositionSummary.security)
        });
      }
      TranslateHelper.translateMenuItems(menuItems, this.translateService);
    }
    return menuItems;
  }

}
