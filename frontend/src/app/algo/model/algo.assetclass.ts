import {AlgoAssetclassSecurity} from './algo.assetclass.security';
import {AlgoSecurity} from './algo.security';
import {Exclude, Type} from 'class-transformer';
import {Assetclass} from '../../entities/assetclass';
import {AlgoTopAssetSecurity} from './algo.top.asset.security';
import {AlgoTreeName} from '../../entities/view/algo.tree.name';

export class AlgoAssetclass extends AlgoAssetclassSecurity implements AlgoTreeName {

  idAlgoAssetclassParent: number;

  assetclass: Assetclass = null;

  @Type(() => AlgoSecurity)
  algoSecurityList: AlgoSecurity[];

  addedPercentage: number;

  @Exclude()
  getNameByLanguage(language: string): string {
    return this.assetclass['categoryType$'] + ', ' + this.assetclass.subCategoryNLS.map[language] + ', '
      + this.assetclass[`specialInvestmentInstrument$`];
  }

  @Exclude()
  getChildList(): AlgoTopAssetSecurity[] {
    return this.algoSecurityList;
  }

}
