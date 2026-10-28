import { Component, OnDestroy, OnInit, signal, computed, input, inject } from '@angular/core';
import { SecurityTransactionSummary } from '../../entities/view/security.transaction.summary';
import { SecurityTransactionPosition } from '../../entities/view/security.transaction.position';
import { Security } from '../../entities/security';
import { Transaction } from '../../entities/transaction';
import { TransactionCallParam } from './transaction.call.parm';
import { TransactionPosition } from '../../entities/view/transaction.position';
import { TransactionContextMenu } from './transaction.context.menu';
import { ColumnConfig } from '../../lib/datashowbase/column.config';
import { SecurityService } from '../../securitycurrency/service/security.service';
import { BusinessHelper } from '../../shared/helper/business.helper';
import { TransactionSecurityFieldDefinition } from './transaction.security.field.definition';
import { TransactionSecurityOptionalParam } from '../model/transaction.security.optional.param';
import { HelpIds } from '../../lib/help/help.ids';

import { ConfigurableTableComponent } from '../../lib/datashowbase/configurable-table.component';
import { TransactionSecurityEditComponent } from './transaction-security-edit.component';
import { StandingOrderSecurityEditComponent } from '../../standingorder/component/standing-order-security-edit.component';
import { DisposalCostHelper } from '../../shared/helper/disposal.cost.helper';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

/**
 * Component that displays transaction data for a single security instrument in a tabular format. This component serves as a
 * nested table view within the transaction management system, providing detailed transaction history with sorting, filtering,
 * and context menu capabilities. It integrates with the security transaction summary system to present comprehensive
 * transaction position data including currency conversions and gain/loss calculations.
 */
@Component({
  selector: 'transaction-security-table',
  templateUrl: '../view/transaction.security.table.html',
  imports: [ConfigurableTableComponent, TransactionSecurityEditComponent, StandingOrderSecurityEditComponent]
})
export class TransactionSecurityTableComponent extends TransactionContextMenu implements OnInit, OnDestroy {
  private securityService: SecurityService = inject(SecurityService);

  /** Array of security account identifiers to filter transactions */
  readonly idsSecurityaccount = input<number[]>(undefined);

  /** Unique identifier of the security currency for transaction filtering */
  readonly idSecuritycurrency = input<number>(undefined);

  /** Portfolio identifier for scoping transaction data */
  readonly idPortfolio = input<number>(undefined);

  /** Tenant identifier for multi-tenant data isolation */
  readonly idTenant = input<number>(undefined);

  /** Optional parameters for customizing transaction security display features */
  readonly transactionSecurityOptionalParam = input<TransactionSecurityOptionalParam[]>(undefined);

  /** Optional cutoff date (yyyy-MM-dd) to limit transactions */
  readonly untilDate = input<string>(undefined);

  /** Column configuration for main currency display formatting */
  currencyColumnConfigMC: ColumnConfig[] = [];

  /** Complete security transaction summary containing position and transaction data */
  readonly securityTransactionSummary = signal(new SecurityTransactionSummary(null, null));

  /** List of transaction positions to be displayed in the table */
  readonly transactionPositionList = computed(() => this.securityTransactionSummary().transactionPositionList ?? []);
  readonly cellStyle = (position: SecurityTransactionPosition, field: ColumnConfig) =>
    this.getCellStyle(position, field);

  /**
   * Creates an instance of TransactionSecurityTableComponent with required dependencies.
   *
   */
  constructor() {
    super();
  }

  /**
   * Initializes the component by setting up table configuration and loading transaction data.
   */
  ngOnInit(): void {
    this.multiSortMeta.push({ field: 'transaction.transactionTime', order: 1 });
    this.currencyColumnConfigMC = TransactionSecurityFieldDefinition.getFieldDefinition(
      this,
      this.idTenant(),
      false,
      this.transactionSecurityOptionalParam(),
      this.gps
    );
    this.initialize();
  }

  /**
   * Records the current paginator page (keyed by security) on every page change so it survives this component being
   * destroyed and recreated by a parent reload.
   *
   * @param event Optimus page event; {@code event.first} is the index of the first row on the new page
   */
  override onPage(event: any): void {
    super.onPage(event);
    this.parentChildRegisterService.saveRowPosition(this.idSecuritycurrency(), this.pageFirstRowSelectedRow());
  }

  /**
   * Retrieves the security object associated with a given transaction.
   *
   * @param transaction The transaction object to get security information from
   * @returns The security object associated with the transaction
   */
  getSecurity(transaction: Transaction): Security {
    return this.securityTransactionSummary().securityPositionSummary.security;
  }

  /**
   * Handles row selection events in the transaction table.
   *
   * @param event The row selection event containing the selected transaction data
   */
  onRowSelect(event): void {
    const transactionPosition: TransactionPosition = event.data;
    this.selectedTransaction.set(transactionPosition.transaction);
    this.setMenuItemsToActivePanel();
  }

  /**
   * Highlights the estimated tax and transaction cost of the hypothetical sale when the disposal cost estimate could
   * not price them completely, because a fee model, a tax model or a matching rule is missing.
   *
   * @param position The row
   * @param field The column
   * @returns The highlighting style, or null for an ordinary cell
   */
  getCellStyle(position: SecurityTransactionPosition, field: ColumnConfig): { [key: string]: string } | null {
    if (
      (field.field === 'transaction.taxCost' && position.disposalTaxCostIncomplete) ||
      (field.field === 'transaction.transactionCost' && position.disposalTransactionCostIncomplete)
    ) {
      return DisposalCostHelper.INCOMPLETE_STYLE;
    }
    return null;
  }

  /** Cleanup method called when the component is destroyed. */
  ngOnDestroy(): void {
    super.destroy();
  }

  public override getHelpContextId(): string {
    return HelpIds.HELP_TRANSACTION_CASH_BASED;
  }

  /** Initializes the component data by loading security transaction summary and configuring display columns. */
  protected initialize(): void {
    this.dataSubscription?.unsubscribe();
    this.dataSubscription = BusinessHelper.getSecurityTransactionSummary(
      this.securityService,
      this.idSecuritycurrency(),
      this.idsSecurityaccount(),
      this.idPortfolio(),
      false,
      this.untilDate()
    )
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((result) => {
        this.securityTransactionSummary.set(result);
        this.createTranslatedValueStoreAndFilterField(this.securityTransactionSummary().transactionPositionList);
        this.goToFirsRowPosition(this.idSecuritycurrency());
        this.currencyColumnConfigMC.forEach((cc) => {
          cc.headerSuffix = this.securityTransactionSummary().securityPositionSummary.mainCurrency;
          cc.fixedCurrency = this.securityTransactionSummary().securityPositionSummary.mainCurrency;
          this.setFieldHeaderTranslation(cc);
        });
        TransactionSecurityFieldDefinition.applySecurityCurrency(
          this.fields,
          this.securityTransactionSummary().securityPositionSummary.security.currency
        );
      });
  }

  /**
   * Prepares transaction call parameters for transaction editing operations.
   *
   * @param transactionCallParam The transaction call parameter object to be configured
   */
  protected prepareTransactionCallParam(transactionCallParam: TransactionCallParam) {}
}
