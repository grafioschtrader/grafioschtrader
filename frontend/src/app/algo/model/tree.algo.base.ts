import {TreeNode} from 'primeng/api';
import {AlgoSecurity} from './algo.security';
import {AlgoAssetclass} from './algo.assetclass';
import {AlgoStrategy} from './algo.strategy';
import {AlgoTopAssetSecurity} from './algo.top.asset.security';
import {AlgoTop} from './algo.top';

export abstract class TreeAlgoBase implements TreeNode {
  get children(): TreeNode[] {
    return [];
  }
}

export class TreeAlgoStrategy extends TreeAlgoBase {
  constructor(public algoStrategy: AlgoStrategy) {
    super();
  }

  get data(): any {
    return this.algoStrategy;
  }

  get expanded(): boolean {
    return false;
  }

}

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

export class TreeAlgoTop extends TreeAlgoTopAssetSecurityBase<AlgoTop> {

  constructor(data: AlgoTop) {
    super(data);
  }

  override get children(): TreeNode[] {
    if (!this.treeNodes) {
      this.treeNodes = super.getStrategyNodes();
      this.data.algoAssetclassList.forEach(algoAssetclass => {
        this.treeNodes.push(new TreeAlgoAssetclass(algoAssetclass));
      });
    }
    return this.treeNodes;
  }

}

export class TreeAlgoSecurity extends TreeAlgoTopAssetSecurityBase<AlgoSecurity> {

  constructor(data: AlgoSecurity) {
    super(data);
  }

  override get children(): TreeNode[] {
    if (!this.treeNodes) {
      this.treeNodes = super.getStrategyNodes();
    }
    return this.treeNodes;
  }

}

export class TreeAlgoAssetclass extends TreeAlgoTopAssetSecurityBase<AlgoAssetclass> {

  constructor(data: AlgoAssetclass) {
    super(data);
  }

  override get children(): TreeNode[] {
    if (!this.treeNodes) {
      this.treeNodes = super.getStrategyNodes();
      this.data.algoSecurityList.forEach(algoSecurity => {
        this.treeNodes.push(new TreeAlgoSecurity(algoSecurity));
      });
    }
    return this.treeNodes;
  }

}
