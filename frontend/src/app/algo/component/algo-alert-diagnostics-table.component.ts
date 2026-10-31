import { Component, input, computed, effect, untracked } from '@angular/core';
import { TableConfigBase } from '../../lib/datashowbase/table.config.base';
import { ConfigurableTableComponent } from '../../lib/datashowbase/configurable-table.component';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { FilterType } from '../../lib/datashowbase/filter.type';
import { TranslateValue } from '../../lib/datashowbase/column.config';

/** Standard table rendering for resolved alert evaluations and for the current trading decisions. */
@Component({
  selector: 'algo-alert-diagnostics-table',
  imports: [ConfigurableTableComponent],
  template: `<configurable-table
    [data]="displayRows()"
    [fields]="fields"
    [fieldsRevision]="fieldsRevision()"
    dataKey="rowKey"
    [valueGetterFn]="valueGetter"
    [baseLocale]="baseLocale"
    [customSortFn]="customSorter"
    [multiSortMeta]="multiSortMeta"
    [paginator]="true"
    [rows]="rowsPerPage ?? 20"
    (pageChange)="onPage($event)"
    [hasFilter]="hasFilter" />`
})
export class AlgoAlertDiagnosticsTableComponent extends TableConfigBase {
  readonly rows = input<any[]>([]);
  readonly trading = input(false);
  constructor() {
    super();
    this.rowsPerPage = 20;

    effect(() => {
      this.trading();
      untracked(() => {
        this.configureColumns();
      });
    });
    effect(() => {
      this.displayRows();
      untracked(() => {
        const rows = this.displayRows();
        this.createTranslatedValueStoreAndFilterField(rows);
        this.prepareFilter(rows);
      });
    });
  }
  private configureColumns(): void {
    this.removeAllColumns();
    this.multiSortMeta = [];
    if (this.trading()) {
      this.addColumnFeqH(DataType.String, 'contextName', true, false, { filterType: FilterType.withOptions });
      this.addColumn(DataType.String, 'strategyName', 'ALGO_STRATEGY_NAME', true, false, {
        filterType: FilterType.withOptions
      });
      this.addColumnFeqH(DataType.String, 'securityName');
      this.addColumnFeqH(DataType.DateString, 'valuationDate');
      this.addColumnFeqH(DataType.String, 'recommendedAction', true, false, {
        translateValues: TranslateValue.NORMAL,
        filterType: FilterType.withOptions
      });
      this.addColumnFeqH(DataType.Numeric, 'recommendedUnits');
      this.addColumnFeqH(DataType.Numeric, 'recommendedAmount');
      this.addColumnFeqH(DataType.String, 'currency');
      this.addColumn(DataType.Numeric, 'price', 'QUOTATION_DIV');
      this.addColumn(DataType.String, 'rationale', 'REASON', true, false, { translateValues: TranslateValue.NORMAL });
      this.multiSortMeta.push({ field: 'valuationDate', order: -1 });
    } else {
      this.addColumnFeqH(DataType.String, 'contextName', true, false, { filterType: FilterType.withOptions });
      this.addColumnFeqH(DataType.String, 'securityName');
      this.addColumnFeqH(DataType.String, 'strategyType', true, false, {
        translateValues: TranslateValue.NORMAL,
        filterType: FilterType.withOptions
      });
      this.addColumnFeqH(DataType.Boolean, 'active', true, false, { templateName: 'check' });
      this.addColumnFeqH(DataType.String, 'outcome', true, false, {
        translateValues: TranslateValue.NORMAL,
        filterType: FilterType.withOptions
      });
      for (const field of ['lastAttempt', 'lastSuccess', 'quoteTimestamp'])
        this.addColumnFeqH(DataType.DateTimeString, field);
      this.addColumnFeqH(DataType.String, 'reason', true, false, { translateValues: TranslateValue.NORMAL });
      this.multiSortMeta.push({ field: 'contextName', order: 1 });
    }
    this.prepareTableAndTranslate();
  }
  readonly displayRows = computed(() => {
    const trading = this.trading();
    return this.rows().map((row) => ({
      ...row,
      rowKey: trading ? row.idAlgoStrategy + ':' + row.idSecuritycurrency : row.strategyId + ':' + row.securityId
    }));
  });
}
