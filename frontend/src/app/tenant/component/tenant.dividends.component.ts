import type { PanelClickEvent } from '../../lib/mainmenubar/component/iglobal.menu.attach';
import { Subscription } from 'rxjs';
import { computed, DestroyRef, inject, signal, Component, OnDestroy, OnInit } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { PortfolioService } from '../../portfolio/service/portfolio.service';
import { SecurityDividendsGrandTotal } from '../../entities/view/securitydividends/security.dividends.grand.total';
import { ActivatedRoute, Router } from '@angular/router';
import { SecurityDividendsYearGroup } from '../../entities/view/securitydividends/security.dividends.year.group';
import { TableConfigBase } from '../../lib/datashowbase/table.config.base';
import { TranslateModule } from '@ngx-translate/core';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { IGlobalMenuAttach } from '../../lib/mainmenubar/component/iglobal.menu.attach';
import { ActivePanelService } from '../../lib/mainmenubar/service/active.panel.service';
import { ColumnConfig, ColumnGroupConfig } from '../../lib/datashowbase/column.config';
import { HelpIds } from '../../lib/help/help.ids';
import { MenuItem } from '@openng/optimus-ui/api';
import { ProcessedActionData } from '../../lib/types/processed.action.data';
import { ProcessedAction } from '../../lib/types/processed.action';
import { TranslateHelper } from '../../lib/helper/translate.helper';
import { IdsAccounts } from '../model/ids.accounts';
import { AppSettings } from '../../shared/app.settings';
import { BaseSettings } from '../../lib/base.settings';

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
import { TenantDividendsChartService } from '../service/tenant.dividends.chart.service';

/**
 * Shows the dividends and some other information like transaction cost grouped by year.
 */
