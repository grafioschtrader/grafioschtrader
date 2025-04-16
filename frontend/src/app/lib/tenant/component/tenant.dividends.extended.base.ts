import {TableConfigBase} from '../../../shared/datashowbase/table.config.base';
import {FilterService} from 'primeng/api';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../../shared/service/globalparameter.service';
import {UserSettingsService} from '../../../shared/service/user.settings.service';
import {DataType} from '../../../dynamic-form/models/data.type';

export class TenantDividendsExtendedBase extends TableConfigBase {
  constructor(filterService: FilterService,
    usersettingsService: UserSettingsService,
    translateService: TranslateService,
    gps: GlobalparameterService) {
    super(filterService, usersettingsService, translateService, gps);
  }

  protected addGeneralColumns(currency: string) {
    this.addColumnFeqH(DataType.Numeric, 'autoPaidTax', true, false);
    this.addColumnFeqH(DataType.Numeric, 'autoPaidTaxMC', true, false,
      {headerSuffix: currency});
    this.addColumnFeqH(DataType.Numeric, 'taxableAmount', true, false);
    this.addColumnFeqH(DataType.Numeric, 'taxableAmountMC', true, false,
      {headerSuffix: currency});
    this.addColumnFeqH(DataType.Numeric, 'realReceivedDivInterestMC', true, false,
      {headerSuffix: currency});
  }

}
