import {Component, Injector, OnDestroy, OnInit} from '@angular/core';
import {PortfolioService} from '../../portfolio/service/portfolio.service';
import {SecurityDividendsGrandTotal} from '../../entities/view/securitydividends/security.dividends.grand.total';
import {ActivatedRoute} from '@angular/router';
import {SecurityDividendsYearGroup} from '../../entities/view/securitydividends/security.dividends.year.group';
import {TableConfigBase} from '../../lib/datashowbase/table.config.base';
import {TranslateService, TranslateModule} from '@ngx-translate/core';
import {UserSettingsService} from '../../lib/services/user.settings.service';
import {DataType} from '../../lib/dynamic-form/models/data.type';
import {IGlobalMenuAttach} from '../../lib/mainmenubar/component/iglobal.menu.attach';
import {ActivePanelService} from '../../lib/mainmenubar/service/active.panel.service';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {ColumnConfig, ColumnGroupConfig} from '../../lib/datashowbase/column.config';
import {HelpIds} from '../../lib/help/help.ids';
import {FilterService, MenuItem} from 'primeng/api';
import {ProcessedActionData} from '../../lib/types/processed.action.data';
import {ProcessedAction} from '../../lib/types/processed.action';
import {TranslateHelper} from '../../lib/helper/translate.helper';
import {IdsAccounts} from '../model/ids.accounts';
import {AppSettings} from '../../shared/app.settings';
import {BaseSettings} from '../../lib/base.settings';
import {CommonModule} from '@angular/common';
import {TableModule} from 'primeng/table';
import {TooltipModule} from 'primeng/tooltip';
import {
  TenantDividendSecurityAccountSelectionDialogComponent
} from './tenant-dividend-security-account-selection-dialog.component';
import {
  TenantDividendsCashaccountExtendedComponent
} from './tenant-dividends-cashaccount-extended.component';
import {
  TenantDividendsSecurityExtendedComponent
} from './tenant-dividends-security-extended.component';
import {
  TaxStatementExportDialogComponent
} from '../../taxdata/component/tax-statement-export-dialog.component';

/**
 * Shows the dividends and some other information like transaction cost grouped by year.
 */
@Component({
  templateUrl: '../view/tenant.dividends.html',
  standalone: true,
  imports: [CommonModule, TranslateModule, TableModule, TooltipModule, TenantDividendSecurityAccountSelectionDialogComponent,
    TenantDividendsCashaccountExtendedComponent, TenantDividendsSecurityExtendedComponent, TaxStatementExportDialogComponent]
})
export class TenantDividendsComponent extends TableConfigBase implements IGlobalMenuAttach, OnInit, OnDestroy {

  securityDividendsGrandTotal: SecurityDividendsGrandTotal;
  securityDividendsYearGroup: SecurityDividendsYearGroup[];

  securityDividendsGrandTotalSelected: SecurityDividendsYearGroup;
  visibleSecurityaccountDialog: boolean;
  visibleExportDialog: boolean;
  idsAccounts: IdsAccounts;
  filterTransactionsToYearEnd = false;

  private columnConfigs: ColumnConfig[] = [];

  constructor(private portfolioService: PortfolioService,
              private activatedRoute: ActivatedRoute,
              private activePanelService: ActivePanelService,
              filterService: FilterService,
              translateService: TranslateService,
              gps: GlobalparameterService,
              usersettingsService: UserSettingsService,
              injector: Injector) {
    super(filterService, usersettingsService, translateService, gps, injector);
  }

  get selectedSecurityAccounts(): number {
    return this.idsAccounts.idsSecurityaccount.length === 0 ? this.securityDividendsGrandTotal.numberOfSecurityAccounts
      : this.idsAccounts.idsSecurityaccount.length;
  }

  get selectedCashAccounts(): number {
    return this.idsAccounts.idsCashaccount.length === 1 && this.idsAccounts.idsCashaccount[0] === -1
      ? this.securityDividendsGrandTotal.numberOfCashAccounts : this.idsAccounts.idsCashaccount.length;
  }

  get totalSecurityAccounts(): number {
    return this.securityDividendsGrandTotal ? this.securityDividendsGrandTotal.numberOfSecurityAccounts : 0;
  }

  get totalCashAccounts(): number {
    return this.securityDividendsGrandTotal ? this.securityDividendsGrandTotal.numberOfCashAccounts : 0;
  }

