import { Component, Input, OnChanges, ChangeDetectionStrategy } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { FilterService } from '@openng/optimus-ui/api';
import { TableConfigBase } from '../../lib/datashowbase/table.config.base';
import { ConfigurableTableComponent } from '../../lib/datashowbase/configurable-table.component';
import { GlobalparameterService } from '../../lib/services/globalparameter.service';
import { UserSettingsService } from '../../lib/services/user.settings.service';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { FilterType } from '../../lib/datashowbase/filter.type';
import { TranslateValue } from '../../lib/datashowbase/column.config';

/** Standard table rendering for resolved alert evaluations and for the current trading decisions. */
@Component({
  selector: 'algo-alert-diagnostics-table',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.Eager,
  imports: [ConfigurableTableComponent],
  template: `<configurable-table
    [data]="rows"
    [fields]="fields"
    dataKey="rowKey"
    [valueGetterFn]="getValueByPath.bind(this)"
    [baseLocale]="baseLocale"
    [customSortFn]="customSort.bind(this)"
    [multiSortMeta]="multiSortMeta"
    [paginator]="true"
    [rows]="rowsPerPage"
    (pageChange)="onPage($event)"
    [hasFilter]="hasFilter" />`
})
export class AlgoAlertDiagnosticsTableComponent extends TableConfigBase implements OnChanges {
  @Input() rows: any[] = [];
  @Input() trading = false;
  private initialized = false;
  constructor(
    filter: FilterService,
    settings: UserSettingsService,
    translate: TranslateService,
    gps: GlobalparameterService
  ) {
    super(filter, settings, translate, gps);
    this.rowsPerPage = 20;
  }
  ngOnChanges(): void {
    if (!this.initialized) {
      if (this.trading) {
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
      this.initialized = true;
    }
    this.rows.forEach(
      (row) =>
        (row.rowKey = this.trading
          ? `${row.idAlgoStrategy}:${row.idSecuritycurrency}`
          : `${row.strategyId}:${row.securityId}`)
    );
    this.createTranslatedValueStoreAndFilterField(this.rows);
    this.prepareFilter(this.rows);
  }
}
