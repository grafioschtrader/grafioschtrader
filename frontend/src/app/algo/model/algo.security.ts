import { Security } from '../../entities/security';
import { AlgoAssetclassSecurity } from './algo.assetclass.security';
import { AlgoTopAssetSecurity } from './algo.top.asset.security';
import { Exclude } from 'class-transformer';
import { AlgoTreeName } from '../../entities/view/algo.tree.name';
import { AlgoStrategyImplementationType } from '../../shared/types/algo.strategy.implementation.type';

export class AlgoSecurity extends AlgoAssetclassSecurity implements AlgoTreeName {
  idAlgoSecurityParent?: number;
  security: Security | null = null;
  /** Units the tenant holds today; only delivered for the assigned monitoring hierarchy, null without a position. */
  holdings: number | null = null;

  @Exclude()
  getNameByLanguage(language: string): string | null {
    return this.security ? this.security.name + ', ' + this.security.currency : null;
  }

  @Exclude()
  getChildList(): AlgoTopAssetSecurity[] | null {
    return null;
  }
}

export interface AlgoSecurityStrategyImplType {
  algoSecurity: AlgoSecurity;
  possibleStrategyImplSet: AlgoStrategyImplementationType[];
  wasCreated: boolean;
}
