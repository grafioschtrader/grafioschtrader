import { Component, Input, OnChanges, ChangeDetectionStrategy } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { FilterService } from '@openng/optimus-ui/api';
import { TableConfigBase } from '../../lib/datashowbase/table.config.base';
import { ConfigurableTableComponent } from '../../lib/datashowbase/configurable-table.component';
import { GlobalparameterService } from '../../lib/services/globalparameter.service';
import { UserSettingsService } from '../../lib/services/user.settings.service';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { TranslateValue } from '../../lib/datashowbase/column.config';

/** Tax previews and persisted run diagnostics share the standard sortable, localized table presentation. */
@Component({
  selector: 'tax-details-table',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.Eager,
  imports: [ConfigurableTableComponent],
  template: `<configurable-table
    [data]="rows"
    [fields]="fields"
    [valueGetterFn]="getValueByPath.bind(this)"
    [baseLocale]="baseLocale"
    [customSortFn]="customSort.bind(this)"
    [multiSortMeta]="multiSortMeta" />`
})
export class TaxDetailsTableComponent extends TableConfigBase implements OnChanges {
  @Input() rows: any[] = [];
  @Input() mode: 'estimate' | 'matches' | 'warnings' | 'income' = 'warnings';
  /** Currency inherited from the estimate for match rows, which do not repeat it on every diagnostic. */
  @Input() currency?: string;
  private initialized = false;

  constructor(
    filter: FilterService,
    settings: UserSettingsService,
    translate: TranslateService,
    gps: GlobalparameterService
  ) {
    super(filter, settings, translate, gps);
  }

  ngOnChanges(): void {
    if (!this.initialized) {
      const columns: [string, DataType][] =
        this.mode === 'estimate'
          ? [
              ['estimatedTax', DataType.Numeric],
              ['currency', DataType.String]
            ]
          : this.mode === 'matches'
            ? [
                ['country', DataType.String],
                ['section', DataType.String],
                ['validFrom', DataType.DateString],
                ['validTo', DataType.DateString],
                ['rule', DataType.String],
                ['amount', DataType.Numeric]
              ]
            : this.mode === 'income'
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
      if (this.mode === 'estimate') {
        this.fields.find((field) => field.field === 'estimatedTax').currencyPrecisionField = 'currency';
      } else if (this.mode === 'income') {
        this.fields
          .filter((field) => field.dataType === DataType.Numeric)
          .forEach((field) => (field.currencyPrecisionField = 'currency'));
      }
      this.prepareTableAndTranslate();
      this.initialized = true;
    }
    if (this.mode === 'matches') {
      this.fields.find((field) => field.field === 'amount').fixedCurrency = this.currency;
    }
    this.createTranslatedValueStore(this.rows);
  }
}