@Component({
  templateUrl: '../view/tenant.dividends.html',
  imports: [
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
  private portfolioService: PortfolioService = inject(PortfolioService);
  private activatedRoute: ActivatedRoute = inject(ActivatedRoute);
  private activePanelService: ActivePanelService = inject(ActivePanelService);
  private transactionService: TransactionService = inject(TransactionService);
  private messageToastService: MessageToastService = inject(MessageToastService);
  private tenantDividendsChartService: TenantDividendsChartService = inject(TenantDividendsChartService);
  private router: Router = inject(Router);

  readonly securityDividendsGrandTotal = signal<SecurityDividendsGrandTotal | null>(null);
  readonly securityDividendsYearGroup = computed(
    () => this.securityDividendsGrandTotal()?.securityDividendsYearGroup ?? []
  );

  securityDividendsGrandTotalSelected: SecurityDividendsYearGroup;
  readonly visibleSecurityaccountDialog = signal(false);
  readonly visibleExportDialog = signal(false);
  readonly idsAccounts = signal<IdsAccounts | null>(null);
  readonly filterTransactionsToYearEnd = signal(false);
  readonly contextMenuItems = signal<MenuItem[]>([]);

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

  private readonly destroyRef = inject(DestroyRef);
  private readRequest: Subscription;
  private menuRequest: Subscription;
  private contextMenuRequest: Subscription;

  private columnConfigs: ColumnConfig[] = [];

  constructor() {
    super();
  }

  readonly selectedSecurityAccounts = computed(() => {
    const accounts = this.idsAccounts();
    if (!accounts) return null;
    return accounts.idsSecurityaccount.length === 0
      ? (this.securityDividendsGrandTotal()?.numberOfSecurityAccounts ?? null)
      : accounts.idsSecurityaccount.length;
  });

  readonly selectedCashAccounts = computed(() => {
    const accounts = this.idsAccounts();
    if (!accounts) return null;
    return accounts.idsCashaccount.length === 1 && accounts.idsCashaccount[0] === -1
      ? (this.securityDividendsGrandTotal()?.numberOfCashAccounts ?? null)
      : accounts.idsCashaccount.length;
  });

  readonly totalSecurityAccounts = computed(() => this.securityDividendsGrandTotal()?.numberOfSecurityAccounts ?? 0);

  readonly totalCashAccounts = computed(() => this.securityDividendsGrandTotal()?.numberOfCashAccounts ?? 0);

  /** True for Swiss tenants; gates the ICTax columns and the eCH-0196 tax statement export. */
  private get isSwissTenant(): boolean {
    return this.securityDividendsGrandTotal()?.tenantCountry === 'CH';
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
    const accounts = new IdsAccounts(
      this.getAccountSettings(AppSettings.DIV_SECURITYACCOUNTS),
      idsCashaccount.length === 0 ? [-1] : idsCashaccount
    );
    this.idsAccounts.set(accounts);
    this.tenantDividendsChartService.setAccounts(accounts);
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

  onComponentClick(event: PanelClickEvent | null): void {
    if (!event || !event[this.consumedGT]) {
      this.menuRequest?.unsubscribe();
      this.menuRequest = TranslateHelper.translateMenuItems$(this.getMenuShowOptions(), this.translateService)
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe((showMenu) => this.activePanelService.activatePanel(this, { showMenu }));
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
    if (this.isSwissTenant && this.securityDividendsGrandTotal()?.availableTaxYears?.length > 0) {
      menuItems.push({
        label: 'EXPORT_TAX_STATEMENT',
        command: (event) => this.showExportDialog()
      });
    }
    menuItems.push(
      {
        label: 'SHOW_CHARTS',
        command: () => this.navigateToChartRoute()
      },
      { separator: true },
      {
        label: 'FILTER_TRANSACTIONS_TO_YEAR_END',
        command: (event) => this.handleFilterTransactionsToggle(event),
        icon: this.filterTransactionsToYearEnd()
          ? BaseSettings.ICONNAME_SQUARE_CHECK
          : BaseSettings.ICONNAME_SQUARE_EMTPY
      }
    );
    return menuItems;
  }

  /**
   * Rebuilds the year-row right-click context menu. The "set ex-date from tax data" item is disabled when no year is
   * selected or the selected year has no imported tax data (same {@code availableTaxYears} gate as the export action).
   */
  private buildContextMenu(): void {
    const year = this.securityDividendsGrandTotalSelected?.year;
    const hasTaxData = year != null && !!this.securityDividendsGrandTotal()?.availableTaxYears?.includes(year);
    const menuItems: MenuItem[] = [
      {
        label: 'SET_EX_DATE_FROM_TAX_DATA',
        command: () => this.handleApplyExDatesFromTaxData(),
        disabled: !hasTaxData
      }
    ];
    this.contextMenuRequest?.unsubscribe();
    this.contextMenuRequest = TranslateHelper.translateMenuItems$(menuItems, this.translateService)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((items) => this.contextMenuItems.set(items));
  }

  /**
   * Opens the income charts in the lower display area. The chart component receives the account selection and the
   * selected year through {@link TenantDividendsChartService}.
   */
  private navigateToChartRoute(): void {
    this.router.navigate([
      BaseSettings.MAINVIEW_KEY + '/',
      { outlets: { mainbottom: [AppSettings.TENANT_DIVIDENDS_CHART] } }
    ]);
  }

  /**
   * Switches the year of the income charts to the year of the selected row.
   *
   * @param event - The row select event of the year table
   */
  onRowSelect(event: any): void {
    this.tenantDividendsChartService.selectYear(event.data.year);
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
    this.transactionService
      .applyExDatesFromTaxData(year)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((result: ExDateFromTaxDataResult) => {
        this.messageToastService.showMessageI18n(InfoLevelType.INFO, 'EX_DATE_FROM_TAX_DATA_RESULT', {
          assigned: result.exDateAssigned,
          alreadySet: result.alreadySet,
          unmatched: result.unmatched
        });
        this.readData();
        this.tenantDividendsChartService.notifyDataChanged();
      });
  }

  handleFilterTransactionsToggle(event: any): void {
    this.filterTransactionsToYearEnd.update((value) => !value);
    this.onComponentClick(null);
  }

  showExportDialog(): void {
    this.visibleExportDialog.set(true);
  }

  handleExportDialogClose(processedActionData: ProcessedActionData): void {
    this.visibleExportDialog.set(false);
    if (processedActionData.action === ProcessedAction.UPDATED && this.securityDividendsGrandTotal()) {
      this.securityDividendsGrandTotal.update(
        (total) => total && { ...total, taxExportSettings: processedActionData.data }
      );
    }
  }

  showPortfolioSelectionDialog(): void {
    this.visibleSecurityaccountDialog.set(true);
  }

  handleOnProcessedDialog(processedActionData: ProcessedActionData) {
    this.visibleSecurityaccountDialog.set(false);
    if (processedActionData.action === ProcessedAction.CREATED) {
      const accounts: IdsAccounts = processedActionData.data;
      if (!accounts) return;
      this.idsAccounts.set(accounts);
      this.writeAccountSettings(AppSettings.DIV_SECURITYACCOUNTS, accounts.idsSecurityaccount);
      this.writeAccountSettings(AppSettings.DIV_CASHACCOUNTS, accounts.idsCashaccount);
      this.tenantDividendsChartService.setAccounts(accounts);
      this.readData();
    }
  }

  transactionDataChanged(processedActionData: ProcessedActionData) {
    if (processedActionData.action !== ProcessedAction.NO_CHANGE) {
      this.readData();
      this.tenantDividendsChartService.notifyDataChanged();
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
    this.readRequest?.unsubscribe();
    const accounts = this.idsAccounts();
    if (!accounts) return;
    this.readRequest = this.portfolioService
      .getSecurityDividendsGrandTotalByTenant(accounts.idsSecurityaccount, accounts.idsCashaccount)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((data: SecurityDividendsGrandTotal) => {
        this.securityDividendsGrandTotal.set(data);
        this.columnConfigs.forEach((columnConfig) => {
          columnConfig.headerSuffix = data.mainCurrency;
          columnConfig.fixedCurrency = data.mainCurrency;
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
