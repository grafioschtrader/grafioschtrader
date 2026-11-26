import { Component, effect, input, untracked } from '@angular/core';

import { ConfigurableTableComponent } from '../../lib/datashowbase/configurable-table.component';
import { TableConfigBase } from '../../lib/datashowbase/table.config.base';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import type { RealizedGainGrandTotal } from '../../entities/view/realizedgain/realized.gain.grand.total';
import { TenantRealizedGainsSaleTableComponent } from './tenant-realized-gains-sale-table.component';

/**
 * The table of the realized gain report with one row per year. The sums of the sales on the market and the net sum of
 * the system bookings of a transfer or an ISIN change are separate columns, because a system booking realizes a gain no
 * market sale produced. A year expands to its sales.
 */
@Component({
  selector: 'tenant-realized-gains-year-table',
  template: `
    <configurable-table
      [data]="grandTotal().years"
      [fields]="fields"
      [fieldsRevision]="fieldsRevision()"
      dataKey="year"
      [customSortFn]="customSorter"
      [multiSortMeta]="multiSortMeta"
      [valueGetterFn]="valueGetter"
      [contextMenuEnabled]="false"
      [scrollable]="false"
      [expandable]="true"
      [expandedRowTemplate]="sales"
      [baseLocale]="baseLocale" />
    <ng-template #sales let-yearGroup>
      <tenant-realized-gains-sale-table [positions]="yearGroup.positions" [mainCurrency]="grandTotal().mainCurrency" />
    </ng-template>
  `,
  imports: [ConfigurableTableComponent, TenantRealizedGainsSaleTableComponent]
})
export class TenantRealizedGainsYearTableComponent extends TableConfigBase {
  /** The report as the server delivered it. */
  readonly grandTotal = input.required<RealizedGainGrandTotal>();

  private readonly mainCurrencyColumns = ['yearGainMC', 'yearLossMC', 'yearNetMC', 'systemNetMC'];

  constructor() {
    super();
    // use a string to avoid number format
    this.addColumn(DataType.String, 'year', 'YEAR', true, false);
    this.addColumn(DataType.Numeric, 'yearGainMC', 'REALIZED_YEAR_GAIN', true, false);
    this.addColumn(DataType.Numeric, 'yearLossMC', 'REALIZED_YEAR_LOSS', true, false);
    this.addColumn(DataType.Numeric, 'yearNetMC', 'REALIZED_YEAR_NET', true, false, { gainLossColor: true });
    this.addColumn(DataType.NumericInteger, 'saleCount', 'REALIZED_SALE_COUNT', true, false);
    this.addColumn(DataType.Numeric, 'systemNetMC', 'REALIZED_SYSTEM_NET', true, false, { gainLossColor: true });
    this.addColumn(DataType.NumericInteger, 'systemBookingCount', 'REALIZED_SYSTEM_COUNT', true, false);
    this.addColumn(DataType.Boolean, 'incomplete', 'REALIZED_INCOMPLETE', true, false);
    this.multiSortMeta.push({ field: 'year', order: -1 });
    this.prepareTableAndTranslate();
    effect(() => {
      const grandTotal = this.grandTotal();
      untracked(() => {
        this.fields
          .filter((field) => this.mainCurrencyColumns.includes(field.field))
          .forEach((field) => {
            field.headerSuffix = grandTotal.mainCurrency;
            field.fixedCurrency = grandTotal.mainCurrency;
          });
        this.prepareTableAndTranslate();
        this.createTranslatedValueStore(grandTotal.years);
      });
    });
  }
}
