import {TreeNode} from 'primeng/api';
import {AlgoTopAssetSecurity} from './algo.top.asset.security';
import {TreeAlgoStrategy} from './tree.algo.strategy';
import {TreeAlgoBase} from './tree.algo.base';

export abstract class TreeAlgoTopAssetSecurityBase<T extends AlgoTopAssetSecurity> extends TreeAlgoBase {
  /**
   * Only for tree support
   */
  public expanded = true;
  treeNodes: TreeNode[];


  protected constructor(public data: T) {
    super();
  }

  public getId(): number {
    return this.data.idAlgoAssetclassSecurity;
  }

  getStrategyNodes(): TreeNode[] {
    const treeNodes: TreeNode[] = [];
    this.data.algoStrategyList.forEach(algoStrategy => {
      treeNodes.push(new TreeAlgoStrategy(algoStrategy));
    });
    return treeNodes;
  }


}
