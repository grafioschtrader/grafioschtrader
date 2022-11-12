import {TreeNode} from 'primeng/api';
import {AlgoSecurity} from './algo.security';
import {TreeAlgoTopAssetSecurityBase} from './tree.algo.top.asset.security.base';

export class TreeAlgoSecurity extends TreeAlgoTopAssetSecurityBase<AlgoSecurity> {

  constructor(data: AlgoSecurity) {
    super(data);
  }

  get children(): TreeNode[] {
    if (!this.treeNodes) {
      this.treeNodes = super.getStrategyNodes();
    }
    return this.treeNodes;
  }

}
