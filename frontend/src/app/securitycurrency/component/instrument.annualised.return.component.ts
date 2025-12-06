import {Component, Input, OnInit} from '@angular/core';
import {AnnualisedYears} from '../../entities/view/instrument.statistics.result';
import {FilterService} from 'primeng/api';
import {TranslateService, TranslateModule} from '@ngx-translate/core';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {UserSettingsService} from '../../lib/services/user.settings.service';
import {DataType} from '../../lib/dynamic-form/models/data.type';
import {TableConfigBase} from '../../lib/datashowbase/table.config.base';
import {CommonModule} from '@angular/common';
import {TableModule} from 'primeng/table';
import {TooltipModule} from 'primeng/tooltip';

/**
 * Shows the annualised return for an instrument.
 */
@Component({
    selector: 'instrument-annualised-return-table',
    templateUrl: '../view/instrument.statistic.table.html',
    standalone: true,
    imports: [CommonModule, TranslateModule, TableModule, TooltipModule]
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
