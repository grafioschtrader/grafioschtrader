import {Component, Input, OnInit} from '@angular/core';
import {AnnualisedYears} from '../../entities/view/instrument.statistics.result';
import {FilterService} from 'primeng/api';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {UserSettingsService} from '../../shared/service/user.settings.service';
import {DataType} from '../../dynamic-form/models/data.type';
import {TableConfigBase} from '../../shared/datashowbase/table.config.base';

/**
 * Shows the annualised return for an instrument.
 */
@Component({
  selector: 'instrument-annualised-return-table',
  templateUrl: '../view/instrument.statistic.table.html'
})
export class InstrumentAnnualisedReturnComponent extends TableConfigBase implements OnInit {
  @Input() values: AnnualisedYears[];
  @Input() mainCurrency: string;
  datakey = 'numberOfYears';
  groupTitle = 'ANNUALISED_RETURN';
  titleRemark = 'WITHOUT_CURRENT_YEAR';
  sortOrder = 1;

  constructor(filterService: FilterService,
              translateService: TranslateService,
              gps: GlobalparameterService,
              usersettingsService: UserSettingsService) {
    super(filterService, usersettingsService, translateService, gps);
  }

  ngOnInit(): void {
    this.addColumnFeqH(DataType.String, 'numberOfYears', true, false);
    this.addColumn(DataType.Numeric, 'performanceAnnualised', 'PERFORMANCE', true, false,
      {templateName: 'greenRed', headerSuffix: '%'});
    this.addColumn(DataType.Numeric, 'performanceAnnualisedMC', 'PERFORMANCE', true, false,
      {templateName: 'greenRed', headerSuffix: (this.mainCurrency ? this.mainCurrency + ' ' : '') + '%'});
    this.prepareTableAndTranslate();
  }
}
