import {TreeNode} from 'primeng/api';
import {AlgoTop} from './algo.top';
import {TreeAlgoTopAssetSecurityBase} from './tree.algo.top.asset.security.base';
import {TreeAlgoAssetclass} from './tree.algo.assetclass';

export class TreeAlgoTop extends TreeAlgoTopAssetSecurityBase<AlgoTop> {

  constructor(data: AlgoTop) {
    super(data);
  }

  get children(): TreeNode[] {
    if (!this.treeNodes) {
      this.treeNodes = super.getStrategyNodes();
      this.data.algoAssetclassList.forEach(algoAssetclass => {
        this.treeNodes.push(new TreeAlgoAssetclass(algoAssetclass));
      });
    }
    return this.treeNodes;
  }

}
