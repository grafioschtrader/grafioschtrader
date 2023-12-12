import {Security} from '../../entities/security';
import {AlgoAssetclassSecurity} from './algo.assetclass.security';
import {AlgoTopAssetSecurity} from './algo.top.asset.security';
import {Exclude} from 'class-transformer';
import {AlgoTreeName} from '../../entities/view/algo.tree.name';


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
