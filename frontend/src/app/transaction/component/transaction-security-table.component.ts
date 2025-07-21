import {Component, Input, OnDestroy, OnInit} from '@angular/core';
import {SecurityTransactionSummary} from '../../entities/view/security.transaction.summary';
import {SecurityTransactionPosition} from '../../entities/view/security.transaction.position';
import {TranslateService} from '@ngx-translate/core';
import {Security} from '../../entities/security';
import {Transaction} from '../../entities/transaction';
import {TransactionCallParam} from './transaction.call.parm';
import {TransactionService} from '../service/transaction.service';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {TransactionPosition} from '../../entities/view/transaction.position';
import {TransactionContextMenu} from './transaction.context.menu';
import {ActivePanelService} from '../../shared/mainmenubar/service/active.panel.service';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {UserSettingsService} from '../../shared/service/user.settings.service';
import {ColumnConfig} from '../../lib/datashowbase/column.config';
import {SecurityService} from '../../securitycurrency/service/security.service';
import {ParentChildRegisterService} from '../../shared/service/parent.child.register.service';
import {BusinessHelper} from '../../shared/helper/business.helper';
import {ConfirmationService, FilterService} from 'primeng/api';
import {TransactionSecurityFieldDefinition} from './transaction.security.field.definition';
import {TransactionSecurityOptionalParam} from '../model/transaction.security.optional.param';
import {HelpIds} from '../../shared/help/help.ids';

/**
 * Component that displays transaction data for a single security instrument in a tabular format. This component serves as a
 * nested table view within the transaction management system, providing detailed transaction history with sorting, filtering,
 * and context menu capabilities. It integrates with the security transaction summary system to present comprehensive
 * transaction position data including currency conversions and gain/loss calculations.
 */
@Component({
    selector: 'transaction-security-table',
    templateUrl: '../view/transaction.security.table.html',
    standalone: false
})
export class TransactionSecurityTableComponent extends TransactionContextMenu implements OnInit, OnDestroy {
  /** Array of security account identifiers to filter transactions */
  @Input() idsSecurityaccount: number[];

  /** Unique identifier of the security currency for transaction filtering */
  @Input() idSecuritycurrency: number;

  /** Portfolio identifier for scoping transaction data */
  @Input() idPortfolio: number;

  /** Tenant identifier for multi-tenant data isolation */
  @Input() idTenant: number;

  /** Optional parameters for customizing transaction security display features */
  @Input() transactionSecurityOptionalParam: TransactionSecurityOptionalParam[];

  /** Column configuration for main currency display formatting */
  currencyColumnConfigMC: ColumnConfig[] = [];

  /** Complete security transaction summary containing position and transaction data */
  securityTransactionSummary: SecurityTransactionSummary = new SecurityTransactionSummary(null, null);

  /** List of transaction positions to be displayed in the table */
  transactionPositionList: SecurityTransactionPosition[] = [];

  /**
   * Creates an instance of TransactionSecurityTableComponent with required dependencies.
   *
   * @param securityService Service for security-related operations and data retrieval
   * @param parentChildRegisterService Service for managing parent-child component relationships
   * @param activePanelService Service for managing active panel state in the application
   * @param transactionService Service for transaction operations and data management
   * @param confirmationService PrimeNG service for displaying confirmation dialogs
   * @param messageToastService Service for displaying user notification messages
   * @param filterService PrimeNG service for data filtering operations
   * @param translateService Angular service for internationalization and translations
   * @param gps Global parameter service for application-wide settings and configurations
   * @param usersettingsService Service for managing user-specific settings and preferences
   */
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

  /**
   * Initializes the component by setting up table configuration and loading transaction data.
   */
  ngOnInit(): void {
    this.multiSortMeta.push({field: 'transaction.transactionTime', order: 1});
    this.currencyColumnConfigMC = TransactionSecurityFieldDefinition.getFieldDefinition(this, this.idTenant, false,
      this.transactionSecurityOptionalParam);
    this.initialize();
  }

  /**
   * Retrieves the security object associated with a given transaction.
   *
   * @param transaction The transaction object to get security information from
   * @returns The security object associated with the transaction
   */
  getSecurity(transaction: Transaction): Security {
    return this.securityTransactionSummary.securityPositionSummary.security;
  }

  /**
   * Handles row selection events in the transaction table.
   *
   * @param event The row selection event containing the selected transaction data
   */
  onRowSelect(event): void {
    const transactionPosition: TransactionPosition = event.data;
    this.selectedTransaction = transactionPosition.transaction;
    this.setMenuItemsToActivePanel();
  }

  /** Cleanup method called when the component is destroyed. */
  ngOnDestroy(): void {
    super.destroy();
  }

  public override getHelpContextId(): HelpIds {
    return HelpIds.HELP_TRANSACTION_CASH_BASED;
  }

  /** Initializes the component data by loading security transaction summary and configuring display columns. */
  protected initialize(): void {
    BusinessHelper.getSecurityTransactionSummary(this.securityService, this.idSecuritycurrency, this.idsSecurityaccount,
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

  /**
   * Prepares transaction call parameters for transaction editing operations.
   *
   * @param transactionCallParam The transaction call parameter object to be configured
   */
  protected prepareTransactionCallParam(transactionCallParam: TransactionCallParam) {
  }

}
