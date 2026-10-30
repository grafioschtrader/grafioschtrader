import type { PanelClickEvent } from '../../lib/mainmenubar/component/iglobal.menu.attach';
import { httpResource } from '@angular/common/http';
import {
  Component,
  DestroyRef,
  OnDestroy,
  effect,
  inject,
  input,
  numberAttribute,
  signal,
  untracked,
  viewChild
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { ConfirmationService, MenuItem } from '@openng/optimus-ui/api';
import { ContextMenuModule } from '@openng/optimus-ui/contextmenu';
import { DatePickerModule } from '@openng/optimus-ui/datepicker';
import { TableModule } from '@openng/optimus-ui/table';
import { TooltipModule } from '@openng/optimus-ui/tooltip';
import { CashaccountEditComponent } from '../../cashaccount/component/cashaccount-edit.component';
import { CashaccountService } from '../../cashaccount/service/cashaccount.service';
import { Cashaccount } from '../../entities/cashaccount';
import { Portfolio } from '../../entities/portfolio';
import { AccountPositionGroupSummary } from '../../entities/view/account.position.group.summary';
import { AccountPositionSummary } from '../../entities/view/account.position.summary';
import { BaseSettings } from '../../lib/base.settings';
import { ColumnConfig, ColumnGroupConfig } from '../../lib/datashowbase/column.config';
import { TableConfigBase } from '../../lib/datashowbase/table.config.base';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { HelpIds } from '../../lib/help/help.ids';
import { AppHelper } from '../../lib/helper/app.helper';
import { TranslateHelper } from '../../lib/helper/translate.helper';
import { reportResourceErrors } from '../../lib/login/service/resource.error.report';
import { IGlobalMenuAttach } from '../../lib/mainmenubar/component/iglobal.menu.attach';
import { ActivePanelService } from '../../lib/mainmenubar/service/active.panel.service';
import { DataChangedService } from '../../lib/maintree/service/data.changed.service';
import { TreeNavigationStateService } from '../../lib/maintree/service/tree.navigation.state.service';
import { InfoLevelType } from '../../lib/message/info.leve.type';
import { MessageToastService } from '../../lib/message/message.toast.service';
import { ProcessedAction } from '../../lib/types/processed.action';
import { ProcessedActionData } from '../../lib/types/processed.action.data';
import { PerformanceReportDialogService } from '../../performanceperiod/service/performance-report-dialog.service';
import { AppSettings } from '../../shared/app.settings';
import { BusinessHelper } from '../../shared/helper/business.helper';
import { CallParam } from '../../shared/maintree/types/dialog.visible';
import { ParentChildRegisterService } from '../../shared/service/parent.child.register.service';
import { TenantLimit, TenantLimitTypes } from '../../shared/types/tenant.limit';
import { TransactionType } from '../../shared/types/transaction.type';
import { TenantService } from '../../tenant/service/tenant.service';
import { TransactionCashaccountEditDoubleComponent } from '../../transaction/component/transaction-cashaccount-editdouble.component';
import { TransactionCashaccountEditSingleComponent } from '../../transaction/component/transaction-cashaccount-editsingle.component';
import { TransactionCashaccountTableComponent } from '../../transaction/component/transaction-cashaccount-table.component';
import { TransactionCallParam } from '../../transaction/component/transaction.call.parm';

/**
 * Shows all cash accounts of a certain portfolio. Over this view the cash account can be edited.
 */
@Component({
  templateUrl: '../view/portfolio.cashaccount.table.html',
  imports: [
    FormsModule,
    TranslateModule,
    TableModule,
    DatePickerModule,
    ContextMenuModule,
    TooltipModule,
    CashaccountEditComponent,
    TransactionCashaccountEditSingleComponent,
    TransactionCashaccountEditDoubleComponent,
    TransactionCashaccountTableComponent
  ]
})
export class PortfolioCashaccountSummaryComponent extends TableConfigBase implements OnDestroy, IGlobalMenuAttach {
  readonly id = input.required({ transform: numberAttribute });

  private parentChildRegisterService: ParentChildRegisterService = inject(ParentChildRegisterService);
  private confirmationService: ConfirmationService = inject(ConfirmationService);
  private activePanelService: ActivePanelService = inject(ActivePanelService);
  private cashaccountService: CashaccountService = inject(CashaccountService);
  private messageToastService: MessageToastService = inject(MessageToastService);
  private dataChangedService: DataChangedService = inject(DataChangedService);
  private treeNavState: TreeNavigationStateService = inject(TreeNavigationStateService);
  private tenantService: TenantService = inject(TenantService);

  // @ViewChild('contextMenuPCD', {static: true}) contextMenu: ContextMenu;
  readonly contextMenu = viewChild<any>('contextMenuPCD');
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
  selectedAccountPositionSummary: AccountPositionSummary | null;
  contextMenuItems: MenuItem[] = [];
  private columnConfigs: ColumnConfig[] = [];
  private excludedDivTaxColumn: ColumnConfig;
  private tenantLimit: TenantLimit | null;

  private statementReports = inject(PerformanceReportDialogService);
  private reportDestroyRef = inject(DestroyRef);

  override getMenuShowOptions() {
    return [
      ...(super.getMenuShowOptions() || []),
      this.statementReports.menu(
        () => this.untilDate,
        () => this.portfolio?.idPortfolio,
        this.reportDestroyRef
      )
    ];
  }

  private readonly summaryRequest = signal<
    ReturnType<CashaccountService['getCashaccountPositionGroupSummaryRequest']> | undefined
  >(undefined);
  readonly positionResource = httpResource<AccountPositionGroupSummary>(() => this.summaryRequest());
  constructor() {
    super();

    this.addColumnFeqH(DataType.String, 'cashaccount.name', true, false, {
      width: 100,
      columnGroupConfigs: [new ColumnGroupConfig(null, 'GRAND_TOTAL')]
    });
    this.addColumnFeqH(DataType.String, 'cashaccount.currency', true, false, {
      width: 40
    });
    this.addColumn(DataType.Numeric, 'closePrice', 'CURRENCY_RATE', true, false, {
      width: 60,
      maxFractionDigits: this.gps.getMaxFractionDigits()
    });

    this.columnConfigs.push(
      this.addColumnFeqH(DataType.Numeric, 'externalCashTransferMC', true, false, {
        templateName: 'greenRed',
        columnGroupConfigs: [new ColumnGroupConfig('groupExternalCashTransferMC')]
      })
    );

    this.columnConfigs.push(
      this.addColumnFeqH(DataType.Numeric, 'cashTransferMC', true, false, {
        templateName: 'greenRed',
        columnGroupConfigs: [new ColumnGroupConfig('groupCashTransferMC')]
      })
    );

    this.columnConfigs.push(
      this.addColumnFeqH(DataType.Numeric, 'cashAccountTransactionFeeMC', true, false, {
        templateName: 'greenRed',
        columnGroupConfigs: [new ColumnGroupConfig('groupCashAccountTransactionFeeMC')]
      })
    );

    this.columnConfigs.push(
      this.addColumn(DataType.Numeric, 'accountFeesMC', 'FEE', true, false, {
        templateName: 'greenRed',
        columnGroupConfigs: [new ColumnGroupConfig('groupAccountFeesMC')]
      })
    );

    this.columnConfigs.push(
      this.addColumn(DataType.Numeric, 'accountInterestMC', 'INTEREST_CASHACCOUNT', true, false, {
        templateName: 'greenRed',
        columnGroupConfigs: [new ColumnGroupConfig('groupAccountInterestMC')]
      })
    );

    this.columnConfigs.push(
      this.addColumnFeqH(DataType.Numeric, 'gainLossCurrencyMC', true, false, {
        templateName: 'greenRed',
        columnGroupConfigs: [new ColumnGroupConfig('groupGainLossCurrencyMC')]
      })
    );

    this.columnConfigs.push(
      this.addColumnFeqH(DataType.Numeric, 'gainLossSecuritiesMC', true, false, {
        templateName: 'greenRed',
        columnGroupConfigs: [new ColumnGroupConfig('groupGainLossSecuritiesMC')]
      })
    );

    this.excludedDivTaxColumn = this.addColumnFeqH(DataType.Numeric, 'excludedDivTaxMC', false, false, {
      templateName: 'greenRed',
      columnGroupConfigs: [new ColumnGroupConfig('groupExcludedDivTaxMC')]
    });
    this.columnConfigs.push(this.excludedDivTaxColumn);

    this.columnConfigs.push(
      this.addColumn(DataType.Numeric, 'valueSecuritiesMC', AppSettings.SECURITY.toUpperCase(), true, false, {
        templateName: 'greenRed',
        columnGroupConfigs: [new ColumnGroupConfig('groupValueSecuritiesMC')]
      })
    );

    this.addColumnFeqH(DataType.Numeric, 'cashBalance', true, false, {
      templateName: 'greenRed',
      currencyPrecisionField: 'cashaccount.currency'
    });

    this.columnConfigs.push(
      this.addColumnFeqH(DataType.Numeric, 'cashBalanceMC', true, false, {
        templateName: 'greenRed',
        columnGroupConfigs: [new ColumnGroupConfig('groupCashBalanceMC')]
      })
    );
    this.columnConfigs.push(
      this.addColumn(DataType.Numeric, 'valueMC', 'TOTAL', true, false, {
        templateName: 'greenRed',
        width: 80,
        columnGroupConfigs: [new ColumnGroupConfig('groupValueMC')]
      })
    );
    this.multiSortMeta.push({ field: 'cashaccount.name', order: 1 });
    this.untilDate = BusinessHelper.getUntilDateBySessionStorage();

    effect(() => {
      const id = this.id();
      untracked(() => {
        this.portfolio = this.treeNavState.getEntity<any>(AppSettings.PORTFOLIO_SUMMARY_KEY, id);
        this.parentChildRegisterService.initRegistry();
        this.readData();
      });
    });

    reportResourceErrors(this.positionResource, this.cashaccountService);
    effect(() => {
      if (this.positionResource.isLoading() || !this.positionResource.hasValue()) return;
      const data = this.positionResource.value();
      untracked(() => {
        this.accountPositionGroupSummary = data;
        this.excludedDivTaxColumn.visible = this.accountPositionGroupSummary.excludeDivTax;
        this.expandedAccountPositionSummary = this.accountPositionGroupSummary.accountPositionSummaryList.filter(
          (accountPositionSummary) =>
            this.expandedIdCashaccount.indexOf(accountPositionSummary.cashaccount.idSecuritycashAccount) >= 0
        );
        this.columnConfigs.forEach((columnConfig) => {
          columnConfig.headerSuffix = this.accountPositionGroupSummary.currency;
          columnConfig.fixedCurrency = this.accountPositionGroupSummary.currency;
        });
        this.prepareTableAndTranslate();
        this.isActivated() && this.resetMenu(null);
        this.notifyViewChanged();
      });
    });
  }

  ngOnDestroy(): void {
    BusinessHelper.saveUntilDateInSessionStorage(this.untilDate);
    this.activePanelService.destroyPanel(this);
  }

  readData(): void {
    const id = this.portfolio?.idPortfolio;
    if (id == null) return;
    const request = this.cashaccountService.getCashaccountPositionGroupSummaryRequest(id, this.untilDate);
    const current = this.summaryRequest();
    if (current?.url === request.url && current.params.toString() === request.params.toString())
      this.positionResource.reload();
    else this.summaryRequest.set(request);
  }

  handleDeleteCashaccount(idSecuritycashAccount: number) {
    AppHelper.confirmationDialog(
      this.translateService,
      this.confirmationService,
      'MSG_CONFIRM_DELETE_RECORD|CASHACCOUNT',
      () => {
        this.cashaccountService.deleteCashaccount(idSecuritycashAccount).subscribe((response) => {
          this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'MSG_DELETE_RECORD', {
            i18nRecord: AppSettings.CASHACCOUNT.toUpperCase()
          });
          this.tenantLimit = null;
          this.readData();
          this.dataChangedService.dataHasChanged(new ProcessedActionData(ProcessedAction.DELETED, new Cashaccount()));
        });
      }
    );
  }

  prepareEditMenu(accountPositionSummary: AccountPositionSummary | null): MenuItem[] {
    const menuItems: MenuItem[] = [];

    menuItems.push({
      label: 'NEW|CASHACCOUNT' + BaseSettings.DIALOG_MENU_SUFFIX,
      command: (event) => this.handleNewAccount(this.portfolio)
    });

    if (accountPositionSummary) {
      menuItems.push({
        label: 'EDIT_RECORD|CASHACCOUNT' + BaseSettings.DIALOG_MENU_SUFFIX,
        command: (e) =>
          accountPositionSummary
            ? this.handleEditAccount(this.portfolio, accountPositionSummary.cashaccount, {
                hasTransaction: accountPositionSummary.hasTransaction
              })
            : null
      });

      menuItems.push({
        label: 'DELETE_RECORD|CASHACCOUNT',
        command: (e) =>
          accountPositionSummary
            ? this.handleDeleteCashaccount(accountPositionSummary.cashaccount.idSecuritycashAccount)
            : null,
        disabled: accountPositionSummary.hasTransaction
      });
      menuItems.push({ separator: true });
      menuItems.push({
        label: 'SINGLE_ACCOUNT_TRANSACTION' + BaseSettings.DIALOG_MENU_SUFFIX,
        command: (e) =>
          accountPositionSummary
            ? this.handleSingleAccountTransaction(TransactionType.WITHDRAWAL, accountPositionSummary.cashaccount)
            : null
      });

      menuItems.push({
        label: 'ACCOUNT_TRANSFER' + BaseSettings.DIALOG_MENU_SUFFIX,
        command: (e) =>
          accountPositionSummary ? this.handleDoubleAccountTransaction(accountPositionSummary.cashaccount) : null
      });
    }

    TranslateHelper.translateMenuItems(menuItems, this.translateService);
    return menuItems;
  }

  ////////////////////////////////////////////////
  onRowExpand(event: { data: { cashaccount: { idSecuritycashAccount: number } } }): void {
    this.expandedIdCashaccount.push(event.data.cashaccount.idSecuritycashAccount);
  }

  ////////////////////////////////////////////////
  // Event handler

  onRowCollapse(event: { data: { cashaccount: { idSecuritycashAccount: number } } }): void {
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
    // The command may come from the menu bar, outside this view
    this.notifyViewChanged();
  }

  handleDoubleAccountTransaction(cashaccount: Cashaccount): void {
    this.transactionCallParam = Object.assign(new TransactionCallParam(), {
      cashaccount
    });
    this.visibleCashaccountTransactionDoubleDialog = true;
    this.notifyViewChanged();
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

  public override filterDate(event: unknown) {
    this.readData();
  }

  onResetToDay(event: unknown): void {
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
    this.parentChildRegisterService.callChildrenToPreserve(
      processedActionData.action !== ProcessedAction.DELETED ? processedActionData.data : null
    );
  }

  isActivated(): boolean {
    return this.activePanelService.isActivated(this);
  }

  callMeDeactivate(): void {}

  onComponentClick(event: PanelClickEvent): void {
    if (!event[this.consumedGT]) {
      this.resetMenu(this.selectedAccountPositionSummary);
    }
  }

  hideContextMenu(): void {
    this.contextMenu().hide();
  }

  public getHelpContextId(): string {
    return HelpIds.HELP_PORTFOLIOS_PORTFOLIOS;
  }

  /**
   * Lists the currency pairs that could not be converted, for the warning shown above the table. It is only these
   * accounts that are missing from the totals, so naming them tells the user exactly what the shown sums leave out.
   *
   * @returns the affected pairs as a readable enumeration, for example 'BTC/CHF', or null when nothing is missing
   */
  get missingCurrenciesText(): string | null {
    const missingExchangeRates = this.accountPositionGroupSummary?.missingExchangeRates;
    return missingExchangeRates?.length
      ? missingExchangeRates.map((mer) => `${mer.fromCurrency}/${mer.toCurrency}`).join(', ')
      : null;
  }

  onContextMenuClick(event: unknown) {
    if (!this.isActivated()) {
      setTimeout(() => this.contextMenu().hide());
    }
  }

  private handleNewAccount(portfolio: Portfolio) {
    if (this.tenantLimit) {
      this.handleEditAccount(portfolio, null, null);
    } else {
      this.tenantService.getMaxTenantLimitsByMsgKey([TenantLimitTypes.MAX_CASH_ACCOUNT]).subscribe((tenantLimits) => {
        this.tenantLimit = tenantLimits[0];
        if (BusinessHelper.isLimitCheckOk(tenantLimits[0], this.messageToastService)) {
          this.handleEditAccount(portfolio, null, null);
        }
      });
    }
  }

  private handleEditAccount(
    portfolio: Portfolio,
    cashaccount: Cashaccount | null,
    optParam: { [key: string]: boolean } | null
  ) {
    // Pre-fetch the entity form definition (memoised) before opening the dialog, so the dialog can build
    // its dynamic form synchronously in ngOnInit and edit values populate reliably.
    this.gps.getEntityFormDefinition(AppSettings.CASHACCOUNT).subscribe((formDefinition) => {
      this.callParam = new CallParam(portfolio, cashaccount, {
        ...optParam,
        formDefinition
      });
      this.visibleCashaccountDialog = true;
      this.notifyViewChanged();
    });
  }

  private resetMenu(selectedAccountPositionSummary: AccountPositionSummary | null): void {
    this.selectedAccountPositionSummary = selectedAccountPositionSummary;
    this.contextMenuItems = this.prepareEditMenu(this.selectedAccountPositionSummary);
    this.activePanelService.activatePanel(this, {
      showMenu: this.getMenuShowOptions(),
      editMenu: this.contextMenuItems
    });
  }
}
