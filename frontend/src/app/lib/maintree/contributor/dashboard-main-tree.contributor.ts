import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';
import { MenuItem, TreeNode } from '@openng/optimus-ui/api';
import { TranslateService } from '@ngx-translate/core';
import { MainTreeContributor } from './main-tree-contributor.interface';
import { TypeNodeData } from '../types/type.node.data';
import { LibTreeNodeType } from '../types/lib.tree.node.type';
import { ProcessedActionData } from '../../types/processed.action.data';
import { GlobalparameterService } from '../../services/globalparameter.service';
import { BaseSettings } from '../../base.settings';

/**
 * Contributes the dashboard as the first node of the navigation tree, on the level of the portfolios.
 *
 * <p>
 * The dashboard is reached from the tree rather than from the main menu, so that leaving it and returning to the node
 * one came from is one click like everywhere else. The tree remembers which node was navigated to last and skips a
 * navigation to that same node; a dashboard opened past the tree therefore left that memory pointing at a view that is
 * no longer on screen, and the node had to be selected twice to come back.
 * </p>
 *
 * <p>
 * The node carries no children and no context menu, and it is contributed only where the server offers the feature.
 * </p>
 */
@Injectable()
export class DashboardMainTreeContributor extends MainTreeContributor {
  constructor(
    private translateService: TranslateService,
    private gps: GlobalparameterService
  ) {
    super();
  }

  /** Before the portfolios, which are the first of the other contributors. */
  getTreeOrder(): number {
    return -1;
  }

  getRootNodes(): Observable<TreeNode[]> {
    const rootNode: TreeNode = {
      label: 'DASHBOARD',
      leaf: true,
      data: new TypeNodeData(
        LibTreeNodeType.NO_MENU,
        BaseSettings.MAINVIEW_KEY + '/' + BaseSettings.DASHBOARD_KEY,
        null,
        null
      )
    };
    this.translateService.get(rootNode.label).subscribe((translated) => (rootNode.label = translated));
    return of([rootNode]);
  }

  /** The card contents are loaded by the dashboard itself, so the node never changes. */
  refreshNodes(rootNode: TreeNode): Observable<void> {
    return of(void 0);
  }

  getContextMenuItems(treeNode: TreeNode, parentNodeData: any, selectedNodeData: any): MenuItem[] | null {
    return null;
  }

  shouldRefreshOnDataChange(processedActionData: ProcessedActionData): boolean {
    return false;
  }

  /**
   * The tree pairs every enabled contributor with one root node by position, which is why the node is withheld here
   * rather than by returning an empty list from {@link getRootNodes}.
   */
  override isEnabled(): boolean {
    return this.gps.useDashboard();
  }
}
