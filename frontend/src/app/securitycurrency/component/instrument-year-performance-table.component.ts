import {Component, Input, OnInit} from '@angular/core';
import {TableConfigBase} from '../../shared/datashowbase/table.config.base';
import {FilterService} from 'primeng/api';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {UserSettingsService} from '../../shared/service/user.settings.service';
import {LastYears} from '../../entities/view/instrument.statistics.result';
import {DataType} from '../../dynamic-form/models/data.type';

@Component({
  selector: 'instrument-year-performance-table',
  templateUrl: '../view/instrument.statistic.table.html'
})
export class InstrumentYearPerformanceTableComponent extends TableConfigBase implements OnInit {
  @Input() values: LastYears[];
  @Input() mainCurrency: string;
  datakey = 'year';
  groupTitle = 'CALENDAR_YEAR_RETURN';
  titleRemark = 'ALL_EOD_AND_DIVIDEND';
  sortOrder = -1;

  constructor(filterService: FilterService,
              translateService: TranslateService,
              gps: GlobalparameterService,
              usersettingsService: UserSettingsService) {
    super(filterService, usersettingsService, translateService, gps);
  }

  ngOnInit(): void {
    this.addColumn(DataType.String, 'year', 'CALENDAR', true, false);
    this.addColumn(DataType.Numeric, 'performanceYear', 'PERFORMANCE', true, false,
      {templateName: 'greenRed', headerSuffix: '%'});
    this.addColumn(DataType.Numeric, 'performanceYearMC', 'PERFORMANCE', true, false, {
      templateName: 'greenRed',
      headerSuffix: (this.mainCurrency ? this.mainCurrency + ' ' : '') + '%'
    });
    this.prepareTableAndTranslate();
  }

}
