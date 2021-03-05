import {Injectable} from '@angular/core';
import {SvgIconRegistryService} from 'angular-svg-icon';
import {SpecialInvestmentInstruments} from '../../shared/types/special.investment.instruments';
import {AssetclassType} from '../../shared/types/assetclass.type';
import {Security} from '../../entities/security';
import {AppSettings} from '../../shared/app.settings';

@Injectable()
export class ProductIconService {

  readonly icons = ['bo', 'c', 'cc', 'cb', 'd', 'cfd_c', 'cfd_i', 'd', 'eq', 'etf_c', 'etf_i', 'f', 'fr', 'fx', 'i', 'm'];

  constructor(private iconReg: SvgIconRegistryService) {
    this.icons.forEach(icon => this.iconReg.loadSvg(AppSettings.PATH_ASSET_ICONS + icon + '.svg', icon));
  }

  getIconForAssetClass(security: Security, isCryptocurrency: boolean): string {
    let icon = isCryptocurrency ? 'cc' : 'c';
    if (security) {
      const assetclass = security.assetClass;
      if (security.idLinkSecuritycurrency && SpecialInvestmentInstruments[assetclass.specialInvestmentInstrument]
        !== SpecialInvestmentInstruments.FOREX) {
        icon = 'd';
      } else {
        switch (SpecialInvestmentInstruments[assetclass.specialInvestmentInstrument]) {
          case SpecialInvestmentInstruments.DIRECT_INVESTMENT:
            icon = this.getDirectInvestmentIcon(assetclass.categoryType);
            break;
          case SpecialInvestmentInstruments.MUTUAL_FUND:
          case SpecialInvestmentInstruments.PENSION_FUNDS:
            icon = assetclass.categoryType === AssetclassType[AssetclassType.REAL_ESTATE] ? 'fr' : 'f';
            break;
          case SpecialInvestmentInstruments.NON_INVESTABLE_INDICES:
            icon = 'i';
            break;
          case SpecialInvestmentInstruments.CFD:
            icon = assetclass.categoryType === AssetclassType[AssetclassType.COMMODITIES] ? 'cfd_c' : 'cfd_i';
            break;
          case SpecialInvestmentInstruments.ETF:
            icon = assetclass.categoryType === AssetclassType[AssetclassType.COMMODITIES] ? 'etf_c' : 'etf_i';
            break;
          case SpecialInvestmentInstruments.FOREX:
            icon = 'fx';
            break;
        }
      }
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
    }
    return icon;
  }

}
