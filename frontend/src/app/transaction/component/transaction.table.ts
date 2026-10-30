import { Directive, inject, linkedSignal, signal } from '@angular/core';
import { MenuItem } from '@openng/optimus-ui/api';
import { DialogService } from '@openng/optimus-ui/dynamicdialog';
import { Currencypair } from '../../entities/currencypair';
import { Security } from '../../entities/security';
import { Transaction } from '../../entities/transaction';
import { BaseSettings } from '../../lib/base.settings';
import { TranslateValue } from '../../lib/datashowbase/column.config';
import { FilterType } from '../../lib/datashowbase/filter.type';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { HelpIds } from '../../lib/help/help.ids';
import { ProcessedAction } from '../../lib/types/processed.action';
import { ProcessedActionData } from '../../lib/types/processed.action.data';
import { CurrencypairService } from '../../securitycurrency/service/currencypair.service';
import { AppSettings } from '../../shared/app.settings';
import {
  TransactionExportCsvDialogComponent,
  TransactionExportCsvDialogData
} from './transaction-export-csv-dialog.component';
import { TransactionCallParam } from './transaction.call.parm';
import { TransactionContextMenu } from './transaction.context.menu';

/**
 * Abstract base class for displaying transactions in a tabular format with comprehensive filtering, sorting, and
 * editing capabilities. Provides standardized column definitions, data processing, and user interaction handling
 * for transaction-related table components throughout the application.
 */
@Directive()
export abstract class TransactionTable extends TransactionContextMenu {
  protected currencypairService: CurrencypairService = inject(CurrencypairService);

  /** Array of transaction positions displayed in the cash account table */
  readonly cashaccountTransactionPositions = signal<Transaction[]>([]);

  /**
   * Creates a new TransactionTable instance with comprehensive service dependencies and predefined column configuration.
   *
   */

  override readonly selectedTransaction = linkedSignal<Transaction[], Transaction | null>({
    source: this.cashaccountTransactionPositions,
    computation: (rows, previous) => rows.find((row) => row.idTransaction === previous?.value?.idTransaction) ?? null
  });
  constructor() {
    super();

    this.addColumn(DataType.DateNumeric, 'transactionTime', 'DATE', true, false, {
      width: 60,
      filterType: FilterType.likeDataType
    });
    this.addColumn(DataType.String, 'cashaccount.name', AppSettings.CASHACCOUNT.toUpperCase(), true, false, {
      width: 100,
      filterType: FilterType.withOptions
    });
    this.addColumn(DataType.String, 'cashaccount.currency', 'ACCOUNT_CURRENCY', true, false, {
      width: 40,
      filterType: FilterType.withOptions
    });
    this.addColumn(DataType.String, 'transactionType', 'TRANSACTION_TYPE', true, false, {
      width: 100,
      translateValues: TranslateValue.NORMAL,
      filterType: FilterType.withOptions
    });
    this.addColumn(DataType.String, 'security.name', AppSettings.SECURITY.toUpperCase(), true, false, {
      width: 150,
      filterType: FilterType.withOptions
    });
    this.addColumnFeqH(DataType.NumericInteger, 'idStandingOrder', true, false, {
      filterType: FilterType.likeDataType
    });
    this.addColumn(DataType.Numeric, 'units', 'QUANTITY', true, false, { filterType: FilterType.likeDataType });
    this.addColumn(DataType.Numeric, 'quotation', 'QUOTATION_DIV', true, false, {
      filterType: FilterType.likeDataType,
      maxFractionDigits: this.gps.getMaxFractionDigits()
    });
    this.addColumn(DataType.String, 'currencypair.fromCurrency', 'CURRENCY', true, false, {
      filterType: FilterType.withOptions
    });
    this.addColumnFeqH(DataType.NumericInteger, 'idStandingOrder', true, false, {
      filterType: FilterType.likeDataType
    });
    this.addColumn(DataType.Numeric, 'currencyExRate', 'EXCHANGE_RATE', true, false, {
      maxFractionDigits: this.gps.getMaxFractionDigits(),
      filterType: FilterType.likeDataType
    });

    this.addColumnFeqH(DataType.Numeric, 'taxCost', true, false, {
      filterType: FilterType.likeDataType,
      currencyPrecisionField: 'security.currency'
    });
    this.addColumnFeqH(DataType.Numeric, 'transactionCost', true, false, {
      width: 60,
      filterType: FilterType.likeDataType,
      currencyPrecisionField: 'security.currency'
    });
    this.addColumnFeqH(DataType.Numeric, 'cashaccountAmount', true, false, {
      width: 70,
      filterType: FilterType.likeDataType,
      templateName: 'greenRed',
      currencyPrecisionField: 'cashaccount.currency'
    });
    this.addColumnFeqH(DataType.Numeric, 'cashaccountRoundingDiff', false, true, {
      filterType: FilterType.likeDataType,
      currencyPrecisionField: 'cashaccount.currency'
    });
  }

