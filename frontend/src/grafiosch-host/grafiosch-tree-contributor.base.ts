import { TranslateService } from '@ngx-translate/core';
import { MenuItem, TreeNode } from '@openng/optimus-ui/api';
import { Observable, of } from 'rxjs';

import { BaseSettings } from '../app/lib/base.settings';
import { MainTreeContributor } from '../app/lib/maintree/contributor/main-tree-contributor.interface';
import { LibTreeNodeType } from '../app/lib/maintree/types/lib.tree.node.type';
import { TypeNodeData } from '../app/lib/maintree/types/type.node.data';
import { ProcessedActionData } from '../app/lib/types/processed.action.data';

/**
 * Shared behaviour of this host's tree contributors. Every node they publish is static navigation into a library view,
 * so the three change related members of {@link MainTreeContributor} collapse into constants here and only the root
 * nodes differ per subclass.
 *
 * <p>This class is deliberately local to the host and not part of {@code src/app/lib}: an application that keeps state
 * in its tree needs the full contract, and the library must not suggest that a contributor is normally static.</p>
 */
export abstract class GrafioschTreeContributorBase extends MainTreeContributor {
  protected constructor(protected translateService: TranslateService) {
    super();
  }

  /** Nothing in this host's tree depends on loaded data, so a refresh has nothing to do. */
  refreshNodes(rootNode: TreeNode): Observable<void> {
    return of(void 0);
  }

  /** Pure navigation nodes, no create/edit/delete actions attached to any of them. */
  getContextMenuItems(treeNode: TreeNode, parentNodeData: any, selectedNodeData: any): MenuItem[] | null {
    return null;
  }

  /** Consequently no data change can invalidate a node either. */
  shouldRefreshOnDataChange(processedActionData: ProcessedActionData): boolean {
    return false;
  }

  /**
   * Creates a leaf that only navigates to a route below {@code /mainview}.
   *
   * @param label - NLS key of the node, translated in place by {@link translateNodes}
   * @param route - route below {@code /mainview}, without the leading segment
   * @returns the tree node, typed {@code NO_MENU} because the host has no node types of its own
   */
  protected navigationNode(label: string, route: string): TreeNode {
    return {
      label,
      data: new TypeNodeData(LibTreeNodeType.NO_MENU, this.addMainRoute(route), null, null, null)
    };
  }

  /**
   * Replaces the NLS key in every label of the given nodes and their children by its translation.
   *
   * @param treeNodes - nodes whose labels are still NLS keys
   */
  protected translateNodes(treeNodes: TreeNode[]): void {
    treeNodes.forEach((treeNode) => {
      this.translateService.get(treeNode.label).subscribe((translated) => (treeNode.label = translated));
      if (treeNode.children) {
        this.translateNodes(treeNode.children);
      }
    });
  }

  /**
   * Prefixes a route with the split layout's path, under which every view of this host is mounted.
   *
   * @param suffix - route below {@code /mainview}
   * @returns the absolute route without a leading slash
   */
  protected addMainRoute(suffix: string): string {
    return BaseSettings.MAINVIEW_KEY + '/' + suffix;
  }
}
