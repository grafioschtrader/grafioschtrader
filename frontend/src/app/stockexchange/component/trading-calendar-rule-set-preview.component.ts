import { Component, Input, OnChanges, SimpleChanges, ChangeDetectionStrategy } from '@angular/core';
import { TranslateModule, TranslateService } from '@ngx-translate/core';

import { ConfigurableTableComponent } from '../../lib/datashowbase/configurable-table.component';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { GlobalparameterService } from '../../lib/services/globalparameter.service';
import { ShowRecordConfigBase } from '../../lib/datashowbase/show.record.config.base';

/** One calculated closure with the name of the rule that produced it. */
export interface RuleSetClosure {
  closureDate: string;
  ruleName: string;
}

/**
 * Shows the closures a rule set produces for one year. The rule name makes it possible to tell which rule caused a
 * date, which is the only practical way to check an unfamiliar rule before saving it. Two rules resolving to the same
 * day are shown with both names separated by a slash.
 */
@Component({
  selector: 'trading-calendar-rule-set-preview',
  template: `
    <configurable-table
      [data]="closureList"
      [fields]="fields"
      [scrollable]="true"
      [scrollHeight]="'240px'"
      [stripedRows]="true"
      [showGridlines]="true"
      [valueGetterFn]="getValueByPath.bind(this)"
      [baseLocale]="baseLocale">
    </configurable-table>
  `,
  standalone: true,
  changeDetection: ChangeDetectionStrategy.Eager,
  imports: [ConfigurableTableComponent, TranslateModule]
})
export class TradingCalendarRuleSetPreviewComponent extends ShowRecordConfigBase implements OnChanges {
  /** Closures as delivered by the backend, keyed by date in ascending order. */
  @Input() closures: { [date: string]: string };

  closureList: RuleSetClosure[] = [];

  constructor(translateService: TranslateService, gps: GlobalparameterService) {
    super(translateService, gps);
    this.addColumn(DataType.DateString, 'closureDate', 'CLOSURE_DATE', true, false);
    this.addColumn(DataType.String, 'ruleName', 'RULE_NAME', true, false);
    this.translateHeadersAndColumns();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['closures']) {
      this.closureList = Object.entries(this.closures ?? {}).map(([closureDate, ruleName]) => ({
        closureDate,
        ruleName
      }));
    }
  }
}
