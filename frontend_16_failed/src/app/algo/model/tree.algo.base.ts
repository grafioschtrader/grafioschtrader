import {TreeNode} from 'primeng/api';

export abstract class TreeAlgoBase implements TreeNode {

  get children(): TreeNode[] {
    return [];
  }
}