  /**
   * Extracts the security object from a transaction for display and processing purposes.
   *
   * @param transaction The transaction from which to extract the security information
   * @returns The security object associated with the transaction
   */
  getSecurity(transaction: Transaction): Security | undefined {
    return transaction.security;
  }

  /**
   * Handles row selection events in the transaction table and updates the active panel menu accordingly.
   *
   * @param event The row selection event containing the selected transaction data
   */
  onRowSelect(event: { data: Transaction }): void {
    this.selectedTransaction.set(event.data);
    this.setMenuItemsToActivePanel();
  }

  /**
   * Handles the closing of transaction dialog windows and processes any resulting data changes. Updates the selected
   * transaction if modifications were made and refreshes the table data when necessary.
   *
   * @param processedActionData Contains information about the action performed and any resulting data changes
   */
  override handleCloseTransactionDialog(processedActionData: ProcessedActionData) {
    super.handleCloseTransactionDialog(processedActionData);
    if (processedActionData.action === ProcessedAction.UPDATED) {
      if (processedActionData.data instanceof Array) {
        const selected = this.selectedTransaction();
        this.selectedTransaction.set(
          (<Transaction[]>processedActionData.data).find(
            (newTrans) => newTrans.idTransaction === selected?.idTransaction
          ) ?? null
        );
      } else {
        this.selectedTransaction.set(processedActionData.data);
      }
      this.setMenuItemsToActivePanel();
    }
    processedActionData.action !== ProcessedAction.NO_CHANGE && this.initialize();
  }

  /**
   * Handles post-deletion cleanup by clearing the selected transaction and reinitializing the table data.
   *
   * @param transaction The transaction that was deleted
   */
  override afterDelete(transaction: Transaction): void {
    this.selectedTransaction.set(null);
    this.initialize();
  }

  public override getHelpContextId(): string {
    return HelpIds.HELP_PORTFOLIOS_TRANSACTIONLIST;
  }

  /**
   * Associates currency pair objects with transactions based on their currency pair IDs and creates translated
   * value stores for proper display and filtering functionality.
   *
   * @param transactions Array of transactions to be enhanced with currency pair information
   * @param currencypairs Array of available currency pairs for association
   * @returns The enhanced transaction array with currency pair objects attached
   */
  protected addCurrencypairToTransaction(transactions: Transaction[], currencypairs: Currencypair[]): Transaction[] {
    const currencypairMap: Map<number, Currencypair> = new Map();
    currencypairs.forEach((currencypair) => {
      if (currencypair.idSecuritycurrency != null) currencypairMap.set(currencypair.idSecuritycurrency, currencypair);
    });
    for (const transaction of transactions) {
      if (transaction.idCurrencypair != null) {
        transaction.currencypair = currencypairMap.get(transaction.idCurrencypair);
      }
    }
    this.createTranslatedValueStoreAndFilterField(transactions);
    return transactions;
  }

  /**
   * Template method for subclasses to customize transaction call parameters. Default implementation provides
   * no additional configuration.
   *
   * @param transactionCallParam The transaction call parameter object to be configured
   */
  protected override prepareTransactionCallParam(transactionCallParam: TransactionCallParam) {}

  /**
   * Appends the selection-independent CSV export action to the transaction menu. The export scope follows the level
   * of the concrete table: the tenant table exports all securities accounts, the portfolio table only the accounts
   * of its portfolio (see getExportIdPortfolio).
   *
   * @param transaction The currently selected transaction, may be null
   * @returns The menu items of the base class plus the export action
   */
  protected override getMenuItemsOnTransaction(transaction: Transaction): MenuItem[] {
    const menuItems = super.getMenuItemsOnTransaction(transaction);
    const exportMenuItem: MenuItem = {
      label: 'EXPORT_TRANSACTIONS_CSV' + BaseSettings.DIALOG_MENU_SUFFIX,
      command: () => this.openExportCsvDialog()
    };
    menuItems.push(exportMenuItem);
    return menuItems;
  }

  /**
   * Portfolio scope of the CSV export. The default (null) exports the whole tenant; the portfolio level table
   * overrides this with its portfolio ID so only that portfolio's securities accounts are exported.
   *
   * @returns The portfolio ID restricting the export, or null for the tenant-wide export
   */
  protected getExportIdPortfolio(): number | null | undefined {
    return null;
  }

  /** Opens the CSV export dialog with the level-dependent portfolio scope. */
  private openExportCsvDialog(): void {
    const data: TransactionExportCsvDialogData = { idPortfolio: this.getExportIdPortfolio() };
    this.injector.get(DialogService).open(TransactionExportCsvDialogComponent, {
      header: this.translateService.instant('EXPORT_TRANSACTIONS_CSV'),
      data,
      width: '500px',
      modal: true,
      closable: true,
      closeOnEscape: true
    });
  }
}
