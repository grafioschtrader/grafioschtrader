import {Injectable} from '@angular/core';
import {SvgIconRegistryService} from 'angular-svg-icon';
import {SpecialInvestmentInstruments} from '../../shared/types/special.investment.instruments';
import {AssetclassType} from '../../shared/types/assetclass.type';
import {DistributionFrequency} from '../../shared/types/distribution.frequency';
import {Security} from '../../entities/security';
import {AppSettings} from '../../shared/app.settings';
import {Assetclass} from '../../entities/assetclass';
import {BaseSettings} from '../../lib/base.settings';

@Injectable()
export class ProductIconService {

  readonly icons = ['bo', 'c', 'cc', 'cb', 'co', 'd', 'cfd_c', 'cfd_i', 'd', 'dist', 'eq', 'etf_c', 'etf_crypto',
    'etf_i', 'f', 'fr', 'fx', 'i', 'ir', 'm'];

  constructor(private iconReg: SvgIconRegistryService) {
    this.icons.forEach(icon => this.iconReg.loadSvg(BaseSettings.PATH_ASSET_ICONS + icon + '.svg', icon));
  }

  getIconForInstrument(security: Security, isCryptocurrency: boolean): string {
    let icon = isCryptocurrency ? 'cc' : 'c';
    if (security) {
      const assetclass = security.assetClass;
      if (security.idLinkSecuritycurrency && SpecialInvestmentInstruments[assetclass.specialInvestmentInstrument]
        !== SpecialInvestmentInstruments.FOREX) {
        icon = 'd';
      } else {
        icon = this.getIconForAssetclass(assetclass, icon);
      }
    }
    return icon;
  }

  /**
   * Returns the distribution icon when the security pays out interest or dividends, otherwise null. Null leaves the
   * cell empty, which is also the outcome for an accumulating security and for a currency pair, since the latter
   * carries no distribution frequency at all.
   *
   * @param security The security to evaluate, may be null or a currency pair without a distribution frequency
   * @returns The name of the registered distribution icon or null when nothing is paid out
   */
  getDistributionIcon(security: Security): string {
    return security?.distributionFrequency
      && DistributionFrequency[security.distributionFrequency] !== DistributionFrequency.DF_NONE ? 'dist' : null;
  }

  getIconForAssetclass(assetclass: Assetclass, icon: string) {
    switch (SpecialInvestmentInstruments[assetclass.specialInvestmentInstrument]) {
      case SpecialInvestmentInstruments.DIRECT_INVESTMENT:
        icon = this.getDirectInvestmentIcon(assetclass.categoryType);
        break;
      case SpecialInvestmentInstruments.MUTUAL_FUND:
      case SpecialInvestmentInstruments.PENSION_FUNDS:
        icon = assetclass.categoryType === AssetclassType[AssetclassType.REAL_ESTATE] ? 'fr' : 'f';
        break;
      case SpecialInvestmentInstruments.ISSUER_RISK_PRODUCT:
        icon = 'ir';
        break;
      case SpecialInvestmentInstruments.NON_INVESTABLE_INDICES:
        icon = 'i';
        break;
      case SpecialInvestmentInstruments.CFD:
        icon = assetclass.categoryType === AssetclassType[AssetclassType.COMMODITIES] ? 'cfd_c' : 'cfd_i';
        break;
      case SpecialInvestmentInstruments.ETF:
        icon = assetclass.categoryType === AssetclassType[AssetclassType.COMMODITIES] ? 'etf_c' :
          assetclass.categoryType === AssetclassType[AssetclassType.CURRENCY_PAIR] ? 'etf_crypto' : 'etf_i';
        break;
      case SpecialInvestmentInstruments.FOREX:
        icon = 'fx';
        break;
    }
    return icon;
  }

  private getDirectInvestmentIcon(assetclassType: AssetclassType | string): string {
    let icon: string;
    switch (AssetclassType[assetclassType]) {
      case AssetclassType.EQUITIES:
        icon = 'eq';
        break;
      case AssetclassType.MONEY_MARKET:
        icon = 'm';
        break;
      case AssetclassType.CONVERTIBLE_BOND:
        icon = 'cb';
        break;
      case AssetclassType.FIXED_INCOME:
        icon = 'bo';
        break;
      case AssetclassType.COMMODITIES:
        icon = 'co';
        break;
    }
    return icon;
  }

}
