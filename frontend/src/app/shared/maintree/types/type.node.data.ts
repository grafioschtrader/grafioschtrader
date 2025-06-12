import {TreeNodeType} from './tree.node.type';

export class TypeNodeData {
  constructor(public treeNodeType: TreeNodeType,
              public route: string,
              public id: number,
              public parentObject: string,
              public entityObject?: string,
              public useQueryParams?: boolean) {
  }
}
