import {ParentChildRegisterService} from '../../shared/service/parent.child.register.service';
import {CurrencypairService} from '../../securitycurrency/service/currencypair.service';
import {UserSettingsService} from '../../shared/service/user.settings.service';
import {ActivePanelService} from '../../shared/mainmenubar/service/active.panel.service';
import {TranslateService} from '@ngx-translate/core';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {TransactionService} from '../service/transaction.service';
import {Directive} from '@angular/core';
import {TransactionContextMenu} from './transaction.context.menu';
import {FilterType} from '../../lib/datashowbase/filter.type';
import {DataType} from '../../lib/dynamic-form/models/data.type';
import {Transaction} from '../../entities/transaction';
import {Currencypair} from '../../entities/currencypair';
import {Security} from '../../entities/security';
import {ProcessedAction} from '../../lib/types/processed.action';
import {ProcessedActionData} from '../../lib/types/processed.action.data';
import {TransactionCallParam} from './transaction.call.parm';
import {ConfirmationService, FilterService} from 'primeng/api';
import {HelpIds} from '../../shared/help/help.ids';
import {TranslateValue} from '../../lib/datashowbase/column.config';
import {AppSettings} from '../../shared/app.settings';

/**
 * Abstract base class for displaying transactions in a tabular format with comprehensive filtering, sorting, and
 * editing capabilities. Provides standardized column definitions, data processing, and user interaction handling
 * for transaction-related table components throughout the application.
 */
@Directive()
export abstract class TransactionTable extends TransactionContextMenu {

  /** Array of transaction positions displayed in the cash account table */
  cashaccountTransactionPositions: Transaction[];

  /**
   * Creates a new TransactionTable instance with comprehensive service dependencies and predefined column configuration.
   *
   * @param currencypairService Service for handling currency pair operations and data retrieval
   * @param parentChildRegisterService Service for managing parent-child component relationships and state preservation
   * @param activePanelService Service for managing active panel states and menu interactions
   * @param transactionService Service for transaction CRUD operations and business logic
   * @param confirmationService PrimeNG service for displaying confirmation dialogs
   * @param messageToastService Service for displaying user notification messages
   * @param filterService PrimeNG service for table filtering functionality
   * @param translateService Angular service for internationalization and text translation
   * @param gps Global parameter service for application-wide settings and configurations
   * @param usersettingsService Service for managing user-specific settings and preferences
   */

  protected constructor(protected currencypairService: CurrencypairService,
              parentChildRegisterService: ParentChildRegisterService,
              activePanelService: ActivePanelService,
              transactionService: TransactionService,
              confirmationService: ConfirmationService,
              messageToastService: MessageToastService,
              filterService: FilterService,
              translateService: TranslateService,
              gps: GlobalparameterService,
              usersettingsService: UserSettingsService) {
    super(parentChildRegisterService, activePanelService, transactionService, confirmationService, messageToastService,
      filterService, translateService, gps, usersettingsService);

    this.addColumn(DataType.DateNumeric, 'transactionTime', 'DATE', true, false,
      {width: 60, filterType: FilterType.likeDataType});
    this.addColumn(DataType.String, 'cashaccount.name', AppSettings.CASHACCOUNT.toUpperCase(), true, false, {
      width: 100,
      filterType: FilterType.withOptions
    });
    this.addColumn(DataType.String, 'cashaccount.currency', 'ACCOUNT_CURRENCY', true, false,
      {width: 40, filterType: FilterType.withOptions});
    this.addColumn(DataType.String, 'transactionType', 'TRANSACTION_TYPE', true, false,
      {width: 100, translateValues: TranslateValue.NORMAL, filterType: FilterType.withOptions});
    this.addColumn(DataType.String, 'security.name', AppSettings.SECURITY.toUpperCase(), true, false,
      {width: 150, filterType: FilterType.withOptions});
    this.addColumn(DataType.Numeric, 'units', 'QUANTITY', true, false,
      {filterType: FilterType.likeDataType});
    this.addColumn(DataType.Numeric, 'quotation', 'QUOTATION_DIV', true, false,
      {filterType: FilterType.likeDataType, maxFractionDigits: AppSettings.FID_MAX_FRACTION_DIGITS});
    this.addColumn(DataType.String, 'currencypair.fromCurrency', 'CURRENCY', true, false,
      {filterType: FilterType.withOptions});
    this.addColumn(DataType.Numeric, 'currencyExRate', 'EXCHANGE_RATE', true, false,
      {maxFractionDigits: AppSettings.FID_MAX_FRACTION_DIGITS, filterType: FilterType.likeDataType});

    this.addColumnFeqH(DataType.Numeric, 'taxCost', true, false,
      {filterType: FilterType.likeDataType, maxFractionDigits: AppSettings.FID_MAX_FRACTION_DIGITS});
    this.addColumnFeqH(DataType.Numeric, 'transactionCost', true, false,
      {width: 60, filterType: FilterType.likeDataType, maxFractionDigits: AppSettings.FID_MAX_FRACTION_DIGITS});
    this.addColumnFeqH(DataType.Numeric, 'cashaccountAmount', true, false,
      {
        width: 70, filterType: FilterType.likeDataType, templateName: 'greenRed',
        maxFractionDigits: AppSettings.FID_MAX_FRACTION_DIGITS
      });
  }

  /**
   * Extracts the security object from a transaction for display and processing purposes.
   *
   * @param transaction The transaction from which to extract the security information
   * @returns The security object associated with the transaction
   */
  getSecurity(transaction: Transaction): Security {
    return transaction.security;
  }

  /**
   * Handles row selection events in the transaction table and updates the active panel menu accordingly.
   *
   * @param event The row selection event containing the selected transaction data
   */
  onRowSelect(event): void {
    this.selectedTransaction = event.data;
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
        this.selectedTransaction = (<Transaction[]>processedActionData.data).find(newTrans =>
          newTrans.idTransaction === this.selectedTransaction.idTransaction);
      } else {
        this.selectedTransaction = processedActionData.data;
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
    this.selectedTransaction = null;
    this.initialize();
  }

  public override getHelpContextId(): HelpIds {
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
  protected addCurrencypairToTransaction(transactions: Transaction[],
                                         currencypairs: Currencypair[]): Transaction[] {
    const currencypairMap: Map<number, Currencypair> = new Map();
    currencypairs.forEach(currencypair => currencypairMap.set(currencypair.idSecuritycurrency, currencypair));
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
  protected override prepareTransactionCallParam(transactionCallParam: TransactionCallParam) {
  }

}
