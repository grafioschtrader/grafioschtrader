import {Injectable, Inject, Optional} from '@angular/core';
import {Observable, combineLatest, of} from 'rxjs';
import {map} from 'rxjs/operators';
import {MenuItem, TreeNode} from 'primeng/api';
import {MAIN_TREE_CONTRIBUTOR, MainTreeContributor, MainTreeCallbacks} from '../contributor/main-tree-contributor.interface';
import {ProcessedActionData} from '../../types/processed.action.data';

/**
 * Main service for managing the navigation tree.
 * This service acts as a facade between the MainTreeComponent and all contributors,
 * eliminating the need for the component to know about specific contributor implementations.
 */
@Injectable()
export class MainTreeService {

  private contributors: MainTreeContributor[];
  private portfolioTrees: TreeNode[] = [];

  // Component callbacks - set by the component
  private componentCallbacks: {
    handleEdit?: (componentType: any, parentObject: any, data: any, titleKey: string) => Observable<any>;
    handleTenantEdit?: (data: any, onlyCurrency: boolean) => Observable<any>;
    navigateToNode?: (data: any) => void;
    refreshTree?: () => void;
  } = {};

  constructor(@Optional() @Inject(MAIN_TREE_CONTRIBUTOR) contributors: MainTreeContributor[]) {
    this.contributors = contributors || [];
    // Filter out disabled contributors and sort by tree order
    this.contributors = this.contributors
      .filter(c => c.isEnabled())
      .sort((a, b) => a.getTreeOrder() - b.getTreeOrder());
  }

  /**
   * Initializes callbacks for all contributors so they can interact with the tree.
   * This is called after component callbacks are set.
   */
  private initializeContributorCallbacks(): void {
    const callbacks: MainTreeCallbacks = {
      handleEdit: (componentType: any, parentObject: any, data: any, titleKey: string) =>
        this.componentCallbacks.handleEdit?.(componentType, parentObject, data, titleKey) || of(null),
      handleTenantEdit: (data: any, onlyCurrency: boolean) =>
        this.componentCallbacks.handleTenantEdit?.(data, onlyCurrency) || of(null),
      navigateToNode: (data: any) => this.componentCallbacks.navigateToNode?.(data),
      refreshTree: () => this.componentCallbacks.refreshTree?.()
    };

    this.contributors.forEach(contributor => {
      contributor.setCallbacks(callbacks);
    });
  }

  /**
   * Sets the component-level callbacks.
   * This is called by MainTreeComponent during initialization.
   */
  setComponentCallbacks(callbacks: {
    handleEdit: (componentType: any, parentObject: any, data: any, titleKey: string) => Observable<any>;
    handleTenantEdit: (data: any, onlyCurrency: boolean) => Observable<any>;
    navigateToNode: (data: any) => void;
    refreshTree: () => void;
  }): void {
    this.componentCallbacks = callbacks;
    // Initialize contributor callbacks now that component callbacks are set
    this.initializeContributorCallbacks();
  }

  /**
   * Builds the complete tree structure by collecting root nodes from all contributors.
   */
  buildTree(): Observable<TreeNode[]> {
    const rootNodesObservables: Observable<TreeNode[]>[] = this.contributors
      .map(contributor => contributor.getRootNodes())
      .filter(obs => obs !== null);

    if (rootNodesObservables.length === 0) {
      return of([]);
    }

    return combineLatest(rootNodesObservables).pipe(
      map(nodeArrays => {
        const allNodes: TreeNode[] = [];
        nodeArrays.forEach(nodes => {
          if (nodes && nodes.length > 0) {
            allNodes.push(...nodes);
          }
        });
        this.portfolioTrees = allNodes;
        return allNodes;
      })
    );
  }

  /**
   * Refreshes all contributors' nodes.
   */
  refreshAllNodes(): Observable<void> {
    const refreshObservables: Observable<void>[] = [];
    this.contributors.forEach((contributor, index) => {
      if (index < this.portfolioTrees.length) {
        const rootNode = this.portfolioTrees[index];
        if (rootNode) {
          const refreshObs = contributor.refreshNodes(rootNode);
          if (refreshObs) {
            refreshObservables.push(refreshObs);
          }
        }
      }
    });

    if (refreshObservables.length === 0) {
      return of(void 0);
    }

    return combineLatest(refreshObservables).pipe(
      map(() => void 0)
    );
  }

  /**
   * Refreshes nodes for contributors that should respond to a data change.
   */
  refreshNodesForDataChange(processedActionData: ProcessedActionData): Observable<void> {
    const refreshObservables: Observable<void>[] = [];

    this.contributors.forEach((contributor, index) => {
      if (contributor.shouldRefreshOnDataChange(processedActionData)) {
        if (index < this.portfolioTrees.length) {
          const rootNode = this.portfolioTrees[index];
          if (rootNode) {
            const refreshObs = contributor.refreshNodes(rootNode);
            if (refreshObs) {
              refreshObservables.push(refreshObs);
            }
          }
        }
      }
    });

    if (refreshObservables.length === 0) {
      return of(void 0);
    }

    return combineLatest(refreshObservables).pipe(
      map(() => void 0)
    );
  }

  /**
   * Gets context menu items for a tree node by delegating to the appropriate contributor.
   */
  getContextMenuItems(treeNode: TreeNode): MenuItem[] | null {
    for (const contributor of this.contributors) {
      const parentNodeData = treeNode.data?.parentObject ? JSON.parse(treeNode.data.parentObject) : null;
      const selectedNodeData = treeNode.data?.entityObject ? JSON.parse(treeNode.data.entityObject) : null;

      const menuItems = contributor.getContextMenuItems(treeNode, parentNodeData, selectedNodeData);
      if (menuItems && menuItems.length > 0) {
        return menuItems;
      }
    }

    return null;
  }

  /**
   * Returns the number of contributors (useful for array sizing).
   */
  getContributorCount(): number {
    return this.contributors.length;
  }

  /**
   * Returns the current tree structure.
   */
  getPortfolioTrees(): TreeNode[] {
    return this.portfolioTrees;
  }

  /**
   * Checks if any contributor can handle a drop operation on the given node.
   * This is called during dragover events.
   */
  canDrop(targetNode: TreeNode, dragData: string): boolean {
    for (const contributor of this.contributors) {
      if (contributor.canDrop?.(targetNode, dragData)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Delegates a drop operation to the appropriate contributor.
   * This is called when an item is dropped on a tree node.
   */
  handleDrop(targetNode: TreeNode, dragData: string, sourceLabel?: string): void {
    for (const contributor of this.contributors) {
      if (contributor.canDrop?.(targetNode, dragData)) {
        contributor.handleDrop?.(targetNode, dragData, sourceLabel);
        break;
      }
    }
  }
}
