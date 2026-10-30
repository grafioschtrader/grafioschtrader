import { Component, OnDestroy, OnInit, viewChild, input, linkedSignal, inject } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';
import { MenuItem } from '@openng/optimus-ui/api';
import { ContextMenuModule } from '@openng/optimus-ui/contextmenu';
import { Table, TableModule } from '@openng/optimus-ui/table';
import { TooltipModule } from '@openng/optimus-ui/tooltip';
import { TranslateValue } from '../../lib/datashowbase/column.config';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { Security } from '../../entities/security';
import { Transaction } from '../../entities/transaction';
import { TransactionCostGrandSummary } from '../../entities/view/transactioncost/transaction.cost.grand.summary';
import { TransactionCostPosition } from '../../entities/view/transactioncost/transaction.cost.position';
import { SecurityService } from '../../securitycurrency/service/security.service';
import { AppSettings } from '../../shared/app.settings';
import { TransactionCallParam } from '../../transaction/component/transaction.call.parm';
import { TransactionContextMenu } from '../../transaction/component/transaction.context.menu';
import { TransactionSecurityEditComponent } from '../../transaction/component/transaction-security-edit.component';

/**
 * Component that displays transaction cost positions in a tabular format with support for editing security transactions.
 * This component extends TransactionContextMenu to provide context menu functionality for transaction operations.
 * It is designed to be used as a nested table within larger transaction cost analysis views and provides detailed
 * information about transaction costs including currency conversions and tax costs in the main currency.
 */
@Component({
  selector: 'tenant-transaction-cost-extended',
  template: `
    <div
      #cmDiv
      class="data-container"
      (click)="onComponentClick($event)"
      [class]="{
        'active-border': isActivated(),
        'passiv-border': !isActivated()
      }">
      <div class="datatable nestedtable">
        <p-table
          [columns]="fields"
          [value]="transactionCostPositions()"
          selectionMode="single"
          [(selection)]="baseSelectedRow"
          #dataTable
          [first]="firstRowIndex()"
          (onRowSelect)="onRowSelect($event)"
          (onRowUnselect)="onRowUnselect($event)"
          (onPage)="onPage($event)"
          sortMode="multiple"
          [multiSortMeta]="multiSortMeta"
          dataKey="transaction.idTransaction"
          [paginator]="true"
          [rows]="20"
          stripedRows
          showGridlines>
          <ng-template #header let-fields>
            <tr>
              @for (field of fields; track field.field) {
                <th
                  [pSortableColumn]="field.field"
                  [style.max-width.px]="field.width"
                  [style]="field.width ? { 'flex-basis': '0 0 ' + field.width + 'px' } : {}">
                  {{ field.headerTranslated }}
                  <p-sortIcon [field]="field.field" />
                </th>
              }
            </tr>
          </ng-template>
          <ng-template #body let-el let-columns="fields">
            <tr [pSelectableRow]="el">
              @for (field of fields; track field.field) {
                @if (field.visible) {
                  <td
                    [style.max-width.px]="field.width"
                    [style]="field.width ? { 'flex-basis': '0 0 ' + field.width + 'px' } : {}"
                    [class]="
                      field.dataType === DataType.Numeric || field.dataType === DataType.DateTimeNumeric
                        ? 'text-end'
                        : ''
                    ">
                    <span [pTooltip]="getValueByPath(el, field)" tooltipPosition="top">{{
                      getValueByPath(el, field)
                    }}</span>
                  </td>
                }
              }
            </tr>
          </ng-template>
        </p-table>
        <p-contextMenu #cm [target]="cmDiv" [model]="contextMenuItems()" appendTo="body" />
      </div>
    </div>
    @if (visibleSecurityTransactionDialog()) {
      <transaction-security-edit
        [transactionCallParam]="transactionCallParam"
        [visibleSecurityTransactionDialog]="visibleSecurityTransactionDialog()"
        (closeDialog)="handleCloseTransactionDialog($event)" />
    }
  `,
  imports: [TranslateModule, TableModule, ContextMenuModule, TooltipModule, TransactionSecurityEditComponent]
})
export class TenantTransactionCostExtendedComponent extends TransactionContextMenu implements OnInit, OnDestroy {
  private securityService: SecurityService = inject(SecurityService);

  /** Array of transaction cost positions to be displayed in the table */
  readonly transactionCostPositions = input.required<TransactionCostPosition[]>();

  /** Grand summary containing aggregated transaction cost data and main currency information */
  readonly transactionCostGrandSummary = input.required<TransactionCostGrandSummary>();

