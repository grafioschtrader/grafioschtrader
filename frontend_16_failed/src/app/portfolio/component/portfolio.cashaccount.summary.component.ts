import {Component, OnDestroy, OnInit, ViewChild} from '@angular/core';
import {CashaccountService} from '../../cashaccount/service/cashaccount.service';
import {ActivatedRoute, Params} from '@angular/router';
import {Cashaccount} from '../../entities/cashaccount';
import {AccountPositionGroupSummary} from '../../entities/view/account.position.group.summary';
import {TransactionType} from '../../shared/types/transaction.type';
import {TranslateService} from '@ngx-translate/core';
import {AccountPositionSummary} from '../../entities/view/account.position.summary';
import {TransactionCallParam} from '../../transaction/component/transaction.call.parm';
import {Portfolio} from '../../entities/portfolio';
import {ProcessedAction} from '../../shared/types/processed.action';
import {ProcessedActionData} from '../../shared/types/processed.action.data';
import {AppHelper} from '../../shared/helper/app.helper';
import {ActivePanelService} from '../../shared/mainmenubar/service/active.panel.service';
import {IGlobalMenuAttach} from '../../shared/mainmenubar/component/iglobal.menu.attach';
import {CallParam} from '../../shared/maintree/types/dialog.visible';
import {InfoLevelType} from '../../shared/message/info.leve.type';
import {MessageToastService} from '../../shared/message/message.toast.service';
import {Subscription} from 'rxjs';
import {DataChangedService} from '../../shared/maintree/service/data.changed.service';
import {ParentChildRegisterService} from '../../shared/service/parent.child.register.service';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {UserSettingsService} from '../../shared/service/user.settings.service';
import {DataType} from '../../dynamic-form/models/data.type';
import {TableConfigBase} from '../../shared/datashowbase/table.config.base';
import {ColumnConfig, ColumnGroupConfig} from '../../shared/datashowbase/column.config';
import {HelpIds} from '../../shared/help/help.ids';
import {TenantLimit, TenantLimitTypes} from '../../entities/backend/tenant.limit';
import {TranslateHelper} from '../../shared/helper/translate.helper';
import {BusinessHelper} from '../../shared/helper/business.helper';
import {ConfirmationService, FilterService, MenuItem} from 'primeng/api';
import {AppSettings} from '../../shared/app.settings';


/**
 * Shows all cash accounts of a certain portfolio. Over this view the cash account can be edited.
 */
@Component({
  templateUrl: '../view/portfolio.cashaccount.table.html'
})
export class PortfolioCashaccountSummaryComponent extends TableConfigBase implements OnInit, OnDestroy, IGlobalMenuAttach {

  // @ViewChild('contextMenuPCD', {static: true}) contextMenu: ContextMenu;
  @ViewChild('contextMenuPCD') contextMenu: any;
  untilDate: Date;

  // For  transaction dialog
  visibleCashaccountTransactionSingleDialog = false;
  visibleCashaccountTransactionDoubleDialog = false;
  transactionCallParam: TransactionCallParam;

  // For edit Account dialog
  callParam: CallParam;
  visibleCashaccountDialog = false;

  public portfolio: Portfolio;
  accountPositionGroupSummary: AccountPositionGroupSummary;
  // private idPortfolio: number;
  // For row expansion, used when a transaction was changed
  expandedAccountPositionSummary: AccountPositionSummary[] = [];
  expandedIdCashaccount: number[] = [];
  // For the component Edit-Menu, it shows the same menu items as the context menu
  selectedAccountPositionSummary: AccountPositionSummary;
  contextMenuItems: MenuItem[] = [];
  private routeSubscribe: Subscription;
  private columnConfigs: ColumnConfig[] = [];
  private tenantLimit: TenantLimit;

