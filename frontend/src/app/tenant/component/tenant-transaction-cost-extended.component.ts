import {Component, Input, OnDestroy, OnInit, ViewChild} from '@angular/core';
import {TransactionCostPosition} from '../../entities/view/transactioncost/transaction.cost.position';
import {TransactionCostGrandSummary} from '../../entities/view/transactioncost/transaction.cost.grand.summary';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {TranslateService} from '@ngx-translate/core';
import {UserSettingsService} from '../../lib/services/user.settings.service';
import {DataType} from '../../lib/dynamic-form/models/data.type';
import {TransactionContextMenu} from '../../transaction/component/transaction.context.menu';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {TransactionService} from '../../transaction/service/transaction.service';
import {ActivePanelService} from '../../lib/mainmenubar/service/active.panel.service';
import {SecurityService} from '../../securitycurrency/service/security.service';
import {Transaction} from '../../entities/transaction';
import {Security} from '../../entities/security';
import {TransactionCallParam} from '../../transaction/component/transaction.call.parm';
import {ParentChildRegisterService} from '../../shared/service/parent.child.register.service';
import {TranslateHelper} from '../../lib/helper/translate.helper';
import {Table} from 'primeng/table';
import {ConfirmationService, FilterService, MenuItem} from 'primeng/api';
import {TranslateValue} from '../../lib/datashowbase/column.config';
import {AppSettings} from '../../shared/app.settings';

/**
 * Component that displays transaction cost positions in a tabular format with support for editing security transactions.
 * This component extends TransactionContextMenu to provide context menu functionality for transaction operations.
 * It is designed to be used as a nested table within larger transaction cost analysis views and provides detailed
 * information about transaction costs including currency conversions and tax costs in the main currency.
 */
@Component({
  selector: 'tenant-transaction-cost-extended',
  template: `
    <div #cmDiv class="data-container" (click)="onComponentClick($event)"
         [ngClass]="{'active-border': isActivated(), 'passiv-border': !isActivated()}">
      <div class="datatable nestedtable">
        <p-table [columns]="fields" [value]="transactionCostPositions" selectionMode="single"
                 [(selection)]="baseSelectedRow" #dataTable [first]="firstRowIndex" (onRowSelect)="onRowSelect($event)"
                 (onRowUnselect)="onRowUnselect($event)" (onPage)="onPage($event)"
                 sortMode="multiple" [multiSortMeta]="multiSortMeta"
                 dataKey="transaction.idTransaction" [paginator]="true" [rows]="20"
                 stripedRows showGridlines>
          <ng-template #header let-fields>
            <tr>
              @for (field of fields; track field.field) {
                <th [pSortableColumn]="field.field" [style.max-width.px]="field.width"
                    [ngStyle]="field.width? {'flex-basis': '0 0 ' + field.width + 'px'}: {}">
                  {{ field.headerTranslated }}
                  <p-sortIcon [field]="field.field"></p-sortIcon>
                </th>
              }
            </tr>
          </ng-template>
          <ng-template #body let-el let-columns="fields">
            <tr [pSelectableRow]="el">
              @for (field of fields; track field.field) {
                @if (field.visible) {
                  <td [style.max-width.px]="field.width"
                      [ngStyle]="field.width? {'flex-basis': '0 0 ' + field.width + 'px'}: {}"
                      [ngClass]="(field.dataType===DataType.Numeric || field.dataType===DataType.DateTimeNumeric)? 'text-right': ''">
                  <span [pTooltip]="getValueByPath(el, field)"
                        tooltipPosition="top">{{ getValueByPath(el, field) }}</span>
                  </td>
                }
              }
            </tr>
          </ng-template>
        </p-table>
        <p-contextMenu #cm [target]="cmDiv" [model]="contextMenuItems" appendTo="body"></p-contextMenu>
      </div>
    </div>
    @if (visibleSecurityTransactionDialog) {
      <transaction-security-edit [transactionCallParam]="transactionCallParam"
                                 [visibleSecurityTransactionDialog]="visibleSecurityTransactionDialog"
                                 (closeDialog)="handleCloseTransactionDialog($event)">
      </transaction-security-edit>
    }
  `,
  standalone: false
})
export class TenantTransactionCostExtendedComponent extends TransactionContextMenu implements OnInit, OnDestroy {
  /** Array of transaction cost positions to be displayed in the table */
  @Input() transactionCostPositions: TransactionCostPosition[];

  /** Grand summary containing aggregated transaction cost data and main currency information */
  @Input() transactionCostGrandSummary: TransactionCostGrandSummary;

  /** Currently selected row in the table */
  @Input() baseSelectedRow: TransactionCostPosition;

  /** Index of the first row displayed on the current page */
  @Input() firstRowIndex: number;