  ngOnInit(): void {
    // use string to avoid number format
    this.addColumn(DataType.String, 'year', 'YEAR', true, false);
    this.columnConfigs.push(this.addColumn(DataType.Numeric, 'yearInterestMC', 'INTEREST_CASHACCOUNT', true, false,
      {columnGroupConfigs: [new ColumnGroupConfig('grandInterestMC')]}));

    this.columnConfigs.push(this.addColumn(DataType.Numeric, 'securityCostGroup.groupTotalTaxCostMc', 'TRANSACTION_TAX_COST', true, false,
      {columnGroupConfigs: [new ColumnGroupConfig('grandTotalTaxCostMC')]}));

    this.addColumn(DataType.NumericInteger, 'yearCountPaidTransactions', 'PAID_TRANSACTIONS', true, false,
      {columnGroupConfigs: [new ColumnGroupConfig('grandCountPaidTransaction')]});
    this.columnConfigs.push(this.addColumn(DataType.Numeric, 'securityCostGroup.groupTotalAverageTransactionCostMC',
      'TRANSACTION_AVERAGE_PAID', true, false,
      {columnGroupConfigs: [new ColumnGroupConfig('grandTotalAverageTransactionCostMC')]}));
    this.columnConfigs.push(this.addColumn(DataType.Numeric, 'securityCostGroup.groupTotalTransactionCostMC',
      'TRANSACTION_COST', true, false,
      {columnGroupConfigs: [new ColumnGroupConfig('grandTotalTransactionCostMC')]}));
    this.columnConfigs.push(this.addColumn(DataType.Numeric, 'yearFeeMC', 'FEE', true, false,
      {columnGroupConfigs: [new ColumnGroupConfig('grandFeeMC')]}));
    this.columnConfigs.push(this.addColumn(DataType.Numeric, 'yearFinanceCostMC', 'MARGIN_FINANCE_COST', false, true,
      {width: 80, columnGroupConfigs: [new ColumnGroupConfig('grandFinanceCostMC')]}));
    this.columnConfigs.push(this.addColumnFeqH(DataType.Numeric, 'yearAutoPaidTaxMC', true, false));
    this.columnConfigs.push(this.addColumnFeqH(DataType.Numeric, 'yearTaxableAmountMC', true, false,
      {columnGroupConfigs: [new ColumnGroupConfig('grandTaxableAmountMC')]}));
    this.columnConfigs.push(this.addColumnFeqH(DataType.Numeric, 'yearRealReceivedDivInterestMC', true, false,
      {columnGroupConfigs: [new ColumnGroupConfig('grandRealReceivedDivInterestMC')]}));

    this.columnConfigs.push(this.addColumn(DataType.Numeric, 'valueAtEndOfYearMC', 'VALUE_AT_END_OF_YEAR', true, false));
    this.addColumn(DataType.Numeric, 'yearIctaxTotalPaymentValueChf', 'ICTAX_TOTAL_PAYMENT_CHF', false, true);
    this.addColumn(DataType.Numeric, 'yearIctaxTotalTaxValueChf', 'ICTAX_TOTAL_TAX_VALUE', false, true);

    const idsCashaccount = this.getAccountSettings(AppSettings.DIV_CASHACCOUNTS);
    this.idsAccounts = new IdsAccounts(this.getAccountSettings(AppSettings.DIV_SECURITYACCOUNTS),
      idsCashaccount.length === 0 ? [-1] : idsCashaccount);
    this.readData();
    this.multiSortMeta.push({field: 'year', order: 1});
    this.onComponentClick(null);
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

  hideContextMenu(): void {
  }

  callMeDeactivate(): void {
  }

  onComponentClick(event): void {
    this.activePanelService.activatePanel(this, {showMenu: this.getMenuShowOptions()});
  }

  public getHelpContextId(): string {
    return HelpIds.HELP_PORTFOLIOS_DIVIDENDS;
  }

  ngOnDestroy(): void {
    this.activePanelService.destroyPanel(this);
  }

  override getMenuShowOptions(): MenuItem[] {
    const menuItems: MenuItem[] = [
      {label: 'DIV_INCLUDE_SECURITYACCOUNT', command: (event) => this.showPortfolioSelectionDialog()},
    ];
    if (this.securityDividendsGrandTotal?.availableTaxYears?.length > 0) {
      menuItems.push({label: 'EXPORT_TAX_STATEMENT', command: (event) => this.showExportDialog()});
    }
    menuItems.push(
      {separator: true},
      {
        label: 'FILTER_TRANSACTIONS_TO_YEAR_END',
        command: (event) => this.handleFilterTransactionsToggle(event),
        icon: this.filterTransactionsToYearEnd ? BaseSettings.ICONNAME_SQUARE_CHECK : BaseSettings.ICONNAME_SQUARE_EMTPY
      }
    );
    TranslateHelper.translateMenuItems(menuItems, this.translateService);
    return menuItems;
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

  private readData(): void {
    this.portfolioService.getSecurityDividendsGrandTotalByTenant(this.idsAccounts.idsSecurityaccount,
      this.idsAccounts.idsCashaccount).subscribe((data: SecurityDividendsGrandTotal) => {
      this.securityDividendsGrandTotal = data;
      this.securityDividendsYearGroup = this.securityDividendsGrandTotal.securityDividendsYearGroup;
      this.columnConfigs.forEach(columnConfig => columnConfig.headerSuffix = this.securityDividendsGrandTotal.mainCurrency);
      const marginCol = this.fields.find(f => f.field === 'yearFinanceCostMC');
      if (marginCol) { marginCol.visible = !!data.hasMarginData; }
      const ictaxFields = ['yearIctaxTotalTaxValueChf', 'yearIctaxTotalPaymentValueChf'];
      ictaxFields.forEach(fieldName => {
        const col = this.fields.find(f => f.field === fieldName);
        if (col) { col.visible = data.tenantCountry === 'CH'; }
      });
      this.prepareTableAndTranslate();
    });
  }
}