  constructor(private parentChildRegisterService: ParentChildRegisterService,
              private confirmationService: ConfirmationService,
              private activePanelService: ActivePanelService,
              private cashaccountService: CashaccountService,
              private messageToastService: MessageToastService,
              private activatedRoute: ActivatedRoute,
              private dataChangedService: DataChangedService,
              filterService: FilterService,
              translateService: TranslateService,
              gps: GlobalparameterService,
              usersettingsService: UserSettingsService) {
    super(filterService, usersettingsService, translateService, gps);

    this.addColumnFeqH(DataType.String, 'cashaccount.name', true, false,
      {width: 100, columnGroupConfigs: [new ColumnGroupConfig(null, 'GRAND_TOTAL')]});
    this.addColumnFeqH(DataType.String, 'cashaccount.currency', true, false,
      {width: 40});
    this.addColumn(DataType.Numeric, 'closePrice', 'CURRENCY_RATE', true,
      false, {width: 60, maxFractionDigits: AppSettings.FID_MAX_FRACTION_DIGITS});

    this.columnConfigs.push(this.addColumnFeqH(DataType.Numeric, 'externalCashTransferMC', true, false,
      {templateName: 'greenRed', columnGroupConfigs: [new ColumnGroupConfig('groupExternalCashTransferMC')]}));

    this.columnConfigs.push(this.addColumnFeqH(DataType.Numeric, 'cashTransferMC', true, false,
      {templateName: 'greenRed', columnGroupConfigs: [new ColumnGroupConfig('groupCashTransferMC')]}));

    this.columnConfigs.push(this.addColumnFeqH(DataType.Numeric, 'cashAccountTransactionFeeMC', true, false,
      {templateName: 'greenRed', columnGroupConfigs: [new ColumnGroupConfig('groupCashAccountTransactionFeeMC')]}));

    this.columnConfigs.push(this.addColumn(DataType.Numeric, 'accountFeesMC', 'FEE', true, false,
      {templateName: 'greenRed', columnGroupConfigs: [new ColumnGroupConfig('groupAccountFeesMC')]}));

    this.columnConfigs.push(this.addColumn(DataType.Numeric, 'accountInterestMC', 'INTEREST_CASHACCOUNT', true, false,
      {templateName: 'greenRed', columnGroupConfigs: [new ColumnGroupConfig('groupAccountInterestMC')]}));

    this.columnConfigs.push(this.addColumnFeqH(DataType.Numeric, 'gainLossCurrencyMC', true, false,
      {templateName: 'greenRed', columnGroupConfigs: [new ColumnGroupConfig('groupGainLossCurrencyMC')]}));

    this.columnConfigs.push(this.addColumnFeqH(DataType.Numeric, 'gainLossSecuritiesMC', true, false,
      {templateName: 'greenRed', columnGroupConfigs: [new ColumnGroupConfig('groupGainLossSecuritiesMC')]}));

    this.columnConfigs.push(this.addColumn(DataType.Numeric, 'valueSecuritiesMC', AppSettings.SECURITY.toUpperCase(), true, false,
      {templateName: 'greenRed', columnGroupConfigs: [new ColumnGroupConfig('groupValueSecuritiesMC')]}));

    this.addColumnFeqH(DataType.Numeric, 'cashBalance', true, false, {templateName: 'greenRed'});

    this.columnConfigs.push(this.addColumnFeqH(DataType.Numeric, 'cashBalanceMC', true, false,
      {templateName: 'greenRed', columnGroupConfigs: [new ColumnGroupConfig('groupCashBalanceMC')]}));
    this.columnConfigs.push(this.addColumn(DataType.Numeric, 'valueMC', 'TOTAL', true, false,
      {templateName: 'greenRed', width: 80, columnGroupConfigs: [new ColumnGroupConfig('groupValueMC')]}));
    this.multiSortMeta.push({field: 'cashaccount.name', order: 1});
    this.untilDate = AppHelper.getUntilDateBySessionStorage();
  }

