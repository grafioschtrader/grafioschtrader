import {ParentChildRegisterService} from '../../shared/service/parent.child.register.service';
import {CurrencypairService} from '../../securitycurrency/service/currencypair.service';
import {UserSettingsService} from '../../shared/service/user.settings.service';
import {ActivePanelService} from '../../shared/mainmenubar/service/active.panel.service';
import {TranslateService} from '@ngx-translate/core';
import {MessageToastService} from '../../shared/message/message.toast.service';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {TransactionService} from '../service/transaction.service';
import {Directive} from '@angular/core';
import {TransactionContextMenu} from './transaction.context.menu';
import {FilterType} from '../../shared/datashowbase/filter.type';
import {DataType} from '../../dynamic-form/models/data.type';
import {Transaction} from '../../entities/transaction';
import {Currencypair} from '../../entities/currencypair';
import {Security} from '../../entities/security';
import {ProcessedAction} from '../../shared/types/processed.action';
import {ProcessedActionData} from '../../shared/types/processed.action.data';
import {TransactionCallParam} from './transaction.call.parm';
import {ConfirmationService, FilterService} from 'primeng/api';
import {HelpIds} from '../../shared/help/help.ids';
import {TranslateValue} from '../../shared/datashowbase/column.config';
import {AppSettings} from '../../shared/app.settings';

@Directive()
export abstract class TransactionTable extends TransactionContextMenu {

  cashaccountTransactionPositions: Transaction[];

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

  getSecurity(transaction: Transaction): Security {
    return transaction.security;
  }

  onRowSelect(event): void {
    this.selectedTransaction = event.data;
    this.setMenuItemsToActivePanel();
  }

  handleCloseTransactionDialog(processedActionData: ProcessedActionData) {
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

  afterDelete(transaction: Transaction): void {
    this.selectedTransaction = null;
    this.initialize();
  }

  public override getHelpContextId(): HelpIds {
    return HelpIds.HELP_PORTFOLIOS_TRANSACTIONLIST;
  }

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

  protected override prepareTransactionCallParam(transactionCallParam: TransactionCallParam) {
  }

}