  /** Reference to the PrimeNG table component for accessing table functionality like CSV export */
  @ViewChild('dataTable', {static: true}) dataTable: Table;

  /** Currently selected transaction cost position for context menu operations */
  private selectedTransactionCostPosition: TransactionCostPosition;

  /**
   * Creates an instance of TenantTransactionCostExtendedComponent with all required dependencies.
   *
   * @param securityService Service for handling security-related operations
   * @param parentChildRegisterService Service for managing parent-child component relationships
   * @param activePanelService Service for managing active panel state
   * @param transactionService Service for transaction-related operations
   * @param confirmationService Service for displaying confirmation dialogs
   * @param messageToastService Service for displaying toast messages
   * @param filterService Service for table filtering functionality
   * @param translateService Service for internationalization and translation
   * @param gps Global parameter service for application-wide settings
   * @param usersettingsService Service for managing user-specific settings
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
   * Initializes the component by setting up table columns, sorting configuration, and preparing data for display.
   * Configures all necessary columns with appropriate data types, translations, and formatting options.
   */
  ngOnInit(): void {
    this.addColumn(DataType.DateString, 'transaction.transactionTime', 'DATE', true, false);
    this.addColumn(DataType.String, 'transaction.security.name', 'NAME', true, false, {width: 200});

    this.addColumn(DataType.String, 'transaction.security.stockexchange.name', AppSettings.STOCKEXCHANGE.toUpperCase(), true, false);
    this.addColumn(DataType.String, 'transaction.transactionType', 'TRANSACTION_TYPE', true, false,
      {translateValues: TranslateValue.NORMAL});
    this.addColumn(DataType.String, 'transaction.cashaccount.currency', 'CURRENCY_CASHACCOUNT', true, false);
    this.addColumn(DataType.String, 'transaction.security.currency', 'CURRENCY_SECURITY', true, false);
    this.addColumn(DataType.Numeric, 'transaction.transactionCost', 'TRANSACTION_COST', true, false);
    this.addColumn(DataType.Numeric, 'taxCostMC', 'TAX_COST', true, false,
      {headerSuffix: this.transactionCostGrandSummary.mainCurrency});
    this.addColumn(DataType.Numeric, 'basePriceForTransactionCostMC', 'NET_VALUE_TRANSACTION', true, false,
      {headerSuffix: this.transactionCostGrandSummary.mainCurrency});
    this.addColumn(DataType.Numeric, 'transaction.currencyExRate', 'EXCHANGE_RATE', true, false);
    this.addColumn(DataType.Numeric, 'transactionCostMC', 'TRANSACTION_COST', true, false,
      {headerSuffix: this.transactionCostGrandSummary.mainCurrency});
    this.multiSortMeta.push({field: 'transaction.transactionTime', order: -1});
    this.prepareTableAndTranslate();
    this.createTranslatedValueStoreAndFilterField(this.transactionCostPositions);
    if (this.baseSelectedRow) {
      this.selectedTransaction = this.baseSelectedRow.transaction;
    }
  }

  /**
   * Retrieves the security associated with a given transaction.
   *
   * @param transaction The transaction for which to retrieve the security
   * @returns The security object associated with the currently selected transaction cost position
   */
  getSecurity(transaction: Transaction): Security {
    return this.selectedTransactionCostPosition.transaction.security;
  }

  /**
   * Handles row selection events in the table by updating the selected transaction and activating the panel.
   *
   * @param event The row selection event containing the selected data
   */
  onRowSelect(event): void {
    this.selectedTransactionCostPosition = event.data;
    this.selectedTransaction = this.selectedTransactionCostPosition.transaction;
    this.setMenuItemsToActivePanel();
  }

  /**
   * Cleanup method called when the component is destroyed to properly release resources.
   */
  ngOnDestroy(): void {
    super.destroy();
  }

  /**
   * Generates context menu items for transaction operations including CSV export functionality.
   *
   * @param transaction The transaction for which to generate menu items
   * @returns Array of menu items including parent menu items and CSV export option
   */
  protected override getMenuItemsOnTransaction(transaction: Transaction): MenuItem[] {
    const localContextMenu: MenuItem[] = [
      {separator: true},
      {label: 'EXPORT_CSV', command: (e) => this.dataTable.exportCSV()}
    ];
    TranslateHelper.translateMenuItems(localContextMenu, this.translateService);
    return super.getMenuItemsOnTransaction(transaction).concat(localContextMenu);
  }

  /** Placeholder method for component initialization - no additional initialization required */
  protected initialize(): void {
  }

  /**
   * Prepares transaction call parameters for transaction operations.
   *
   * @param transactionCallParam The transaction call parameter object to be prepared
   */
  protected prepareTransactionCallParam(transactionCallParam: TransactionCallParam) {
  }

}
