import { Component, input, effect, untracked } from '@angular/core';
import { TableConfigBase } from '../../lib/datashowbase/table.config.base';
import { ConfigurableTableComponent } from '../../lib/datashowbase/configurable-table.component';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { TranslateValue } from '../../lib/datashowbase/column.config';

/** Tax previews and persisted run diagnostics share the standard sortable, localized table presentation. */
@Component({
  selector: 'tax-details-table',
  imports: [ConfigurableTableComponent],
  template: `<configurable-table
    [data]="rows()"
    [fields]="fields"
    [fieldsRevision]="fieldsRevision()"
    [valueGetterFn]="valueGetter"
    [baseLocale]="baseLocale"
    [customSortFn]="customSorter"
    [multiSortMeta]="multiSortMeta" />`
})
export class TaxDetailsTableComponent extends TableConfigBase {
  readonly rows = input<any[]>([]);
  readonly mode = input<'estimate' | 'matches' | 'warnings' | 'income'>('warnings');
  /** Currency inherited from the estimate for match rows, which do not repeat it on every diagnostic. */
  readonly currency = input<string>(undefined);

  constructor() {
    super();

    effect(() => {
      this.mode();
      untracked(() => {
        this.configureColumns();
      });
    });
    effect(() => {
      this.rows();
      this.mode();
      this.currency();
      untracked(() => {
        if (this.mode() === 'matches') {
          this.fields.find((field) => field.field === 'amount').fixedCurrency = this.currency();
          this.notifyViewChanged();
        }
        this.createTranslatedValueStore(this.rows());
      });
    });
  }

  private configureColumns(): void {
    this.removeAllColumns();
    const mode = this.mode();
    const columns: [string, DataType][] =
      mode === 'estimate'
        ? [
            ['estimatedTax', DataType.Numeric],
            ['currency', DataType.String]
          ]
        : mode === 'matches'
          ? [
              ['country', DataType.String],
              ['section', DataType.String],
              ['validFrom', DataType.DateString],
              ['validTo', DataType.DateString],
              ['rule', DataType.String],
              ['amount', DataType.Numeric]
            ]
          : mode === 'income'
            ? [
                ['kind', DataType.String],
                ['currency', DataType.String],
                ['grossPaid', DataType.Numeric],
                ['withholdingPaid', DataType.Numeric],
                ['netPaid', DataType.Numeric],
                ['grossReceivables', DataType.Numeric],
                ['estimatedWithholding', DataType.Numeric],
                ['netReceivables', DataType.Numeric],
                ['reconciliation', DataType.Numeric]
              ]
            : [
                ['code', DataType.String],
                ['country', DataType.String],
                ['section', DataType.String],
                ['security', DataType.NumericInteger],
                ['account', DataType.NumericInteger],
                ['count', DataType.NumericInteger],
                ['firstDate', DataType.DateString],
                ['lastDate', DataType.DateString],
                ['eventIdentity', DataType.String],
                ['detail', DataType.String]
              ];
    for (const [name, type] of columns)
      this.addColumnFeqH(
        type,
        name,
        true,
        false,
        ['code', 'kind', 'section'].includes(name) ? { translateValues: TranslateValue.UPPER_CASE } : {}
      );
    const modeValue = this.mode();
    if (modeValue === 'estimate') {
      this.fields.find((field) => field.field === 'estimatedTax').currencyPrecisionField = 'currency';
    } else if (modeValue === 'income') {
      this.fields
        .filter((field) => field.dataType === DataType.Numeric)
        .forEach((field) => (field.currencyPrecisionField = 'currency'));
    }
    this.prepareTableAndTranslate();
  }
}
