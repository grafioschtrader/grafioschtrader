import {Security} from '../../entities/security';
import {TableConfigBase} from '../../shared/datashowbase/table.config.base';
import {TranslateService} from '@ngx-translate/core';
import {SpecialInvestmentInstruments} from '../../shared/types/special.investment.instruments';
import {SecurityaccountGroupBaseDynamic} from './securityaccount.group.base.dynamic';

/**
 * Group by financial instrument of asset class like ETF, direct investment.
 */
export class SecurityaccountAssetclassSpecInvestGroup extends SecurityaccountGroupBaseDynamic<SpecialInvestmentInstruments> {

  constructor(translateService: TranslateService, datatableConfigBase: TableConfigBase) {
    super(translateService, datatableConfigBase, 'specialinvestmentinstrument',
      'security.assetClass.specialInvestmentInstrument', 'GROUP_BY_FINANCIAL_INSTRUMENT');
  }

  public getGroupValue(security: Security) {
    return security.assetClass.specialInvestmentInstrument;
  }

  protected getGroupFieldAsString(enumType: SpecialInvestmentInstruments): string {
    return SpecialInvestmentInstruments[SpecialInvestmentInstruments[enumType]];
  }


}