  ngOnInit(): void {
    this.routeSubscribe = this.activatedRoute.params.subscribe((params: Params) => {
      this.portfolio = JSON.parse(params[AppSettings.PORTFOLIO.toLowerCase()]);
      this.parentChildRegisterService.initRegistry();
      this.readData();
    });
  }

  ngOnDestroy(): void {
    AppHelper.saveUntilDateInSessionStorage(this.untilDate);
    this.activePanelService.destroyPanel(this);
    this.routeSubscribe.unsubscribe();
  }

  readData(): void {
    this.cashaccountService.getCashaccountPositionGroupSummary(this.portfolio.idPortfolio, this.untilDate)
      .subscribe((data: AccountPositionGroupSummary) => {
        this.accountPositionGroupSummary = data;
        this.expandedAccountPositionSummary = this.accountPositionGroupSummary.accountPositionSummaryList.filter(accountPositionSummary =>
          this.expandedIdCashaccount.indexOf(accountPositionSummary.cashaccount.idSecuritycashAccount) >= 0);
        this.columnConfigs.forEach(columnConfig => columnConfig.headerSuffix = this.accountPositionGroupSummary.currency);
        this.prepareTableAndTranslate();
        this.isActivated() && this.resetMenu(null);
      });
  }

  handleDeleteCashaccount(idSecuritycashAccount: number) {
    AppHelper.confirmationDialog(this.translateService, this.confirmationService,
      'MSG_CONFIRM_DELETE_RECORD|CASHACCOUNT', () => {
        this.cashaccountService.deleteCashaccount(idSecuritycashAccount).subscribe(response => {
          this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS,
            'MSG_DELETE_RECORD', {i18nRecord: AppSettings.CASHACCOUNT.toUpperCase()});
          this.tenantLimit = null;
          this.readData();
          this.dataChangedService.dataHasChanged(new ProcessedActionData(ProcessedAction.DELETED, new Cashaccount()));
        });
      });
  }

  prepareEditMenu(accountPositionSummary: AccountPositionSummary): MenuItem[] {
    const menuItems: MenuItem[] = [];

    menuItems.push({
      label: 'NEW|CASHACCOUNT' + AppSettings.DIALOG_MENU_SUFFIX,
      command: (event) => this.handleNewAccount(this.portfolio)
    });

    if (accountPositionSummary) {
      menuItems.push({
        label: 'EDIT_RECORD|CASHACCOUNT' + AppSettings.DIALOG_MENU_SUFFIX,
        command: (e) => (accountPositionSummary) ? this.handleEditAccount(this.portfolio, accountPositionSummary.cashaccount,
          {hasTransaction: accountPositionSummary.hasTransaction}) : null
      });

      menuItems.push({
        label: 'DELETE_RECORD|CASHACCOUNT',
        command: (e) => (accountPositionSummary) ?
          this.handleDeleteCashaccount(accountPositionSummary.cashaccount.idSecuritycashAccount) : null,
        disabled: accountPositionSummary.hasTransaction
      });
      menuItems.push({separator: true});
      menuItems.push({
        label: 'SINGLE_ACCOUNT_TRANSACTION' + AppSettings.DIALOG_MENU_SUFFIX,
        command: (e) => (accountPositionSummary) ? this.handleSingleAccountTransaction(TransactionType.WITHDRAWAL,
          accountPositionSummary.cashaccount) : null
      });

      menuItems.push({
        label: 'ACCOUNT_TRANSFER' + AppSettings.DIALOG_MENU_SUFFIX,
        command: (e) => (accountPositionSummary) ? this.handleDoubleAccountTransaction(accountPositionSummary.cashaccount) : null
      });
    }

    TranslateHelper.translateMenuItems(menuItems, this.translateService);
    return menuItems;
  }

  ////////////////////////////////////////////////
  onRowExpand(event): void {
    this.expandedIdCashaccount.push(event.data.cashaccount.idSecuritycashAccount);
  }

  ////////////////////////////////////////////////
  // Event handler

  onRowCollapse(event): void {
    const index = this.expandedIdCashaccount.indexOf(event.data.cashaccount.idSecuritycashAccount);
    this.expandedIdCashaccount.splice(index, 1);
  }

  handleSingleAccountTransaction(transactionType: TransactionType, cashaccount: Cashaccount): void {
    this.transactionCallParam = Object.assign(new TransactionCallParam(), {
      transactionType,
      portfolio: this.portfolio,
      cashaccount
    });
    this.visibleCashaccountTransactionSingleDialog = true;
  }

  handleDoubleAccountTransaction(cashaccount: Cashaccount): void {
    this.transactionCallParam = Object.assign(new TransactionCallParam(), {
      cashaccount
    });
    this.visibleCashaccountTransactionDoubleDialog = true;
  }

  /**
   * Data on the cash account table row or on the panel was changed.
   *
   */
  handleCloseCashaccountDialog(processedActionData: ProcessedActionData) {
    this.visibleCashaccountDialog = false;
    if (processedActionData.action !== ProcessedAction.NO_CHANGE) {
      if (processedActionData.action === ProcessedAction.CREATED) {
        // Inform date parent view, because a new cash account is added
        this.tenantLimit = null;
        this.dataChangedService.dataHasChanged(processedActionData);
      }
      this.readData();
    }
  }

  public filterDate(event) {
    this.readData();
  }

  onResetToDay(event): void {
    this.untilDate = new Date();
    this.readData();
  }

  /**
   * Changes on the table row reflects one or two transaction/s.
   */
  handleCloseDialog(processedActionData: ProcessedActionData) {
    this.visibleCashaccountTransactionSingleDialog = false;
    this.visibleCashaccountTransactionDoubleDialog = false;
    if (processedActionData.action !== ProcessedAction.NO_CHANGE) {
      this.transactionDataChanged(processedActionData);
    }
  }

  /**
   * Data of the child transaction table was changed, because this table must be loaded.
   */
  transactionDataChanged(processedActionData: ProcessedActionData) {
    this.readData();
    // Try to go to previous page and row selection
    this.parentChildRegisterService.callChildrenToPreserve(processedActionData.action !== ProcessedAction.DELETED ?
      processedActionData.data : null);
  }

  isActivated(): boolean {
    return this.activePanelService.isActivated(this);
  }

  callMeDeactivate(): void {
  }

  onComponentClick(event): void {
    if (!event[this.consumedGT]) {
      this.resetMenu(this.selectedAccountPositionSummary);
    }
  }

  hideContextMenu(): void {
    this.contextMenu.hide();
  }

  public getHelpContextId(): HelpIds {
    return HelpIds.HELP_PROTFOLIOS_PORTFOLIOS;
  }

  onContextMenuClick(event) {
    if (!this.isActivated()) {
      setTimeout(() => this.contextMenu.hide());
    }
  }

  private handleNewAccount(portfolio: Portfolio) {
    if (this.tenantLimit) {
      this.handleEditAccount(portfolio, null, null);
    } else {
      this.gps.getMaxTenantLimitsByMsgKey([TenantLimitTypes.MAX_CASH_ACCOUNT]).subscribe(tenantLimits => {
        this.tenantLimit = tenantLimits[0];
        if (BusinessHelper.isLimitCheckOk(tenantLimits[0], this.messageToastService)) {
          this.handleEditAccount(portfolio, null, null);
        }
      });
    }
  }

  private handleEditAccount(portfolio: Portfolio, cashaccount: Cashaccount, optParam: { [key: string]: boolean }) {
    this.callParam = new CallParam(portfolio, cashaccount, optParam);
    this.visibleCashaccountDialog = true;
  }

  private resetMenu(selectedAccountPositionSummary: AccountPositionSummary): void {
    this.selectedAccountPositionSummary = selectedAccountPositionSummary;
    this.contextMenuItems = this.prepareEditMenu(this.selectedAccountPositionSummary);
    this.activePanelService.activatePanel(this, {editMenu: this.contextMenuItems});
  }

}
