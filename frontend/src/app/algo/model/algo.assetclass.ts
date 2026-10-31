import { AlgoAssetclassSecurity } from './algo.assetclass.security';
import { AlgoSecurity } from './algo.security';
import { Exclude, Type } from 'class-transformer';
import { Assetclass } from '../../entities/assetclass';
import { AlgoTopAssetSecurity } from './algo.top.asset.security';
import { AlgoTreeName } from '../../entities/view/algo.tree.name';

export class AlgoAssetclass extends AlgoAssetclassSecurity implements AlgoTreeName {
  idAlgoAssetclassParent?: number;

  name: string | null = null;
  securityDeviationPercentage: number | null = null;
  maxTradedSecuritiesPerAssetclass: number | null = null;

  assetclass: Assetclass | null = null;

  @Type(() => AlgoSecurity)
  algoSecurityList: AlgoSecurity[] = [];

  addedPercentage?: number;

  @Exclude()
  isCustomCategory(): boolean {
    return this.name != null;
  }

  @Exclude()
  getNameByLanguage(language: string): string | null {
    if (this.name != null) {
      return this.name;
    }
    if (!this.assetclass) return null;
    const category = this.assetclass.categoryType$;
    const subCategory = this.assetclass.subCategoryNLS.map[language];
    const instrument = this.assetclass.specialInvestmentInstrument$;
    if (category == null || subCategory == null || instrument == null) return null;
    return category + ', ' + subCategory + ', ' + instrument;
  }

  @Exclude()
  getChildList(): AlgoTopAssetSecurity[] {
    return this.algoSecurityList;
  }
}
