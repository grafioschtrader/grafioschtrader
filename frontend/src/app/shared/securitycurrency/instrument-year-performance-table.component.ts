import { Component, computed, Injector, Input, OnInit } from '@angular/core';
import { TableConfigBase } from '../../lib/datashowbase/table.config.base';
import { FilterService } from '@openng/optimus-ui/api';
import { TranslateService, TranslateModule } from '@ngx-translate/core';
import { GlobalparameterService } from '../../lib/services/globalparameter.service';
import { UserSettingsService } from '../../lib/services/user.settings.service';
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
  @Input() values: LastYears[];
  @Input() mainCurrency: string;
  datakey = 'year';
  groupTitle = 'CALENDAR_YEAR_RETURN';
  titleRemark = 'ALL_EOD_AND_DIVIDEND';
  sortOrder = -1;
  /** A new columns reference re-renders the header and body templates of the OnPush p-table after translation. */
  readonly tableFields = computed(() => {
    this.fieldsRevision();
    return [...this.fields];
  });

  constructor(
    filterService: FilterService,
    translateService: TranslateService,
    gps: GlobalparameterService,
    usersettingsService: UserSettingsService,
    injector: Injector
  ) {
    super(filterService, usersettingsService, translateService, gps, injector);
  }

  ngOnInit(): void {
    this.addColumn(DataType.String, 'year', 'CALENDAR', true, false);
    this.addColumn(DataType.Numeric, 'performanceYear', 'PERFORMANCE', true, false, {
      templateName: 'greenRed',
      headerSuffix: '%'
    });
    this.addColumn(DataType.Numeric, 'performanceYearMC', 'PERFORMANCE', true, false, {
      templateName: 'greenRed',
      headerSuffix: (this.mainCurrency ? this.mainCurrency + ' ' : '') + '%'
    });
    this.prepareTableAndTranslate();
  }
}
