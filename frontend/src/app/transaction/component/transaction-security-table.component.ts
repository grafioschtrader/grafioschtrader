import {Component, Input, OnDestroy, OnInit} from '@angular/core';
import {SecurityTransactionSummary} from '../../entities/view/security.transaction.summary';
import {SecurityTransactionPosition} from '../../entities/view/security.transaction.position';
import {TranslateService} from '@ngx-translate/core';
import {Security} from '../../entities/security';
import {Transaction} from '../../entities/transaction';
import {TransactionCallParam} from './transaction.call.parm';
import {TransactionService} from '../service/transaction.service';
import {MessageToastService} from '../../shared/message/message.toast.service';
import {TransactionPosition} from '../../entities/view/transaction.position';
import {TransactionContextMenu} from './transaction.context.menu';
import {ActivePanelService} from '../../shared/mainmenubar/service/active.panel.service';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {UserSettingsService} from '../../shared/service/user.settings.service';
import {ColumnConfig} from '../../shared/datashowbase/column.config';
import {SecurityService} from '../../securitycurrency/service/security.service';
import {ParentChildRegisterService} from '../../shared/service/parent.child.register.service';
import {BusinessHelper} from '../../shared/helper/business.helper';
import {ConfirmationService, FilterService} from 'primeng/api';
import {TransactionSecurityFieldDefinition} from './transaction.security.field.definition';
import {TransactionSecurityOptionalParam} from '../model/transaction.security.optional.param';
import {HelpIds} from '../../shared/help/help.ids';

/**
 * Shows the transactions for a single security.
 * It is used as a nested table.
 */
@Component({
  selector: 'transaction-security-table',
  templateUrl: '../view/transaction.security.table.html',
})
export class TransactionSecurityTableComponent extends TransactionContextMenu implements OnInit, OnDestroy {
  @Input() idsSecurityaccount: number[];
  @Input() idSecuritycurrency: number;
  @Input() idPortfolio: number;
  @Input() idTenant: number;
  @Input() transactionSecurityOptionalParam: TransactionSecurityOptionalParam[];

  currencyColumnConfigMC: ColumnConfig[] = [];
  // Data to be shown
  securityTransactionSummary: SecurityTransactionSummary = new SecurityTransactionSummary(null, null);
  transactionPositionList: SecurityTransactionPosition[] = [];

  constructor(private securityService: SecurityService,
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
  }

  ngOnInit(): void {
    this.multiSortMeta.push({field: 'transaction.transactionTime', order: 1});
    this.currencyColumnConfigMC = TransactionSecurityFieldDefinition.getFieldDefinition(this, this.idTenant, false,
      this.transactionSecurityOptionalParam);
    this.initialize();
  }

  getSecurity(transaction: Transaction): Security {
    return this.securityTransactionSummary.securityPositionSummary.security;
  }

  onRowSelect(event): void {
    const transactionPosition: TransactionPosition = event.data;
    this.selectedTransaction = transactionPosition.transaction;
    this.setMenuItemsToActivePanel();
  }

  ngOnDestroy(): void {
    super.destroy();
  }

  public getHelpContextId(): HelpIds {
    return HelpIds.HELP_TRANSACTION_CASH_BASED;
  }

  protected initialize(): void {
    BusinessHelper.setSecurityTransactionSummary(this.securityService, this.idSecuritycurrency, this.idsSecurityaccount,
      this.idPortfolio, false).subscribe(result => {

      this.securityTransactionSummary = result;
      this.createTranslatedValueStoreAndFilterField(this.securityTransactionSummary.transactionPositionList);
      this.transactionPositionList = this.securityTransactionSummary.transactionPositionList;
      this.currencyColumnConfigMC.forEach(cc => {
        cc.headerSuffix = this.securityTransactionSummary.securityPositionSummary.mainCurrency;
        this.setFieldHeaderTranslation(cc);
      });
    });
  }

  protected prepareTransactionCallParam(transactionCallParam: TransactionCallParam) {
  }

}
