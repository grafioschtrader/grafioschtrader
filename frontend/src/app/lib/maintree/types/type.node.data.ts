/**
 * Data structure for tree nodes in the main navigation tree.
 * This is a library class and should not depend on application-specific types.
 *
 * The treeNodeType is a string identifier that can be any value defined by the application.
 * Applications typically define these as constants or enums (e.g., TreeNodeType.Portfolio).
 */
export class TypeNodeData {
  constructor(public treeNodeType: string,
              public route: string,
              public id: number,
              public parentObject: string,
              public entityObject?: string,
              public useQueryParams?: boolean) {
  }
}


