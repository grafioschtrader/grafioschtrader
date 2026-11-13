import { Component, effect, input, untracked } from '@angular/core';

import { ConfigurableTableComponent } from '../../lib/datashowbase/configurable-table.component';
import { TableConfigBase } from '../../lib/datashowbase/table.config.base';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { TranslateValue } from '../../lib/datashowbase/column.config';
import type { RealizedGainPosition } from '../../entities/view/realizedgain/realized.gain.grand.total';
import { TransactionConsumedLotsTableComponent } from '../../transaction/component/transaction-consumed-lots-table.component';

/**
 * Shows the sales of one year of the realized gain report. A sale calculated by FIFO or LIFO expands to the purchases
 * it consumed. A system booking, the sale of a security account transfer or an ISIN change, is marked.
 */
@Component({
  selector: 'tenant-realized-gains-sale-table',
  template: `
    <configurable-table
      [data]="positions()"
      [fields]="fields"
      [fieldsRevision]="fieldsRevision()"
      dataKey="idTransaction"
      [customSortFn]="customSorter"
      [multiSortMeta]="multiSortMeta"
      [valueGetterFn]="valueGetter"
      [contextMenuEnabled]="false"
      [scrollable]="false"
      [expandable]="hasConsumedLots()"
      [canExpandFn]="canExpand"
      [expandedRowTemplate]="consumedLots"
      [baseLocale]="baseLocale" />
    <ng-template #consumedLots let-position>
      <transaction-consumed-lots-table [lots]="position.consumedLots" />
    </ng-template>
  `,
  imports: [ConfigurableTableComponent, TransactionConsumedLotsTableComponent]
})
export class TenantRealizedGainsSaleTableComponent extends TableConfigBase {
  /** The sales of one year, as the server delivered them. */
  readonly positions = input<RealizedGainPosition[]>([]);
  /** The currency of the main currency columns, set by the report that owns this table. */
  readonly mainCurrency = input('');

  /** A sale expands when it names the purchases it consumed. */
  readonly canExpand = (position: RealizedGainPosition): boolean => !!position.consumedLots?.length;
  protected hasConsumedLots(): boolean {
    return this.positions().some((position) => !!position.consumedLots?.length);
  }

  private readonly mainCurrencyColumns: string[] = ['gainLossMC'];

  constructor() {
    super();
    this.addColumn(DataType.DateString, 'transactionTime', 'DATE', true, false, { width: 100 });
    this.addColumn(DataType.String, 'security.name', 'SECURITY', true, false);
    this.addColumn(DataType.String, 'securityCurrency', 'CURRENCY', true, false, { width: 70 });
    this.addColumn(DataType.Numeric, 'units', 'QUANTITY', true, false);
    this.addColumn(DataType.Numeric, 'proceeds', 'REALIZED_PROCEEDS', true, false, {
      currencyPrecisionField: 'securityCurrency'
    });
    this.addColumn(DataType.Numeric, 'costBase', 'REALIZED_COST_BASE', true, false, {
      currencyPrecisionField: 'securityCurrency'
    });
    this.addColumn(DataType.Numeric, 'gainLoss', 'REALIZED_GAIN_LOSS', true, false, {
      currencyPrecisionField: 'securityCurrency'
    });
    this.addColumn(DataType.Numeric, 'gainLossPercentage', 'REALIZED_GAIN_LOSS_PERCENTAGE', true, false);
    this.addColumn(DataType.Numeric, 'exchangeRate', 'EXCHANGE_RATE', true, false, {
      maxFractionDigits: this.gps.getMaxFractionDigits()
    });
    this.addColumn(DataType.Numeric, 'gainLossMC', 'REALIZED_GAIN_LOSS_MC', true, false);
    this.addColumn(DataType.String, 'costBasisMethod', 'COST_BASIS_METHOD', true, false, {
      translateValues: TranslateValue.NORMAL
    });
    this.addColumn(DataType.Boolean, 'systemBooking', 'SYSTEM_BOOKING', true, false);
    this.addColumn(DataType.Boolean, 'costBasisIncomplete', 'REALIZED_INCOMPLETE', true, false);
    this.multiSortMeta.push({ field: 'transactionTime', order: 1 });
    this.prepareTableAndTranslate();
    effect(() => {
      const positions = this.positions();
      const mainCurrency = this.mainCurrency();
      untracked(() => {
        this.fields
          .filter((field) => this.mainCurrencyColumns.includes(field.field))
          .forEach((field) => {
            field.fixedCurrency = mainCurrency || undefined;
          });
        this.createTranslatedValueStore(positions);
        this.notifyViewChanged();
      });
    });
  }
}
