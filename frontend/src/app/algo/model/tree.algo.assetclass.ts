import {TreeNode} from 'primeng/api';
import {AlgoAssetclass} from './algo.assetclass';
import {TreeAlgoTopAssetSecurityBase} from './tree.algo.top.asset.security.base';
import {TreeAlgoSecurity} from './tree.algo.security';

export class TreeAlgoAssetclass extends TreeAlgoTopAssetSecurityBase<AlgoAssetclass> {

  constructor(data: AlgoAssetclass) {
    super(data);
  }

  get children(): TreeNode[] {
    if (!this.treeNodes) {
      this.treeNodes = super.getStrategyNodes();
      this.data.algoSecurityList.forEach(algoSecurity => {
        this.treeNodes.push(new TreeAlgoSecurity(algoSecurity));
      });
    }
    return this.treeNodes;
  }

}
