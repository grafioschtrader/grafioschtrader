import {Component, Input, OnChanges, OnInit, SimpleChanges} from '@angular/core';
import {CommonModule} from '@angular/common';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {ShowRecordConfigBase} from '../../lib/datashowbase/show.record.config.base';
import {ConfigurableTableComponent} from '../../lib/datashowbase/configurable-table.component';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {DataType} from '../../lib/dynamic-form/models/data.type';
import {StandingOrderFailure} from '../../entities/standing.order';

/**
 * Displays execution failures for a single standing order. Uses ConfigurableTableComponent with its own
 * row expansion for showing unexpected error stack traces in a textarea, following the TaskDataChangeTable pattern.
 */
@Component({
  selector: 'standing-order-failure-table',
  template: `
    <h5>{{ 'EXECUTION_FAILURES' | translate }}</h5>
    <configurable-table
      [data]="failures"
      [fields]="fields"
      [dataKey]="'idStandingOrderFailure'"
      [expandable]="true"
      [canExpandFn]="canExpandRow.bind(this)"
      [expandedRowTemplate]="stackTraceExpansion"
      [selectionMode]="null"
      [scrollable]="false"
      [valueGetterFn]="getValueByPath.bind(this)"
      [baseLocale]="baseLocale">
    </configurable-table>

    <ng-template #stackTraceExpansion let-failure>
      <textarea [rows]="getShowLines(failure.unexpectedError)"
                style="width:100%;" readonly>{{failure.unexpectedError}}</textarea>
    </ng-template>
  `,
  standalone: true,
  imports: [CommonModule, TranslateModule, ConfigurableTableComponent]
})
export class StandingOrderFailureTableComponent extends ShowRecordConfigBase implements OnInit, OnChanges {

  @Input() failures: StandingOrderFailure[] = [];

  private fieldsInitialized = false;

  constructor(translateService: TranslateService, gps: GlobalparameterService) {
    super(translateService, gps);
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['failures'] && this.failures) {
      if (this.fieldsInitialized) {
        this.translateHeadersAndColumns();
      }
    }
  }

  ngOnInit(): void {
    this.addColumnFeqH(DataType.DateString, 'executionDate', true, false);
    this.addColumnFeqH(DataType.String, 'businessError', true, false);
    this.translateHeadersAndColumns();
    this.fieldsInitialized = true;
  }

  canExpandRow(failure: StandingOrderFailure): boolean {
    return !!failure.unexpectedError;
  }

  getShowLines(text: string): number {
    return Math.min((text.match(/\n/g) || '').length + 1, 15);
  }
}
