import {InjectionToken, Type} from '@angular/core';
import {Observable} from 'rxjs';
import {MenuItem, TreeNode} from 'primeng/api';
import {ProcessedActionData} from '../../types/processed.action.data';

/**
 * Callbacks that contributors can use to interact with the main tree.
 * These are provided by the MainTreeService.
 */
export interface MainTreeCallbacks {
  /**
   * Opens an edit dialog for an entity.
   */
  handleEdit: (componentType: Type<any>, parentObject: any, data: any, titleKey: string) => Observable<any>;

  /**
   * Opens the tenant edit dialog.
   */
  handleTenantEdit: (data: any, onlyCurrency: boolean) => Observable<any>;

  /**
   * Navigates to a specific tree node.
   */
  navigateToNode: (data: any) => void;

  /**
   * Triggers a full tree refresh.
   */
  refreshTree: () => void;
}

/**
 * Interface defining the contract for Main Tree contributors.
 * Each feature module can implement this to contribute nodes to the navigation tree.
 */
export abstract class MainTreeContributor {

  protected callbacks?: MainTreeCallbacks;

  /**
   * Sets the callbacks that this contributor can use to interact with the main tree.
   * This is called by the MainTreeService during initialization.
   */
  setCallbacks(callbacks: MainTreeCallbacks): void {
    this.callbacks = callbacks;
  }

  /**
   * Returns the root tree nodes that this contributor provides.
   * This is called during initial tree setup.
   *
   * @returns Observable of TreeNode array, or null if this contributor doesn't provide root nodes
   */
  abstract getRootNodes(): Observable<TreeNode[]> | null;

  /**
   * Returns the index position where this contributor's nodes should be inserted in the tree.
   * Lower numbers appear first. Use this to control ordering of root nodes.
   *
   * @returns The sort order index
   */
  abstract getTreeOrder(): number;

  /**
   * Refreshes the tree nodes managed by this contributor.
   * This is called when data changes occur.
   *
   * @param rootNode The root node that should be updated with fresh children
   * @returns Observable that completes when refresh is done
   */
  abstract refreshNodes(rootNode: TreeNode): Observable<void>;

  /**
   * Provides context menu items for a specific tree node type.
   *
   * @param treeNode The node for which to generate menu items
   * @param parentNodeData Parent node data (if applicable)
   * @param selectedNodeData Selected node entity data (if applicable)
   * @returns Array of menu items, or null if no menu for this node type
   */
  abstract getContextMenuItems(treeNode: TreeNode, parentNodeData: any, selectedNodeData: any): MenuItem[] | null;

  /**
   * Handles delete operations for nodes managed by this contributor.
   *
   * @param treeNode The tree node to delete
   * @param id The entity ID to delete
   * @returns Observable that completes when deletion is done, or null if not applicable
   */
  handleDelete?(treeNode: TreeNode, id: number): Observable<any> | null {
    return null;
  }

  /**
   * Determines if a drag-and-drop operation can be performed on this node.
   * Called during dragover event.
   *
   * @param targetNode The tree node being dragged over
   * @param dataTransfer The data transfer object from the drag event
   * @returns true if this contributor can handle the drop operation
   */
  canDrop?(targetNode: TreeNode, dragData: string): boolean {
    return false;
  }

  /**
   * Handles a drop operation on a tree node.
   * Called when an item is dropped on a node that this contributor manages.
   *
   * @param targetNode The tree node where the drop occurred
   * @param dataTransfer The data transfer object containing the dropped data
   * @param sourceLabel Optional label of the source node (for messaging)
   */
  handleDrop?(targetNode: TreeNode, dragData: string, sourceLabel?: string): void {
    // Default implementation does nothing
  }

  /**
   * Determines if this contributor should handle the given ProcessedActionData event.
   * Used for automatic tree refresh when data changes.
   *
   * @param processedActionData The data change event
   * @returns true if this contributor should refresh its nodes
   */
  abstract shouldRefreshOnDataChange(processedActionData: ProcessedActionData): boolean;

  /**
   * Returns whether this contributor is enabled/active.
   * Allows conditional features (like Algo) to be toggled on/off.
   *
   * @returns true if this contributor should be active
   */
  isEnabled(): boolean {
    return true;
  }
}

/**
 * Injection token for providing multiple MainTreeContributor instances.
 * Use with multi: true provider configuration.
 */
export const MAIN_TREE_CONTRIBUTOR = new InjectionToken<MainTreeContributor[]>('MainTreeContributor');
