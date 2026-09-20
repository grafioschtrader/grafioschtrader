import { Component, Input, Output, EventEmitter, OnChanges, ChangeDetectionStrategy } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { FilterService } from '@openng/optimus-ui/api';
import { TableConfigBase } from '../../lib/datashowbase/table.config.base';
import { ConfigurableTableComponent } from '../../lib/datashowbase/configurable-table.component';
import { GlobalparameterService } from '../../lib/services/globalparameter.service';
import { UserSettingsService } from '../../lib/services/user.settings.service';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { TranslateValue } from '../../lib/datashowbase/column.config';

/** Standard table rendering for resolved evaluations and recorded notification deliveries. */
@Component({
  selector: 'algo-alert-diagnostics-table',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.Eager,
  imports: [ConfigurableTableComponent],
  template: `<configurable-table
    [data]="rows"
    [fields]="fields"
    [dataKey]="notification ? 'id' : 'rowKey'"
    [valueGetterFn]="getValueByPath.bind(this)"
    [baseLocale]="baseLocale"
    [customSortFn]="customSort.bind(this)"
    [multiSortMeta]="multiSortMeta"
    (selectionChange)="selected.emit($event)" />`
})
export class AlgoAlertDiagnosticsTableComponent extends TableConfigBase implements OnChanges {
  @Input() rows: any[] = [];
  @Input() notification = false;
  @Input() trading = false;
  @Output() selected = new EventEmitter<any>();
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
      if (this.trading) {
        this.addColumnFeqH(DataType.String, 'contextName');
        this.addColumn(DataType.String, 'strategyName', 'ALGO_STRATEGY_NAME');
        this.addColumnFeqH(DataType.String, 'securityName');
        this.addColumnFeqH(DataType.DateString, 'valuationDate');
        this.addColumnFeqH(DataType.String, 'recommendedAction', true, false, {
          translateValues: TranslateValue.NORMAL
        });
        this.addColumnFeqH(DataType.Numeric, 'recommendedUnits');
        this.addColumnFeqH(DataType.Numeric, 'recommendedAmount');
        this.addColumnFeqH(DataType.String, 'currency');
        this.addColumn(DataType.Numeric, 'price', 'QUOTATION_DIV');
        this.addColumn(DataType.String, 'rationale', 'REASON', true, false, { translateValues: TranslateValue.NORMAL });
      } else if (this.notification) {
        this.addColumnFeqH(DataType.String, 'contextName');
        this.addColumnFeqH(DataType.String, 'securityName');
        this.addColumnFeqH(DataType.DateTimeString, 'alertTime');
        this.addColumnFeqH(DataType.String, 'deliveryStatus', true, false, { translateValues: TranslateValue.NORMAL });
        this.addColumnFeqH(DataType.String, 'deliveryChannels', true, false, {
          translateValues: TranslateValue.NORMAL
        });
        this.addColumnFeqH(DataType.NumericInteger, 'deliveryAttempts');
        for (const field of ['nextAttemptAt', 'internalCompletedAt', 'externalCompletedAt'])
          this.addColumnFeqH(DataType.DateTimeString, field);
        this.addColumnFeqH(DataType.String, 'deliveryError');
        this.addColumnFeqH(DataType.String, 'alarmDetails');
      } else {
        this.addColumnFeqH(DataType.String, 'contextName');
        this.addColumnFeqH(DataType.String, 'securityName');
        this.addColumnFeqH(DataType.String, 'strategyType', true, false, { translateValues: TranslateValue.NORMAL });
        this.addColumnFeqH(DataType.Boolean, 'active');
        this.addColumnFeqH(DataType.String, 'outcome', true, false, { translateValues: TranslateValue.NORMAL });
        for (const field of ['lastAttempt', 'lastSuccess', 'quoteTimestamp'])
          this.addColumnFeqH(DataType.DateTimeString, field);
        this.addColumnFeqH(DataType.String, 'reason');
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
    this.createTranslatedValueStore(this.rows);
  }
}
