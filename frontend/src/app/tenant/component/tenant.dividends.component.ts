import { Component, Injector, OnDestroy, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { PortfolioService } from '../../portfolio/service/portfolio.service';
import { SecurityDividendsGrandTotal } from '../../entities/view/securitydividends/security.dividends.grand.total';
import { ActivatedRoute } from '@angular/router';
import { SecurityDividendsYearGroup } from '../../entities/view/securitydividends/security.dividends.year.group';
import { TableConfigBase } from '../../lib/datashowbase/table.config.base';
import { TranslateService, TranslateModule } from '@ngx-translate/core';
import { UserSettingsService } from '../../lib/services/user.settings.service';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { IGlobalMenuAttach } from '../../lib/mainmenubar/component/iglobal.menu.attach';
import { ActivePanelService } from '../../lib/mainmenubar/service/active.panel.service';
import { GlobalparameterService } from '../../lib/services/globalparameter.service';
import { ColumnConfig, ColumnGroupConfig } from '../../lib/datashowbase/column.config';
import { HelpIds } from '../../lib/help/help.ids';
import { FilterService, MenuItem } from '@openng/optimus-ui/api';
import { ProcessedActionData } from '../../lib/types/processed.action.data';
import { ProcessedAction } from '../../lib/types/processed.action';
import { TranslateHelper } from '../../lib/helper/translate.helper';
import { IdsAccounts } from '../model/ids.accounts';
import { AppSettings } from '../../shared/app.settings';
import { BaseSettings } from '../../lib/base.settings';
import { CommonModule } from '@angular/common';
import { TableModule } from '@openng/optimus-ui/table';
import { TooltipModule } from '@openng/optimus-ui/tooltip';
import { ContextMenuModule } from '@openng/optimus-ui/contextmenu';
import { TransactionService } from '../../transaction/service/transaction.service';
import { MessageToastService } from '../../lib/message/message.toast.service';
import { InfoLevelType } from '../../lib/message/info.leve.type';
import { ExDateFromTaxDataResult } from '../../entities/ex.date.from.tax.data.result';
import { TenantDividendSecurityAccountSelectionDialogComponent } from './tenant-dividend-security-account-selection-dialog.component';
import { TenantDividendsCashaccountExtendedComponent } from './tenant-dividends-cashaccount-extended.component';
import { TenantDividendsSecurityExtendedComponent } from './tenant-dividends-security-extended.component';
import { TaxStatementExportDialogComponent } from '../../taxdata/component/tax-statement-export-dialog.component';

/**
 * Shows the dividends and some other information like transaction cost grouped by year.
 */
@Component({
  templateUrl: '../view/tenant.dividends.html',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.Eager,
  imports: [
    CommonModule,
    TranslateModule,
    TableModule,
    TooltipModule,
    ContextMenuModule,
    TenantDividendSecurityAccountSelectionDialogComponent,
    TenantDividendsCashaccountExtendedComponent,
    TenantDividendsSecurityExtendedComponent,
    TaxStatementExportDialogComponent
  ]
})
export class TenantDividendsComponent extends TableConfigBase implements IGlobalMenuAttach, OnInit, OnDestroy {
  securityDividendsGrandTotal: SecurityDividendsGrandTotal;
  securityDividendsYearGroup: SecurityDividendsYearGroup[];

  securityDividendsGrandTotalSelected: SecurityDividendsYearGroup;
  visibleSecurityaccountDialog: boolean;
  visibleExportDialog: boolean;
  idsAccounts: IdsAccounts;
  filterTransactionsToYearEnd = false;
  contextMenuItems: MenuItem[] = [];

  /**
   * Tracks which year rows are expanded, keyed by the {@code year} dataKey value. Bound two-way on the top table so the
   * expansion survives a {@code readData()} reload (e.g. after a TAXABLE_INTEREST_TOGGLE) instead of collapsing.
   */
  expandedYearKeys: { [year: string]: boolean } = {};

  /**
   * Per-year expansion state of the nested security table. The security table lives inside a child component that is
   * recreated on every parent reload, so its expansion map is owned here and passed back into each child by reference.
   * Outer key is the {@code year}; inner key is {@code security.idSecuritycurrency}.
   */
  securityExpandedKeysByYear: { [year: string]: { [id: string]: boolean } } = {};

  private columnConfigs: ColumnConfig[] = [];

  constructor(
    private portfolioService: PortfolioService,
    private activatedRoute: ActivatedRoute,
    private activePanelService: ActivePanelService,
    private transactionService: TransactionService,
    private messageToastService: MessageToastService,
    filterService: FilterService,
    translateService: TranslateService,
    gps: GlobalparameterService,
    usersettingsService: UserSettingsService,
    injector: Injector
  ) {
    super(filterService, usersettingsService, translateService, gps, injector);
  }

  get selectedSecurityAccounts(): number {
    return this.idsAccounts.idsSecurityaccount.length === 0
      ? this.securityDividendsGrandTotal.numberOfSecurityAccounts
      : this.idsAccounts.idsSecurityaccount.length;
  }

  get selectedCashAccounts(): number {
    return this.idsAccounts.idsCashaccount.length === 1 && this.idsAccounts.idsCashaccount[0] === -1
      ? this.securityDividendsGrandTotal.numberOfCashAccounts
      : this.idsAccounts.idsCashaccount.length;
  }

  get totalSecurityAccounts(): number {
    return this.securityDividendsGrandTotal ? this.securityDividendsGrandTotal.numberOfSecurityAccounts : 0;
  }

  get totalCashAccounts(): number {
    return this.securityDividendsGrandTotal ? this.securityDividendsGrandTotal.numberOfCashAccounts : 0;
  }

  /** True for Swiss tenants; gates the ICTax columns and the eCH-0196 tax statement export. */
  private get isSwissTenant(): boolean {
    return this.securityDividendsGrandTotal?.tenantCountry === 'CH';
  }

  ngOnInit(): void {
    // use string to avoid number format
    this.addColumn(DataType.String, 'year', 'YEAR', true, false);
    this.columnConfigs.push(
      this.addColumn(DataType.Numeric, 'yearInterestMC', 'INTEREST_CASHACCOUNT', true, false, {
        columnGroupConfigs: [new ColumnGroupConfig('grandInterestMC')]
      })
    );

    this.columnConfigs.push(
      this.addColumn(DataType.Numeric, 'securityCostGroup.groupTotalTaxCostMc', 'TRANSACTION_TAX_COST', true, false, {
        columnGroupConfigs: [new ColumnGroupConfig('grandTotalTaxCostMC')]
      })
    );

    this.addColumn(DataType.NumericInteger, 'yearCountPaidTransactions', 'PAID_TRANSACTIONS', true, false, {
      columnGroupConfigs: [new ColumnGroupConfig('grandCountPaidTransaction')]
    });
    this.columnConfigs.push(
      this.addColumn(
        DataType.Numeric,
        'securityCostGroup.groupTotalAverageTransactionCostMC',
        'TRANSACTION_AVERAGE_PAID',
        true,
        false,
        {
          columnGroupConfigs: [new ColumnGroupConfig('grandTotalAverageTransactionCostMC')]
        }
      )
    );
    this.columnConfigs.push(
      this.addColumn(
        DataType.Numeric,
        'securityCostGroup.groupTotalTransactionCostMC',
        'TRANSACTION_COST',
        true,
        false,
        {
          columnGroupConfigs: [new ColumnGroupConfig('grandTotalTransactionCostMC')]
        }
      )
    );
    this.columnConfigs.push(
      this.addColumn(DataType.Numeric, 'yearFeeMC', 'FEE', true, false, {
        columnGroupConfigs: [new ColumnGroupConfig('grandFeeMC')]
      })
    );
    this.columnConfigs.push(
      this.addColumn(DataType.Numeric, 'yearFinanceCostMC', 'MARGIN_FINANCE_COST', false, true, {
        width: 80,
        columnGroupConfigs: [new ColumnGroupConfig('grandFinanceCostMC')]
      })
    );
    this.columnConfigs.push(this.addColumnFeqH(DataType.Numeric, 'yearAutoPaidTaxMC', true, false));
    this.columnConfigs.push(
      this.addColumnFeqH(DataType.Numeric, 'yearTaxableAmountMC', true, false, {
        columnGroupConfigs: [new ColumnGroupConfig('grandTaxableAmountMC')]
      })
    );
    this.columnConfigs.push(
      this.addColumnFeqH(DataType.Numeric, 'yearRealReceivedDivInterestMC', true, false, {
        columnGroupConfigs: [new ColumnGroupConfig('grandRealReceivedDivInterestMC')]
      })
    );

    this.columnConfigs.push(
      this.addColumn(DataType.Numeric, 'valueAtEndOfYearMC', 'VALUE_AT_END_OF_YEAR', true, false)
    );
    this.addColumn(DataType.Numeric, 'yearIctaxTotalPaymentValueChf', 'ICTAX_TOTAL_PAYMENT_CHF', false, true);
    this.addColumn(DataType.Numeric, 'yearIctaxTotalTaxValueChf', 'ICTAX_TOTAL_TAX_VALUE', false, true);

    const idsCashaccount = this.getAccountSettings(AppSettings.DIV_CASHACCOUNTS);
    this.idsAccounts = new IdsAccounts(
      this.getAccountSettings(AppSettings.DIV_SECURITYACCOUNTS),
      idsCashaccount.length === 0 ? [-1] : idsCashaccount
    );
    this.readData();
    this.multiSortMeta.push({ field: 'year', order: 1 });
    this.onComponentClick(null);
    this.buildContextMenu();
  }

  private getAccountSettings(propertyKey: string): any[] {
    return this.usersettingsService.readArray(this.getStorePropertyPrefix() + propertyKey);
  }

  private writeAccountSettings(propertyKey: string, values: any[]): void {
    this.usersettingsService.saveArray(this.getStorePropertyPrefix() + propertyKey, values);
  }

  private getStorePropertyPrefix(): string {
    return this.gps.getIdTenant() + '_';
  }

  isActivated(): boolean {
    return this.activePanelService.isActivated(this);
  }

  hideContextMenu(): void {}

  callMeDeactivate(): void {}

  onComponentClick(event): void {
    if (!event || !event[this.consumedGT]) {
      this.activePanelService.activatePanel(this, {
        showMenu: this.getMenuShowOptions()
      });
    }
  }

  public getHelpContextId(): string {
    return HelpIds.HELP_PORTFOLIOS_DIVIDENDS;
  }

  ngOnDestroy(): void {
    this.activePanelService.destroyPanel(this);
  }

  override getMenuShowOptions(): MenuItem[] {
    const menuItems: MenuItem[] = [
      {
        label: 'DIV_INCLUDE_SECURITYACCOUNT',
        command: (event) => this.showPortfolioSelectionDialog()
      }
    ];
    if (this.isSwissTenant && this.securityDividendsGrandTotal?.availableTaxYears?.length > 0) {
      menuItems.push({
        label: 'EXPORT_TAX_STATEMENT',
        command: (event) => this.showExportDialog()
      });
    }
    menuItems.push(
      { separator: true },
      {
        label: 'FILTER_TRANSACTIONS_TO_YEAR_END',
        command: (event) => this.handleFilterTransactionsToggle(event),
        icon: this.filterTransactionsToYearEnd ? BaseSettings.ICONNAME_SQUARE_CHECK : BaseSettings.ICONNAME_SQUARE_EMTPY
      }
    );
    TranslateHelper.translateMenuItems(menuItems, this.translateService);
    return menuItems;
  }

  /**
   * Rebuilds the year-row right-click context menu. The "set ex-date from tax data" item is disabled when no year is
   * selected or the selected year has no imported tax data (same {@code availableTaxYears} gate as the export action).
   */
  private buildContextMenu(): void {
    const year = this.securityDividendsGrandTotalSelected?.year;
    const hasTaxData = year != null && !!this.securityDividendsGrandTotal?.availableTaxYears?.includes(year);
    const menuItems: MenuItem[] = [
      {
        label: 'SET_EX_DATE_FROM_TAX_DATA',
        command: () => this.handleApplyExDatesFromTaxData(),
        disabled: !hasTaxData
      }
    ];
    TranslateHelper.translateMenuItems(menuItems, this.translateService);
    this.contextMenuItems = menuItems;
  }

  onContextMenuSelect(event: any): void {
    this.securityDividendsGrandTotalSelected = event.data;
    this.buildContextMenu();
  }

  handleApplyExDatesFromTaxData(): void {
    const year = this.securityDividendsGrandTotalSelected?.year;
    if (year == null) {
      return;
    }
    this.transactionService.applyExDatesFromTaxData(year).subscribe((result: ExDateFromTaxDataResult) => {
      this.messageToastService.showMessageI18n(InfoLevelType.INFO, 'EX_DATE_FROM_TAX_DATA_RESULT', {
        assigned: result.exDateAssigned,
        alreadySet: result.alreadySet,
        unmatched: result.unmatched
      });
      this.readData();
    });
  }

  handleFilterTransactionsToggle(event: any): void {
    if (event.item.icon === BaseSettings.ICONNAME_SQUARE_EMTPY) {
      event.item.icon = BaseSettings.ICONNAME_SQUARE_CHECK;
      this.filterTransactionsToYearEnd = true;
    } else {
      event.item.icon = BaseSettings.ICONNAME_SQUARE_EMTPY;
      this.filterTransactionsToYearEnd = false;
    }
    this.onComponentClick(null);
  }

  showExportDialog(): void {
    this.visibleExportDialog = true;
  }

  handleExportDialogClose(processedActionData: ProcessedActionData): void {
    this.visibleExportDialog = false;
    if (processedActionData.action === ProcessedAction.UPDATED && this.securityDividendsGrandTotal) {
      this.securityDividendsGrandTotal.taxExportSettings = processedActionData.data;
    }
  }

  showPortfolioSelectionDialog(): void {
    this.visibleSecurityaccountDialog = true;
  }

  handleOnProcessedDialog(processedActionData: ProcessedActionData) {
    this.visibleSecurityaccountDialog = false;
    if (processedActionData.action === ProcessedAction.CREATED) {
      this.idsAccounts = processedActionData.data;
      this.writeAccountSettings(AppSettings.DIV_SECURITYACCOUNTS, this.idsAccounts.idsSecurityaccount);
      this.writeAccountSettings(AppSettings.DIV_CASHACCOUNTS, this.idsAccounts.idsCashaccount);
      this.readData();
    }
  }

  transactionDataChanged(processedActionData: ProcessedActionData) {
    if (processedActionData.action !== ProcessedAction.NO_CHANGE) {
      this.readData();
    }
  }

  /**
   * Returns the security-table expansion map for the given year, lazily creating it on first access. Always returns the
   * same object reference for a year so the binding stays stable across change detection and Optimus can mutate it in
   * place when the user expands/collapses a security row.
   *
   * @param year the year of the dividends year group
   * @returns the per-year expansion map keyed by {@code security.idSecuritycurrency}
   */
  getSecurityExpandedKeys(year: number): { [id: string]: boolean } {
    const key = String(year);
    return (this.securityExpandedKeysByYear[key] ??= {});
  }

  private readData(): void {
    this.portfolioService
      .getSecurityDividendsGrandTotalByTenant(this.idsAccounts.idsSecurityaccount, this.idsAccounts.idsCashaccount)
      .subscribe((data: SecurityDividendsGrandTotal) => {
        this.securityDividendsGrandTotal = data;
        this.securityDividendsYearGroup = this.securityDividendsGrandTotal.securityDividendsYearGroup;
        this.columnConfigs.forEach((columnConfig) => {
          columnConfig.headerSuffix = this.securityDividendsGrandTotal.mainCurrency;
          columnConfig.fixedCurrency = this.securityDividendsGrandTotal.mainCurrency;
        });
        const marginCol = this.fields.find((f) => f.field === 'yearFinanceCostMC');
        if (marginCol) {
          marginCol.visible = !!data.hasMarginData;
        }
        const ictaxFields = ['yearIctaxTotalTaxValueChf', 'yearIctaxTotalPaymentValueChf'];
        ictaxFields.forEach((fieldName) => {
          const col = this.fields.find((f) => f.field === fieldName);
          if (col) {
            col.visible = this.isSwissTenant;
          }
        });
        this.prepareTableAndTranslate();
      });
  }
}
