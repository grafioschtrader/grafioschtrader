import {ParentChildRegisterService} from '../../shared/service/parent.child.register.service';
import {CurrencypairService} from '../../securitycurrency/service/currencypair.service';
import {UserSettingsService} from '../../shared/service/user.settings.service';
import {ActivePanelService} from '../../shared/mainmenubar/service/active.panel.service';
import {TranslateService} from '@ngx-translate/core';
import {MessageToastService} from '../../shared/message/message.toast.service';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {TransactionService} from '../service/transaction.service';
import { ChangeDetectorRef, OnInit, Directive } from '@angular/core';
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

@Directive()
export abstract class TransactionTable extends TransactionContextMenu {

  cashaccountTransactionPositions: Transaction[];

  constructor(protected currencypairService: CurrencypairService,
              parentChildRegisterService: ParentChildRegisterService,
              activePanelService: ActivePanelService,
              transactionService: TransactionService,
              confirmationService: ConfirmationService,
              messageToastService: MessageToastService,
              changeDetectionStrategy: ChangeDetectorRef,
              filterService: FilterService,
              translateService: TranslateService,
              globalparameterService: GlobalparameterService,
              usersettingsService: UserSettingsService) {
    super(parentChildRegisterService, activePanelService, transactionService, confirmationService, messageToastService,
      changeDetectionStrategy, filterService, translateService, globalparameterService, usersettingsService);

    this.addColumn(DataType.DateNumeric, 'transactionTime', 'DATE', true, false,
      {width: 60, filterType: FilterType.likeDataType});
    this.addColumn(DataType.String, 'cashaccount.name', 'ACCOUNT', true, false, {
      width: 100,
      filterType: FilterType.withOptions
    });
    this.addColumn(DataType.String, 'cashaccount.currency', 'ACCOUNT_CURRENCY', true, false,
      {filterType: FilterType.withOptions});
    this.addColumn(DataType.String, 'transactionType', 'TRANSACTION_TYPE', true, false,
      {width: 100, translateValues: TranslateValue.NORMAL, filterType: FilterType.withOptions});
    this.addColumn(DataType.String, 'security.name', 'SECURITY', true, false,
      {width: 150, filterType: FilterType.withOptions});
    this.addColumn(DataType.Numeric, 'units', 'QUANTITY', true, false,
      {filterType: FilterType.likeDataType});
    this.addColumn(DataType.Numeric, 'quotation', 'QUOTATION_DIV', true, false,
      {filterType: FilterType.likeDataType, maxFractionDigits: 5});
    this.addColumn(DataType.String, 'currencypair.fromCurrency', 'CURRENCY', true, false,
      {filterType: FilterType.withOptions});
    this.addColumn(DataType.Numeric, 'currencyExRate', 'EXCHANGE_RATE', true, false,
      {maxFractionDigits: 5, filterType: FilterType.likeDataType});

    this.addColumnFeqH(DataType.Numeric, 'taxCost', true, false,
      {filterType: FilterType.likeDataType});
    this.addColumnFeqH(DataType.Numeric, 'transactionCost',  true, false,
      {filterType: FilterType.likeDataType});
    this.addColumnFeqH(DataType.Numeric, 'cashaccountAmount', true, false,
      {width: 60, filterType: FilterType.likeDataType, templateName: 'greenRed'});
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
        this.selectedTransaction = (<Transaction[]> processedActionData.data).find(newTrans =>
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

  protected prepareTransactionCallParam(transactionCallParam: TransactionCallParam) {
  }

  public getHelpContextId(): HelpIds {
    return HelpIds.HELP_PORTFOLIOS_TRANSACTIONLIST;
  }

}
