import {Inject, Injectable, Optional} from '@angular/core';
import {combineLatest, Observable, of} from 'rxjs';
import {map, tap} from 'rxjs/operators';
import {MenuItem, TreeNode} from 'primeng/api';
import {MAIN_TREE_CONTRIBUTOR, MainTreeContributor} from './main-tree-contributor.interface';
import {ProcessedActionData} from '../../types/processed.action.data';

/**
 * Manager service that coordinates all MainTreeContributor instances.
 * This service is injected into MainTreeComponent to abstract away
 * the details of working with multiple contributors.
 */
@Injectable()
export class MainTreeContributorManager {

  private contributors: MainTreeContributor[];

  constructor(
    @Optional() @Inject(MAIN_TREE_CONTRIBUTOR) contributors: MainTreeContributor[]
  ) {
    this.contributors = contributors || [];
    // Filter out disabled contributors and sort by tree order
    this.contributors = this.contributors
      .filter(c => c.isEnabled())
      .sort((a, b) => a.getTreeOrder() - b.getTreeOrder());
  }

  /**
   * Builds the complete tree structure by collecting root nodes from all contributors.
   *
   * @returns Observable of the complete TreeNode array
   */
  buildTree(): Observable<TreeNode[]> {
    const rootNodesObservables: Observable<TreeNode[]>[] = this.contributors
      .map(contributor => contributor.getRootNodes())
      .filter(obs => obs !== null);

    if (rootNodesObservables.length === 0) {
      return of([]);
    }

    // Combine all observables and flatten the results
    return combineLatest(rootNodesObservables).pipe(
      map(nodeArrays => {
        const allNodes: TreeNode[] = [];
        nodeArrays.forEach(nodes => {
          if (nodes && nodes.length > 0) {
            allNodes.push(...nodes);
          }
        });
        return allNodes;
      })
    );
  }

  /**
   * Refreshes nodes for contributors that should respond to a data change.
   *
   * @param processedActionData The data change event
   * @param portfolioTrees The current tree structure
   * @returns Observable that completes when all refreshes are done
   */
  refreshNodesForDataChange(processedActionData: ProcessedActionData, portfolioTrees: TreeNode[]): Observable<void> {
    const refreshObservables: Observable<void>[] = [];

    this.contributors.forEach(contributor => {
      if (contributor.shouldRefreshOnDataChange(processedActionData)) {
        // Find the root node managed by this contributor
        const rootNode = this.findRootNodeForContributor(contributor, portfolioTrees);
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
   * Gets context menu items for a tree node by delegating to the appropriate contributor.
   *
   * @param treeNode The node for which to get menu items
   * @returns MenuItem array or null if no menu available
   */
  getContextMenuItems(treeNode: TreeNode): MenuItem[] | null {
    const parentNodeData = treeNode.data?.parentObject ? JSON.parse(treeNode.data.parentObject) : null;
    const selectedNodeData = treeNode.data?.entityObject ? JSON.parse(treeNode.data.entityObject) : null;
    for (const contributor of this.contributors) {
      const menuItems = contributor.getContextMenuItems(treeNode, parentNodeData, selectedNodeData);
      if (menuItems && menuItems.length > 0) {
        return menuItems;
      }
    }
    return null;
  }

  /**
   * Delegates delete operation to the appropriate contributor.
   *
   * @param treeNode The node to delete
   * @param id The entity ID
   * @returns Observable that completes when deletion is done, or null
   */
  handleDelete(treeNode: TreeNode, id: number): Observable<any> | null {
    for (const contributor of this.contributors) {
      if (contributor.handleDelete) {
        const deleteObs = contributor.handleDelete(treeNode, id);
        if (deleteObs) {
          return deleteObs;
        }
      }
    }
    return null;
  }

  /**
   * Finds the root node that should be managed by a specific contributor.
   * This is a simple implementation that can be enhanced if needed.
   *
   * @param contributor The contributor to find the node for
   * @param portfolioTrees The tree structure
   * @returns The root TreeNode or null
   */
  private findRootNodeForContributor(contributor: MainTreeContributor, portfolioTrees: TreeNode[]): TreeNode | null {
    // For now, we'll need to enhance this based on the actual implementation
    // This is a placeholder that assumes the tree order matches array index
    const index = this.contributors.indexOf(contributor);
    return index >= 0 && index < portfolioTrees.length ? portfolioTrees[index] : null;
  }

  /**
   * Returns the number of contributors (useful for array sizing).
   */
  getContributorCount(): number {
    return this.contributors.length;
  }
}
