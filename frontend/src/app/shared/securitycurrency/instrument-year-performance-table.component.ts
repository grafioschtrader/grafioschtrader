import { Component, computed, OnInit, input } from '@angular/core';
import { TableConfigBase } from '../../lib/datashowbase/table.config.base';
import { TranslateModule } from '@ngx-translate/core';
import { LastYears } from '../../entities/view/instrument.statistics.result';
import { DataType } from '../../lib/dynamic-form/models/data.type';

import { TableModule } from '@openng/optimus-ui/table';
import { TooltipModule } from '@openng/optimus-ui/tooltip';

@Component({
  selector: 'instrument-year-performance-table',
  templateUrl: './view/instrument.statistic.table.html',
  imports: [TranslateModule, TableModule, TooltipModule]
})
export class InstrumentYearPerformanceTableComponent extends TableConfigBase implements OnInit {
  readonly values = input.required<LastYears[]>();
  readonly mainCurrency = input<string>();
  datakey = 'year';
  groupTitle = 'CALENDAR_YEAR_RETURN';
  titleRemark = 'ALL_EOD_AND_DIVIDEND';
  sortOrder = -1;
  /** A new columns reference re-renders the header and body templates of the OnPush p-table after translation. */
  readonly tableFields = computed(() => {
    this.fieldsRevision();
    return [...this.fields];
  });

  constructor() {
    super();
  }

  ngOnInit(): void {
    this.addColumn(DataType.String, 'year', 'CALENDAR', true, false);
    this.addColumn(DataType.Numeric, 'performanceYear', 'PERFORMANCE', true, false, {
      templateName: 'greenRed',
      headerSuffix: '%'
    });
    const mainCurrency = this.mainCurrency();
    this.addColumn(DataType.Numeric, 'performanceYearMC', 'PERFORMANCE', true, false, {
      templateName: 'greenRed',
      headerSuffix: (mainCurrency ? mainCurrency + ' ' : '') + '%'
    });
    this.prepareTableAndTranslate();
  }
}
