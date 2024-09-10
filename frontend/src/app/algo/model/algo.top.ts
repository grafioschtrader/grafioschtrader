import {AlgoTopAssetSecurity} from './algo.top.asset.security';
import {AlgoAssetclass} from './algo.assetclass';
import {Exclude, Type} from 'class-transformer';
import {AlgoTreeName} from '../../entities/view/algo.tree.name';
import {RuleStrategyType} from '../../shared/types/rule.strategy.type';


export class AlgoTop extends AlgoTopAssetSecurity implements AlgoTreeName {
  name: string = null;
  @Type(() => AlgoAssetclass)
  algoAssetclassList: AlgoAssetclass[];
  ruleStrategy: RuleStrategyType | string;
  idWatchlist: number = null;
  activatable: boolean;
  addedPercentage: number;

  @Exclude()
  getNameByLanguage(language: string): string {
    return this.name;
  }

  @Exclude()
  getChildList(): AlgoTopAssetSecurity[] {
    return this.algoAssetclassList;
  }
}

export enum AlgoLevelType {
  TOP_LEVEL = "T",
  ASSET_CLASS_LEVEL = "A",
  SECURITY_LEVEL = "S"
}
