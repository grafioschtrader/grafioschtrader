import { Component, inject } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { SplitPeriodTableBase } from './split.period.table.base';
import { HistoryquotePeriod } from '../../entities/historyquote.period';
import { HistoryquotePeriodService } from '../../securitycurrency/service/historyquote.period.service';

import { TableModule } from '@openng/optimus-ui/table';
import { ButtonModule } from '@openng/optimus-ui/button';
import { AngularSvgIconModule } from 'angular-svg-icon';

/**
 * The split and history quote period table component.
 */
@Component({
  selector: 'security-historyquote-period-edit-table',
  templateUrl: './view/split.period.table.html',
  imports: [TranslateModule, TableModule, ButtonModule, AngularSvgIconModule]
})
export class SecurityHistoryquotePeriodEditTableComponent extends SplitPeriodTableBase<HistoryquotePeriod> {
  // readonly dataSortKey = 'fromDate';

  constructor() {
    super('fromDate', 'SECURITY_PERIODS_FROM_MAX', HistoryquotePeriod, inject(HistoryquotePeriodService));
    this.addColumn(DataType.DateString, 'fromDate', 'FROM_DATE_NEW_PRICE', true, false);
    this.addColumn(DataType.Numeric, 'price', 'CLOSE', true, false);
    this.prepareTableAndTranslate();
  }
}