  /** Currently selected row in the table */
  readonly baseSelectedRowInput = input<TransactionCostPosition | null>(undefined, { alias: 'baseSelectedRow' });
  readonly baseSelectedRow = linkedSignal(() => this.baseSelectedRowInput());

  /** Index of the first row displayed on the current page */
  readonly firstRowIndex = input.required<number>();

  /** Reference to the Optimus table component for accessing table functionality like CSV export */
  readonly dataTable = viewChild.required<Table>('dataTable');

  /** Currently selected transaction cost position for context menu operations */
  private selectedTransactionCostPosition: TransactionCostPosition | null;

  /**
   * Creates an instance of TenantTransactionCostExtendedComponent with all required dependencies.
   *
   */
  constructor() {
    super();
  }

  /**
   * Initializes the component by setting up table columns, sorting configuration, and preparing data for display.
   * Configures all necessary columns with appropriate data types, translations, and formatting options.
   */
  ngOnInit(): void {
    this.addColumn(DataType.DateString, 'transaction.transactionTime', 'DATE', true, false);
    this.addColumn(DataType.String, 'transaction.security.name', 'NAME', true, false, { width: 200 });

    this.addColumn(
      DataType.String,
      'transaction.security.stockexchange.name',
      AppSettings.STOCKEXCHANGE.toUpperCase(),
      true,
      false
    );
    this.addColumn(DataType.String, 'transaction.transactionType', 'TRANSACTION_TYPE', true, false, {
      translateValues: TranslateValue.NORMAL
    });
    this.addColumn(DataType.String, 'transaction.cashaccount.currency', 'CURRENCY_CASHACCOUNT', true, false);
    this.addColumn(DataType.String, 'transaction.security.currency', 'CURRENCY_SECURITY', true, false);
    this.addColumn(DataType.Numeric, 'transaction.transactionCost', 'TRANSACTION_COST', true, false, {
      currencyPrecisionField: 'transaction.security.currency'
    });
    const transactionCostGrandSummary = this.transactionCostGrandSummary();
    this.addColumn(DataType.Numeric, 'taxCostMC', 'TAX_COST', true, false, {
      headerSuffix: transactionCostGrandSummary.mainCurrency,
      fixedCurrency: transactionCostGrandSummary.mainCurrency
    });
    this.addColumn(DataType.Numeric, 'basePriceForTransactionCostMC', 'NET_VALUE_TRANSACTION', true, false, {
      headerSuffix: transactionCostGrandSummary.mainCurrency,
      fixedCurrency: transactionCostGrandSummary.mainCurrency
    });
    this.addColumn(DataType.Numeric, 'transaction.currencyExRate', 'EXCHANGE_RATE', true, false);
    this.addColumn(DataType.Numeric, 'transactionCostMC', 'TRANSACTION_COST', true, false, {
      headerSuffix: transactionCostGrandSummary.mainCurrency,
      fixedCurrency: transactionCostGrandSummary.mainCurrency
    });
    this.multiSortMeta.push({
      field: 'transaction.transactionTime',
      order: -1
    });
    this.prepareTableAndTranslate();
    this.createTranslatedValueStoreAndFilterField(this.transactionCostPositions());
    const row = this.baseSelectedRow();
    if (row) this.selectedTransaction.set(row.transaction);
  }

  /**
   * Retrieves the security associated with a given transaction.
   *
   * @param transaction The transaction for which to retrieve the security
   * @returns The security object associated with the currently selected transaction cost position
   */
  getSecurity(transaction: Transaction): Security | undefined {
    return transaction.security;
  }

  /**
   * Handles row selection events in the table by updating the selected transaction and activating the panel.
   *
   * @param event The row selection event containing the selected data
   */
  onRowSelect(event: { data?: TransactionCostPosition | TransactionCostPosition[] }): void {
    const row = (Array.isArray(event.data) ? event.data[0] : event.data) ?? null;
    this.selectedTransactionCostPosition = row;
    this.selectedTransaction.set(row?.transaction ?? null);
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
      { separator: true },
      { label: 'EXPORT_CSV', command: (e) => this.dataTable().exportCSV() }
    ];
    return super.getMenuItemsOnTransaction(transaction).concat(localContextMenu);
  }

  /** Placeholder method for component initialization - no additional initialization required */
  protected initialize(): void {}

  /**
   * Prepares transaction call parameters for transaction operations.
   *
   * @param transactionCallParam The transaction call parameter object to be prepared
   */
  protected prepareTransactionCallParam(transactionCallParam: TransactionCallParam) {}
}
