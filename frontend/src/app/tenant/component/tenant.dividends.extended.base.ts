import {Injector} from '@angular/core';
import {TableConfigBase} from '../../lib/datashowbase/table.config.base';
import {FilterService} from 'primeng/api';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {UserSettingsService} from '../../lib/services/user.settings.service';
import {DataType} from '../../lib/dynamic-form/models/data.type';

export class TenantDividendsExtendedBase extends TableConfigBase {
  constructor(filterService: FilterService,
    usersettingsService: UserSettingsService,
    translateService: TranslateService,
    gps: GlobalparameterService,
    injector: Injector) {
    super(filterService, usersettingsService, translateService, gps, injector);
  }

  /**
   * Adds the tax and dividend/interest columns shared by the security and cash account extended views.
   *
   * @param currency Tenant main currency, used as header suffix and fixed precision currency of the MC columns
   * @param currencyPrecisionField Path to the row's own currency code (e.g. 'security.currency' or
   *                               'cashaccount.currency') for the non-MC amount columns
   */
  protected addGeneralColumns(currency: string, currencyPrecisionField: string) {
    this.addColumnFeqH(DataType.Numeric, 'autoPaidTax', true, false, {currencyPrecisionField});
    this.addColumnFeqH(DataType.Numeric, 'autoPaidTaxMC', true, false,
      {headerSuffix: currency, fixedCurrency: currency});
    this.addColumnFeqH(DataType.Numeric, 'taxableAmount', true, false, {currencyPrecisionField});
    this.addColumnFeqH(DataType.Numeric, 'taxableAmountMC', true, false,
      {headerSuffix: currency, fixedCurrency: currency});
    this.addColumnFeqH(DataType.Numeric, 'realReceivedDivInterestMC', true, false,
      {headerSuffix: currency, fixedCurrency: currency});
  }

  protected addIctaxColumns(): void {
    this.addColumn(DataType.Numeric, 'ictaxTotalPaymentValueChf', 'ICTAX_TOTAL_PAYMENT_CHF', false, true, {width: 80});
    this.addColumn(DataType.Numeric, 'ictaxTotalTaxValueChf', 'ICTAX_TOTAL_TAX_VALUE', false, true, {width: 80});
  }

}
