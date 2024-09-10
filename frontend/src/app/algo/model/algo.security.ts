import {Security} from '../../entities/security';
import {AlgoAssetclassSecurity} from './algo.assetclass.security';
import {AlgoTopAssetSecurity} from './algo.top.asset.security';
import {Exclude} from 'class-transformer';
import {AlgoTreeName} from '../../entities/view/algo.tree.name';
import {AlgoStrategyImplementationType} from '../../shared/types/algo.strategy.implementation.type';


export class AlgoSecurity extends AlgoAssetclassSecurity implements AlgoTreeName {
  idAlgoSecurityParent: number;
  security: Security = null;

  @Exclude()
  getNameByLanguage(language: string): string {
    return this.security.name + ', ' + this.security.currency;
  }

  @Exclude()
  getChildList(): AlgoTopAssetSecurity[] {
    return null;
  }
}

export class AlgoSecurityStrategyImplType {
  algoSecurity: AlgoSecurity;
  possibleStrategyImplSet: AlgoStrategyImplementationType[];
  wasCreated: boolean;
}
